package com.riceguard.api.diagnosis;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chạy best.pt (YOLOv9c, đã xuất ONNX với kích thước ảnh động) bằng ONNX Runtime, không cần Python.
 * Tái hiện đúng đường đi của ultralytics 8.x khi gọi {@code model.predict(imgsz=..., conf=..., augment=...)}:
 * letterbox hình chữ nhật nhỏ nhất (rect=True: cạnh dài = imgsz, phần đệm 114 chỉ đủ để chia hết cho stride 32)
 * -> (tùy chọn) TTA ba tỉ lệ 1 / 0.83 / 0.67 có lật ngang ở tỉ lệ giữa -> NMS theo lớp
 * (IoU 0.7, tối đa 300 hộp) -> đưa hộp về tọa độ ảnh gốc.
 */
final class YoloOnnxDetector implements AutoCloseable {
    private static final int STRIDE = 32;
    private static final float LETTERBOX_PAD = 114f / 255f;
    private static final float TTA_PAD = 0.447f;
    private static final double[] TTA_SCALES = {1.0, 0.83, 0.67};
    private static final boolean[] TTA_FLIP_LR = {false, true, false};
    private static final float NMS_IOU = 0.7f;
    private static final int MAX_DET = 300;
    private static final int MAX_NMS_CANDIDATES = 30000;

    private final OrtEnvironment env = OrtEnvironment.getEnvironment();
    private final OrtSession session;
    private final String inputName;
    private final List<String> names;

