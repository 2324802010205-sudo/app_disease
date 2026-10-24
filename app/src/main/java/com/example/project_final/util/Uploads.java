package com.example.project_final.util;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/** Chuẩn bị ảnh để tải lên: xoay theo EXIF, thu nhỏ tối đa 1600 px, nén JPEG. Chạy trên luồng nền. */
public final class Uploads {
    private static final int MAX_SIDE = 1600;
    private static final int QUALITY = 85;
    private static final MediaType JPEG = MediaType.parse("image/jpeg");
    private static final MediaType TEXT = MediaType.parse("text/plain; charset=utf-8");

    private Uploads() {}

    public static MultipartBody.Part imagePart(Context ctx, Uri uri, String fieldName) throws IOException {
        byte[] bytes = compress(ctx.getContentResolver(), uri);
        return MultipartBody.Part.createFormData(fieldName, "photo.jpg", RequestBody.create(bytes, JPEG));
    }

    /**
     * Nén ảnh được chọn thành tệp JPEG trong bộ nhớ đệm của app (tối đa 1600 px, đã xoay đúng chiều).
     * Dùng cho luồng quét: các màn hình sau chỉ cần đường dẫn tệp, không phụ thuộc quyền đọc URI gốc. Chạy trên luồng nền.
     */
    public static File prepareToCache(Context ctx, Uri uri) throws IOException {
        byte[] bytes = compress(ctx.getContentResolver(), uri);
        File dir = new File(ctx.getCacheDir(), "captures");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File out = new File(dir, "scan_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream f = new FileOutputStream(out)) {
            f.write(bytes);
        }
        return out;
    }

    public static MultipartBody.Part filePart(File file, String fieldName) {
        return MultipartBody.Part.createFormData(fieldName, "photo.jpg", RequestBody.create(file, JPEG));
    }

    /** Các trường văn bản của yêu cầu multipart; giá trị null hoặc rỗng bị bỏ qua. */
    public static Map<String, RequestBody> text(String... keyValues) {
        Map<String, RequestBody> m = new HashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            String v = keyValues[i + 1];
            if (v != null && !v.trim().isEmpty()) m.put(keyValues[i], RequestBody.create(v.trim(), TEXT));
        }
        return m;
    }

    static byte[] compress(ContentResolver cr, Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) throw new IOException("Không mở được ảnh");
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in = cr.openInputStream(uri)) {
            bmp = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bmp == null) throw new IOException("Tệp không phải ảnh hợp lệ");

        Matrix m = new Matrix();
        try (InputStream in = cr.openInputStream(uri)) {
            if (in != null) {
                int o = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (o == ExifInterface.ORIENTATION_ROTATE_90) m.postRotate(90);
                else if (o == ExifInterface.ORIENTATION_ROTATE_180) m.postRotate(180);
                else if (o == ExifInterface.ORIENTATION_ROTATE_270) m.postRotate(270);
            }
        } catch (IOException ignored) {
            // không đọc được EXIF thì dùng ảnh nguyên bản
        }
        float scale = (float) MAX_SIDE / Math.max(bmp.getWidth(), bmp.getHeight());
        if (scale < 1f) m.postScale(scale, scale);
        if (!m.isIdentity()) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, QUALITY, out);
        return out.toByteArray();
    }
}
