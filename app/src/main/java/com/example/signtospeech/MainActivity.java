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

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;

import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity
        implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final int CAMERA_PERMISSION_REQUEST = 101;

    JavaCameraView cameraView;
    TextView detectedText;
    Button btnSpeak;

    // ===== STABILITY VARIABLES =====
    int stableFingerCount = 0;
    int frameCounter = 0;
    int fingerSum = 0;

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

    // ================= CAMERA CALLBACKS =================
    @Override
    public void onCameraViewStarted(int width, int height) { }

    @Override
    public void onCameraViewStopped() { }

    // ================= CORE LOGIC =================
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        Mat frame = inputFrame.rgba();
        Mat rgb = new Mat();
        Mat hsv = new Mat();
        Mat mask = new Mat();

        Imgproc.cvtColor(frame, rgb, Imgproc.COLOR_RGBA2RGB);
        Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV);

        Scalar lowerSkin = new Scalar(0, 30, 60);
        Scalar upperSkin = new Scalar(20, 150, 255);

        Core.inRange(hsv, lowerSkin, upperSkin, mask);
        Imgproc.GaussianBlur(mask, mask, new Size(5, 5), 0);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();

        Imgproc.findContours(mask, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        double maxArea = 0;
        MatOfPoint handContour = null;

        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area > maxArea) {
                maxArea = area;
                handContour = contour;
            }
        }

        if (handContour != null) {

            Rect boundingRect = Imgproc.boundingRect(handContour);
            Imgproc.rectangle(frame, boundingRect.tl(),
                    boundingRect.br(), new Scalar(0, 255, 0), 2);

            MatOfInt hull = new MatOfInt();
            Imgproc.convexHull(handContour, hull);

            Point[] contourPoints = handContour.toArray();
            int[] hullIndexes = hull.toArray();

            List<Point> hullPoints = new ArrayList<>();
            for (int index : hullIndexes) {
                hullPoints.add(contourPoints[index]);
            }

            MatOfPoint hullMat = new MatOfPoint();
            hullMat.fromList(hullPoints);

            List<MatOfPoint> hullList = new ArrayList<>();
            hullList.add(hullMat);

            Imgproc.drawContours(frame, hullList, -1,
                    new Scalar(255, 0, 0), 2);

            MatOfInt4 defects = new MatOfInt4();
            Imgproc.convexityDefects(handContour, hull, defects);

            int[] defectArray = defects.toArray();
            int fingerCount = 0;

            for (int i = 0; i < defectArray.length; i += 4) {

                int startIdx = defectArray[i];
                int endIdx = defectArray[i + 1];
                int farIdx = defectArray[i + 2];
                double depth = defectArray[i + 3] / 256.0;

                Point startPoint = contourPoints[startIdx];
                Point endPoint = contourPoints[endIdx];
                Point farPoint = contourPoints[farIdx];

                double ang = angle(startPoint, endPoint, farPoint);

                if (depth > 30 && ang < 90 &&
                        farPoint.y < boundingRect.y + boundingRect.height * 0.8) {
                    fingerCount++;
                }
            }

            int totalFingers;

             // Closed fist
            if (fingerCount == 0) {
                totalFingers = 0;
            }
             // One finger (special case)
            else if (fingerCount == 1) {
                totalFingers = 1;
            }
             // General case
            else {
                totalFingers = fingerCount;
            }
            // Safety cap (noise control)
            if (totalFingers > 5) {
                totalFingers = 5;
            }



            // == TEMPORAL SMOOTHING ==
            fingerSum += totalFingers;
            frameCounter++;

            if (frameCounter >= 8) {
                stableFingerCount = Math.round((float) fingerSum / frameCounter);
                frameCounter = 0;
                fingerSum = 0;
            }

            Imgproc.putText(frame,
                    "Fingers: " + stableFingerCount,
                    new Point(boundingRect.x, boundingRect.y - 10),
                    Imgproc.FONT_HERSHEY_SIMPLEX,
                    0.8,
                    new Scalar(255, 255, 255),
                    2);
        }

        return frame;
    }

    // ===== ANGLE FUNCTION =====
    private double angle(Point s, Point e, Point f) {
        double a = Math.hypot(e.x - f.x, e.y - f.y);
        double b = Math.hypot(s.x - f.x, s.y - f.y);
        double c = Math.hypot(e.x - s.x, e.y - s.y);

        return Math.acos((a * a + b * b - c * c) / (2 * a * b)) * 180 / Math.PI;
    }
}
