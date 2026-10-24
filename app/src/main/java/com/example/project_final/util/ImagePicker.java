package com.example.project_final.util;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.project_final.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Chọn ảnh từ máy ảnh hoặc thư viện. Phải tạo trong onCreate/onReady của màn hình (trước khi STARTED).
 * Nhiều ảnh: truyền max > 1 thì thư viện cho chọn tối đa max ảnh.
 */
public class ImagePicker {
    public interface Listener {
        void onPicked(List<Uri> uris);
    }

    private final ComponentActivity activity;
    private final int max;
    private final Listener listener;
    private final ActivityResultLauncher<PickVisualMediaRequest> galleryOne;
    private final ActivityResultLauncher<PickVisualMediaRequest> galleryMany;
    private final ActivityResultLauncher<Uri> camera;
    private final ActivityResultLauncher<String> cameraPermission;
    private Uri pendingCapture;

    public ImagePicker(ComponentActivity activity, int max, Listener listener) {
        this.activity = activity;
        this.max = Math.max(1, max);
        this.listener = listener;
        galleryOne = activity.registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) deliver(List.of(uri));
        });
        galleryMany = activity.registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(Math.max(2, this.max)), uris -> {
            if (uris != null && !uris.isEmpty()) deliver(uris.size() > this.max ? uris.subList(0, this.max) : uris);
        });
        camera = activity.registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
            if (Boolean.TRUE.equals(ok) && pendingCapture != null) deliver(List.of(pendingCapture));
        });
        cameraPermission = activity.registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) launchCamera();
            else android.widget.Toast.makeText(activity, "Cần cho phép dùng máy ảnh để chụp ảnh", android.widget.Toast.LENGTH_SHORT).show();
        });
    }

    private void deliver(List<Uri> uris) {
        listener.onPicked(new ArrayList<>(uris));
    }

    /** Hiện hộp chọn: chụp ảnh mới hoặc chọn từ thư viện. */
    public void show() {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View v = LayoutInflater.from(activity).inflate(R.layout.sheet_image_source, null);
        v.findViewById(R.id.sheet_camera).setOnClickListener(x -> {
            dialog.dismiss();
            takePhoto();
        });
        v.findViewById(R.id.sheet_gallery).setOnClickListener(x -> {
            dialog.dismiss();
            pickGallery();
        });
        dialog.setContentView(v);
        dialog.show();
    }

    public void pickGallery() {
        PickVisualMediaRequest req = new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build();
        if (max > 1) galleryMany.launch(req);
        else galleryOne.launch(req);
    }

    public void takePhoto() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera();
        else cameraPermission.launch(Manifest.permission.CAMERA);
    }

    private void launchCamera() {
        File dir = new File(activity.getCacheDir(), "captures");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File file = new File(dir, "capture_" + System.currentTimeMillis() + ".jpg");
        pendingCapture = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", file);
        camera.launch(pendingCapture);
    }
}
