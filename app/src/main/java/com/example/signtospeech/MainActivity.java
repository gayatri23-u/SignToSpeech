package com.example.signtospeech;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

public class MainActivity extends AppCompatActivity
        implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final int CAMERA_PERMISSION_REQUEST = 101;

    JavaCameraView cameraView;
    TextView detectedText;
    Button btnSpeak;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        detectedText = findViewById(R.id.detectedText);
        btnSpeak = findViewById(R.id.btnSpeak);

        cameraView = findViewById(R.id.cameraView);
        cameraView.setVisibility(SurfaceView.VISIBLE);
        cameraView.setCvCameraViewListener(this);
        cameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK);

        checkCameraPermission();
    }

    // ================= CAMERA PERMISSION =================
    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST
            );
        } else {
            cameraView.setCameraPermissionGranted();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQUEST &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            cameraView.setCameraPermissionGranted();
        } else {
            Toast.makeText(this,
                    "Camera permission is mandatory",
                    Toast.LENGTH_LONG).show();
        }
    }

    // ================= LIFECYCLE =================
    @Override
    protected void onResume() {
        super.onResume();
        if (OpenCVLoader.initLocal()) {
            cameraView.enableView();
        } else {
            Toast.makeText(this, "OpenCV load failed", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraView != null)
            cameraView.disableView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraView != null)
            cameraView.disableView();
    }

    // ================= CAMERA FRAMES =================
    @Override
    public void onCameraViewStarted(int width, int height) { }

    @Override
    public void onCameraViewStopped() { }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        return inputFrame.rgba(); // LIVE CAMERA FEED
    }
}
