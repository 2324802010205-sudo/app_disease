package com.example.project_final.ui.scan;

import com.example.project_final.R;
import com.example.project_final.ui.common.BaseActivity;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.project_final.util.Uploads;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;

/** Chụp ảnh lá lúa bằng máy ảnh (CameraX) hoặc chọn từ thư viện, rồi chuyển sang màn xem lại ảnh. */
public class ScanActivity extends BaseActivity {
    public static final String EXTRA_FIELD_ID = "fieldId";

    private PreviewView preview;
    private ImageCapture capture;
    private boolean flashAuto = true;
    private boolean cameraStarted;
    private String fieldId;
    private ActivityResultLauncher<String> permission;
    private ActivityResultLauncher<PickVisualMediaRequest> gallery;

    @Override
    protected int layout() {
        return R.layout.activity_scan;
    }

    @Override
    protected void onReady() {
        fieldId = getIntent().getStringExtra(EXTRA_FIELD_ID);
        preview = findViewById(R.id.preview);

        permission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) startCamera();
            else showPermissionDenied();
        });
        gallery = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) prepare(uri);
        });

        findViewById(R.id.btn_gallery).setOnClickListener(v -> gallery.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()));
        findViewById(R.id.btn_shutter).setOnClickListener(v -> takePhoto());
        findViewById(R.id.btn_history).setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        findViewById(R.id.btn_flash).setOnClickListener(v -> toggleFlash());

        if (hasCameraPermission()) startCamera();
        else permission.launch(Manifest.permission.CAMERA);
    }

    /** Quay lại từ Cài đặt sau khi cấp quyền thì tự bật máy ảnh, không bắt người dùng thoát màn hình. */
    @Override
    protected void onResume() {
        super.onResume();
        if (permission != null && !cameraStarted && hasCameraPermission()) startCamera();
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    /** Bị từ chối: hướng dẫn mở Cài đặt (hệ thống không hỏi lại sau lần từ chối thứ hai). */
    private void showPermissionDenied() {
        showCameraMessage("Cần cho phép dùng máy ảnh để chụp lá lúa.\nChạm vào đây để mở Cài đặt, hoặc chọn ảnh từ thư viện.");
        findViewById(R.id.camera_message).setOnClickListener(v -> startActivity(
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null))));
    }

    private void showCameraMessage(String text) {
        TextView t = findViewById(R.id.camera_message);
        t.setText(text);
        t.setVisibility(View.VISIBLE);
    }

    private void startCamera() {
        findViewById(R.id.camera_message).setVisibility(View.GONE);
        findViewById(R.id.camera_message).setOnClickListener(null);
        findViewById(R.id.camera_message).setClickable(false);
        cameraStarted = true;
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview p = new Preview.Builder().build();
                p.setSurfaceProvider(preview.getSurfaceProvider());
                capture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setFlashMode(flashAuto ? ImageCapture.FLASH_MODE_AUTO : ImageCapture.FLASH_MODE_OFF)
                        .build();
                provider.unbindAll();
                // Ưu tiên camera sau; máy không có thì dùng camera trước
                CameraSelector selector = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                        ? CameraSelector.DEFAULT_BACK_CAMERA : CameraSelector.DEFAULT_FRONT_CAMERA;
                provider.bindToLifecycle(this, selector, p, capture);
            } catch (Exception e) {
                capture = null;
                cameraStarted = false;
                showCameraMessage("Không mở được máy ảnh trên thiết bị này.\nHãy chọn ảnh từ thư viện.");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void toggleFlash() {
        flashAuto = !flashAuto;
        if (capture != null) capture.setFlashMode(flashAuto ? ImageCapture.FLASH_MODE_AUTO : ImageCapture.FLASH_MODE_OFF);
        ImageView icon = (ImageView) ((android.view.ViewGroup) findViewById(R.id.btn_flash)).getChildAt(0);
        icon.setAlpha(flashAuto ? 1f : 0.35f);
        toast(flashAuto ? "Đèn flash: tự động" : "Đèn flash: tắt");
    }

    private void takePhoto() {
        if (capture == null) {
            toast("Máy ảnh chưa sẵn sàng");
            return;
        }
        File dir = new File(getCacheDir(), "captures");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File raw = new File(dir, "raw_" + System.currentTimeMillis() + ".jpg");
        busy(true);
        capture.takePicture(new ImageCapture.OutputFileOptions.Builder(raw).build(), ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(ImageCapture.OutputFileResults r) {
                        busy(false);
                        prepare(Uri.fromFile(raw));
                    }

                    @Override
                    public void onError(ImageCaptureException e) {
                        busy(false);
                        toast("Không chụp được ảnh, vui lòng thử lại");
                    }
                });
    }

    /** Nén và xoay ảnh trên luồng nền rồi mở màn xem lại. */
    private void prepare(Uri uri) {
        busy(true);
        new Thread(() -> {
            try {
                File file = Uploads.prepareToCache(this, uri);
                runOnUiThread(() -> {
                    busy(false);
                    Intent it = new Intent(this, ReviewPhotoActivity.class)
                            .putExtra(ReviewPhotoActivity.EXTRA_PATH, file.getAbsolutePath())
                            .putExtra(EXTRA_FIELD_ID, fieldId);
                    startActivity(it);
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    busy(false);
                    toast("Không đọc được ảnh: " + e.getMessage());
                });
            }
        }).start();
    }
}
