package com.edgedetection;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.SurfaceTexture;
import androidx.annotation.NonNull;

public class CameraManager {
    private static final String TAG = "CameraManager";
    private android.hardware.camera2.CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private Context context;
    private GLRenderer glRenderer;
    private boolean useRawFeed = false;

    public CameraManager(Context context, GLRenderer glRenderer) {
        this.context = context;
        this.glRenderer = glRenderer;
        this.cameraManager = (android.hardware.camera2.CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    }

    public void startCamera() {
        startBackgroundThread();
        try {
            String cameraId = cameraManager.getCameraIdList()[0];
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    try {
                        createCaptureSession();
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "Camera access exception", e);
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    camera.close();
                    cameraDevice = null;
                    Log.e(TAG, "Camera error: " + error);
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera access exception", e);
        }
    }

    private void createCaptureSession() throws CameraAccessException {
        if (cameraDevice == null) return;

        SurfaceTexture surfaceTexture = glRenderer.getSurfaceTexture();
        if (surfaceTexture == null) {
            Log.e(TAG, "SurfaceTexture not available");
            return;
        }

        android.view.Surface surface = new android.view.Surface(surfaceTexture);

        try {
            cameraDevice.createCaptureSession(java.util.Arrays.asList(surface),
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        captureSession = session;
                        try {
                            startRepeatingCapture();
                        } catch (CameraAccessException e) {
                            Log.e(TAG, "Error starting capture", e);
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        Log.e(TAG, "Capture session configuration failed");
                    }
                }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera access exception", e);
        }
    }

    private void startRepeatingCapture() throws CameraAccessException {
        if (captureSession == null || cameraDevice == null) return;

        CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        SurfaceTexture surfaceTexture = glRenderer.getSurfaceTexture();
        if (surfaceTexture != null) {
            builder.addTarget(new android.view.Surface(surfaceTexture));
            captureSession.setRepeatingRequest(builder.build(), null, backgroundHandler);
        }
    }

    public void setProcessingMode(boolean rawFeed) {
        this.useRawFeed = rawFeed;
        // In real implementation, this would toggle between processing modes
    }

    public void stopCamera() {
        try {
            if (captureSession != null) {
                captureSession.stopRepeating();
                captureSession.close();
            }
            if (cameraDevice != null) {
                cameraDevice.close();
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error stopping camera", e);
        }
        stopBackgroundThread();
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while stopping background thread", e);
            }
        }
    }
}