    YoloOnnxDetector(Path model) throws OrtException {
        try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
            this.session = env.createSession(model.toString(), opts);
        }
        this.inputName = session.getInputNames().iterator().next();
        this.names = parseNames(session.getMetadata().getCustomMetadata().get("names"));
    }

    List<String> names() {
        return names;
    }

    /** Ultralytics ghi tên lớp vào metadata ONNX dạng "{0: 'Brown spot', 1: 'Leaf Blight', ...}". */
    static List<String> parseNames(String raw) {
        Map<Integer, String> byId = new HashMap<>();
        if (raw != null) {
            Matcher m = Pattern.compile("(\\d+)\\s*:\\s*(?:'([^']*)'|\"([^\"]*)\")").matcher(raw);
            while (m.find()) byId.put(Integer.parseInt(m.group(1)), m.group(2) != null ? m.group(2) : m.group(3));
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < byId.size(); i++) out.add(byId.getOrDefault(i, "class " + i));
        return out;
    }

    /** Một ảnh CHW (3 kênh, giá trị 0..1) phẳng trong một mảng. */
    private record Tensor(float[] data, int h, int w) {}

    /** Phát hiện sau NMS, tọa độ ảnh gốc. */
    private record Box(int cls, float conf, float x1, float y1, float x2, float y2) {}

    /** Hộp phát hiện ở tọa độ ảnh gốc (pixel). */
    record Located(String name, double confidence, double x1, double y1, double x2, double y2) {
        double area(int imageW, int imageH) {
            return Math.max(0.0, (x2 - x1) * (y2 - y1) / ((double) imageW * imageH));
        }
    }

    List<YoloPostprocess.Detection> detect(BufferedImage image, int imgsz, boolean augment, float runConf)
            throws OrtException {
        int w = image.getWidth(), h = image.getHeight();
        List<YoloPostprocess.Detection> out = new ArrayList<>();
        for (Located b : locate(image, imgsz, augment, runConf)) out.add(new YoloPostprocess.Detection(b.name, b.confidence, b.area(w, h)));
        return out;
    }

    /** Một lần suy luận ở kích thước imgsz, trả các hộp sau NMS sắp theo độ tin cậy giảm dần. */
    List<Located> locate(BufferedImage image, int imgsz, boolean augment, float runConf) throws OrtException {
        int w = image.getWidth(), h = image.getHeight();
        int[] argb = image.getRGB(0, 0, w, h, null, 0, w);

        // --- letterbox (ultralytics LetterBox, auto=True): giữ tỉ lệ, đệm hai phía bằng màu 114 vừa đủ chia hết cho stride.
        // Dùng rect=True là mặc định của predictor ultralytics; letterbox vuông cho kết quả khác hẳn (độ tin cậy lệch rất xa).
        double r = Math.min((double) imgsz / h, (double) imgsz / w);
        int newW = Math.max((int) Math.rint(w * r), 1), newH = Math.max((int) Math.rint(h * r), 1);
        double dw = ((imgsz - newW) % STRIDE) / 2.0, dh = ((imgsz - newH) % STRIDE) / 2.0;
        int left = (int) Math.rint(dw - 0.1), right = (int) Math.rint(dw + 0.1);
        int top = (int) Math.rint(dh - 0.1), bottom = (int) Math.rint(dh + 0.1);
        Tensor input = letterbox(argb, w, h, newW, newH, left, top, newW + left + right, newH + top + bottom);

        float[][] pred = augment ? predictAugmented(input) : infer(input);
        List<Box> boxes = nms(pred, runConf);

        // --- đưa về ảnh gốc
        List<Located> out = new ArrayList<>();
        for (Box b : boxes) {
            double x1 = clamp((b.x1 - left) / r, 0, w), x2 = clamp((b.x2 - left) / r, 0, w);
            double y1 = clamp((b.y1 - top) / r, 0, h), y2 = clamp((b.y2 - top) / r, 0, h);
            String name = b.cls < names.size() ? names.get(b.cls) : "class " + b.cls;
            out.add(new Located(name, b.conf, x1, y1, x2, y2));
        }
        return out;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ------------------------------------------------------------------ tiền xử lý

    private static Tensor letterbox(int[] argb, int w, int h, int newW, int newH, int left, int top, int outW, int outH) {
        int plane = outW * outH;
        float[] data = new float[3 * plane];
        Arrays.fill(data, LETTERBOX_PAD);
        double sx = (double) w / newW, sy = (double) h / newH;
        // cv2.resize INTER_LINEAR: tâm điểm ảnh lệch nửa ô, không lọc khi thu nhỏ
        int[] x0s = new int[newW], x1s = new int[newW];
        float[] fxs = new float[newW];
        for (int x = 0; x < newW; x++) {
            double fx = (x + 0.5) * sx - 0.5;
            if (fx < 0) fx = 0;
            int x0 = (int) fx;
            if (x0 >= w - 1) {
                x0 = w - 1;
                fx = x0;
            }
            x0s[x] = x0;
            x1s[x] = Math.min(x0 + 1, w - 1);
            fxs[x] = (float) (fx - x0);
        }
        for (int y = 0; y < newH; y++) {
            double fy = (y + 0.5) * sy - 0.5;
            if (fy < 0) fy = 0;
            int y0 = (int) fy;
            if (y0 >= h - 1) {
                y0 = h - 1;
                fy = y0;
            }
            int y1 = Math.min(y0 + 1, h - 1);
            float wy = (float) (fy - y0);
            int row0 = y0 * w, row1 = y1 * w;
            int dst = (top + y) * outW + left;
            for (int x = 0; x < newW; x++) {
                int a = argb[row0 + x0s[x]], b = argb[row0 + x1s[x]], c = argb[row1 + x0s[x]], d = argb[row1 + x1s[x]];
                float wx = fxs[x];
                for (int ch = 0; ch < 3; ch++) {
                    int shift = 16 - 8 * ch;       // R, G, B
                    float top0 = ((a >> shift) & 0xFF) * (1 - wx) + ((b >> shift) & 0xFF) * wx;
                    float bot0 = ((c >> shift) & 0xFF) * (1 - wx) + ((d >> shift) & 0xFF) * wx;
                    data[ch * plane + dst + x] = (top0 * (1 - wy) + bot0 * wy) / 255f;
                }
            }
        }
        return new Tensor(data, outH, outW);
    }

    // ------------------------------------------------------------------ suy luận

    /** Chạy mô hình, trả về [4 + số lớp][số điểm lưới]: hàng 0..3 = cx, cy, w, h (pixel đầu vào), còn lại = điểm lớp. */
    private float[][] infer(Tensor t) throws OrtException {
        long[] shape = {1, 3, t.h, t.w};
        try (OnnxTensor in = OnnxTensor.createTensor(env, FloatBuffer.wrap(t.data), shape);
             OrtSession.Result res = session.run(Map.of(inputName, in))) {
            float[][][] out = (float[][][]) res.get(0).getValue();
            return out[0];
        }
    }

    /** TTA của ultralytics (DetectionModel._predict_augment): suy luận ở nhiều tỉ lệ rồi nối các dự đoán lại. */
    private float[][] predictAugmented(Tensor x) throws OrtException {
        List<float[][]> ys = new ArrayList<>();
        for (int i = 0; i < TTA_SCALES.length; i++) {
            double scale = TTA_SCALES[i];
            Tensor xi = scaleImg(TTA_FLIP_LR[i] ? flipLeftRight(x) : x, scale);
            float[][] yi = infer(xi);
            // de-scale: chia tọa độ cho tỉ lệ, rồi lật lại trục x nếu ảnh đã lật
            for (int k = 0; k < 4; k++) for (int j = 0; j < yi[k].length; j++) yi[k][j] /= (float) scale;
            if (TTA_FLIP_LR[i]) for (int j = 0; j < yi[0].length; j++) yi[0][j] = x.w - yi[0][j];
            ys.add(yi);
        }
        // _clip_augmented: bỏ phần đuôi của lần chạy đầu (lưới lớn nhất) và đầu của lần cuối (lưới nhỏ nhất)
        int nl = 3;
        int g = 1 + 4 + 16;                                                   // sum(4**x for x in range(nl))
        float[][] first = ys.get(0), last = ys.get(ys.size() - 1);
        int cutTail = (first[0].length / g) * 1;                              // sum(4**x for x in range(1))
        int cutHead = (last[0].length / g) * (int) Math.pow(4, nl - 1);       // sum(4**(nl-1-x) for x in range(1))
        ys.set(0, slice(first, 0, first[0].length - cutTail));
        ys.set(ys.size() - 1, slice(last, cutHead, last[0].length));
        int rows = ys.get(0).length, total = 0;
        for (float[][] y : ys) total += y[0].length;
        float[][] all = new float[rows][total];
        int off = 0;
        for (float[][] y : ys) {
            for (int k = 0; k < rows; k++) System.arraycopy(y[k], 0, all[k], off, y[k].length);
            off += y[0].length;
        }
        return all;
    }

    private static float[][] slice(float[][] a, int from, int to) {
        float[][] out = new float[a.length][];
        for (int k = 0; k < a.length; k++) out[k] = Arrays.copyOfRange(a[k], from, to);
        return out;
    }

    private static Tensor flipLeftRight(Tensor t) {
        float[] out = new float[t.data.length];
        for (int row = 0; row < 3 * t.h; row++) {
            int base = row * t.w;
            for (int x = 0; x < t.w; x++) out[base + x] = t.data[base + t.w - 1 - x];
        }
        return new Tensor(out, t.h, t.w);
    }

    /** ultralytics.utils.torch_utils.scale_img: thu nhỏ bilinear rồi đệm phải/dưới lên bội số của stride bằng 0.447. */
    private static Tensor scaleImg(Tensor t, double ratio) {
        if (ratio == 1.0) return t;
        int nh = (int) (t.h * ratio), nw = (int) (t.w * ratio);
        int ph = (int) (Math.ceil(t.h * ratio / STRIDE) * STRIDE), pw = (int) (Math.ceil(t.w * ratio / STRIDE) * STRIDE);
        float[] out = new float[3 * ph * pw];
        Arrays.fill(out, TTA_PAD);
        double sy = (double) t.h / nh, sx = (double) t.w / nw;
        for (int ch = 0; ch < 3; ch++) {
            int srcBase = ch * t.h * t.w, dstBase = ch * ph * pw;
            for (int y = 0; y < nh; y++) {
                double fy = Math.max(0, (y + 0.5) * sy - 0.5);
                int y0 = Math.min((int) fy, t.h - 1), y1 = Math.min(y0 + 1, t.h - 1);
                float wy = (float) (fy - y0);
                for (int x = 0; x < nw; x++) {
                    double fx = Math.max(0, (x + 0.5) * sx - 0.5);
                    int x0 = Math.min((int) fx, t.w - 1), x1 = Math.min(x0 + 1, t.w - 1);
                    float wx = (float) (fx - x0);
                    float a = t.data[srcBase + y0 * t.w + x0], b = t.data[srcBase + y0 * t.w + x1];
                    float c = t.data[srcBase + y1 * t.w + x0], d = t.data[srcBase + y1 * t.w + x1];
                    out[dstBase + y * pw + x] = (a * (1 - wx) + b * wx) * (1 - wy) + (c * (1 - wx) + d * wx) * wy;
                }
            }
        }
        return new Tensor(out, ph, pw);
    }

    // ------------------------------------------------------------------ NMS

    /** ultralytics ops.non_max_suppression (một nhãn mỗi hộp, không "agnostic"): lọc theo ngưỡng rồi NMS riêng từng lớp. */
    private static List<Box> nms(float[][] pred, float conf) {
        int nc = pred.length - 4, n = pred[0].length;
        List<Box> cand = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int best = 0;
            float score = pred[4][i];
            for (int c = 1; c < nc; c++) {
                if (pred[4 + c][i] > score) {
                    score = pred[4 + c][i];
                    best = c;
                }
            }
            if (score <= conf) continue;
            float cx = pred[0][i], cy = pred[1][i], bw = pred[2][i], bh = pred[3][i];
            cand.add(new Box(best, score, cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2));
        }
        cand.sort((a, b) -> Float.compare(b.conf, a.conf));
        if (cand.size() > MAX_NMS_CANDIDATES) cand = new ArrayList<>(cand.subList(0, MAX_NMS_CANDIDATES));
        List<Box> kept = new ArrayList<>();
        for (Box b : cand) {
            boolean suppressed = false;
            for (Box k : kept) {
                if (k.cls == b.cls && iou(k, b) > NMS_IOU) {
                    suppressed = true;
                    break;
                }
            }
            if (!suppressed) {
                kept.add(b);
                if (kept.size() >= MAX_DET) break;
            }
        }
        return kept;
    }

    private static float iou(Box a, Box b) {
        float iw = Math.min(a.x2, b.x2) - Math.max(a.x1, b.x1), ih = Math.min(a.y2, b.y2) - Math.max(a.y1, b.y1);
        if (iw <= 0 || ih <= 0) return 0f;
        float inter = iw * ih;
        float union = (a.x2 - a.x1) * (a.y2 - a.y1) + (b.x2 - b.x1) * (b.y2 - b.y1) - inter;
        return union <= 0 ? 0f : inter / union;
    }

    @Override
    public void close() throws OrtException {
        session.close();
    }
}
