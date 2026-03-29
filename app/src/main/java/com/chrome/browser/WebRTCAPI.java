package com.chrome.browser;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebRTC API - Full Camera and Microphone Support
 * JavaScript Interface for WebRTC-like functionality
 */
public class WebRTCAPI {
    private static final String TAG = "WebRTCAPI";
    
    private final Activity activity;
    private final WebView webView;
    private final Handler mainHandler;
    private final Context context;
    
    // Camera
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private String currentCameraId;
    private boolean isCameraOpen = false;
    private Surface cameraSurface;
    
    // Audio
    private AudioRecord audioRecord;
    private boolean isRecordingAudio = false;
    private Thread audioThread;
    
    // State
    private AtomicBoolean isInitialized = new AtomicBoolean(false);
    
    public WebRTCAPI(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.context = activity.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        try {
            cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
            isInitialized.set(true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize camera manager", e);
        }
    }
    
    // ==================== CAPABILITIES ====================
    
    @JavascriptInterface
    public boolean isAvailable() {
        return isInitialized.get();
    }
    
    @JavascriptInterface
    public boolean hasCamera() {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }
    
    @JavascriptInterface
    public boolean hasMicrophone() {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
    }
    
    @JavascriptInterface
    public boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }
    
    @JavascriptInterface
    public boolean hasMicrophonePermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }
    
    @JavascriptInterface
    public void requestCameraPermission() {
        ActivityCompat.requestPermissions(activity, 
            new String[]{Manifest.permission.CAMERA}, 1002);
    }
    
    @JavascriptInterface
    public void requestMicrophonePermission() {
        ActivityCompat.requestPermissions(activity,
            new String[]{Manifest.permission.RECORD_AUDIO}, 1003);
    }
    
    @JavascriptInterface
    public void requestAllPermissions() {
        ActivityCompat.requestPermissions(activity,
            new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 1005);
    }
    
    // ==================== DEVICES ====================
    
    @JavascriptInterface
    public String getVideoInputDevices() {
        try {
            JSONArray devices = new JSONArray();
            
            if (cameraManager != null) {
                String[] cameraIds = cameraManager.getCameraIdList();
                
                for (String id : cameraIds) {
                    CameraCharacteristics chars = cameraManager.getCameraCharacteristics(id);
                    
                    JSONObject device = new JSONObject();
                    device.put("deviceId", "camera-" + id);
                    device.put("kind", "videoinput");
                    
                    Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                    if (facing != null) {
                        if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                            device.put("label", "Front Camera");
                            device.put("facing", "user");
                        } else if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                            device.put("label", "Back Camera");
                            device.put("facing", "environment");
                        } else {
                            device.put("label", "Camera " + id);
                            device.put("facing", "unknown");
                        }
                    }
                    
                    device.put("groupId", "camera-group");
                    
                    // Get supported resolutions
                    Size[] sizes = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        .getOutputSizes(ImageFormat.JPEG);
                    if (sizes != null && sizes.length > 0) {
                        JSONArray resolutions = new JSONArray();
                        for (Size size : sizes) {
                            JSONObject res = new JSONObject();
                            res.put("width", size.getWidth());
                            res.put("height", size.getHeight());
                            resolutions.put(res);
                        }
                        device.put("resolutions", resolutions);
                    }
                    
                    devices.put(device);
                }
            }
            
            JSONObject result = new JSONObject();
            result.put("devices", devices);
            return result.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error getting video devices", e);
            return "{\"devices\":[]}";
        }
    }
    
    @JavascriptInterface
    public String getAudioInputDevices() {
        try {
            JSONArray devices = new JSONArray();
            
            if (hasMicrophone()) {
                JSONObject device = new JSONObject();
                device.put("deviceId", "microphone-default");
                device.put("kind", "audioinput");
                device.put("label", "Microphone");
                device.put("groupId", "audio-group");
                devices.put(device);
            }
            
            JSONObject result = new JSONObject();
            result.put("devices", devices);
            return result.toString();
        } catch (Exception e) {
            return "{\"devices\":[]}";
        }
    }
    
    @JavascriptInterface
    public String getAllMediaDevices() {
        try {
            JSONArray devices = new JSONArray();
            
            // Video input devices
            String videoDevices = getVideoInputDevices();
            JSONObject videoObj = new JSONObject(videoDevices);
            JSONArray videoArr = videoObj.getJSONArray("devices");
            for (int i = 0; i < videoArr.length(); i++) {
                devices.put(videoArr.getJSONObject(i));
            }
            
            // Audio input devices
            String audioDevices = getAudioInputDevices();
            JSONObject audioObj = new JSONObject(audioDevices);
            JSONArray audioArr = audioObj.getJSONArray("devices");
            for (int i = 0; i < audioArr.length(); i++) {
                devices.put(audioArr.getJSONObject(i));
            }
            
            // Screen capture
            JSONObject screenDevice = new JSONObject();
            screenDevice.put("deviceId", "screen-capture");
            screenDevice.put("kind", "videoinput");
            screenDevice.put("label", "Screen");
            screenDevice.put("groupId", "screen-group");
            devices.put(screenDevice);
            
            JSONObject result = new JSONObject();
            result.put("devices", devices);
            return result.toString();
        } catch (Exception e) {
            return "{\"devices\":[]}";
        }
    }
    
    // ==================== CAMERA CONTROL ====================
    
    @SuppressLint("MissingPermission")
    @JavascriptInterface
    public void openCamera(String cameraId, int width, int height, String callback) {
        if (!hasCameraPermission()) {
            executeCallback(callback, "{\"success\":false,\"error\":\"Camera permission not granted\"}");
            return;
        }
        
        if (isCameraOpen) {
            closeCamera(null);
        }
        
        mainHandler.post(() -> {
            try {
                // Extract actual camera ID
                String tempId = cameraId.replace("camera-", "");
                if (tempId.isEmpty()) {
                    // Default to front camera
                    String[] ids = cameraManager.getCameraIdList();
                    for (String id : ids) {
                        CameraCharacteristics chars = cameraManager.getCameraCharacteristics(id);
                        Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                            tempId = id;
                            break;
                        }
                    }
                    if (tempId.isEmpty() && ids.length > 0) {
                        tempId = ids[0];
                    }
                }
                
                final String actualId = tempId;
                currentCameraId = actualId;
                
                // Create image reader
                final int w = width > 0 ? width : 1280;
                final int h = height > 0 ? height : 720;
                
                imageReader = ImageReader.newInstance(w, h, ImageFormat.JPEG, 2);
                
                // Open camera
                cameraManager.openCamera(actualId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        cameraDevice = camera;
                        isCameraOpen = true;
                        
                        try {
                            // Create capture session
                            List<Surface> surfaces = new ArrayList<>();
                            if (cameraSurface != null) {
                                surfaces.add(cameraSurface);
                            }
                            surfaces.add(imageReader.getSurface());
                            
                            camera.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                                @Override
                                public void onConfigured(@NonNull CameraCaptureSession session) {
                                    captureSession = session;
                                    
                                    try {
                                        CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                        if (cameraSurface != null) {
                                            builder.addTarget(cameraSurface);
                                        }
                                        builder.addTarget(imageReader.getSurface());
                                        
                                        session.setRepeatingRequest(builder.build(), null, mainHandler);
                                        
                                        JSONObject result = new JSONObject();
                                        result.put("success", true);
                                        result.put("cameraId", actualId);
                                        result.put("width", w);
                                        result.put("height", h);
                                        executeCallback(callback, result.toString());
                                    } catch (Exception e) {
                                        executeCallback(callback, "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
                                    }
                                }
                                
                                @Override
                                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                                    executeCallback(callback, "{\"success\":false,\"error\":\"Session configuration failed\"}");
                                }
                            }, mainHandler);
                        } catch (Exception e) {
                            executeCallback(callback, "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
                        }
                    }
                    
                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        camera.close();
                        cameraDevice = null;
                        isCameraOpen = false;
                    }
                    
                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        camera.close();
                        cameraDevice = null;
                        isCameraOpen = false;
                        executeCallback(callback, "{\"success\":false,\"error\":\"Camera error: " + error + "\"}");
                    }
                }, mainHandler);
                
            } catch (Exception e) {
                Log.e(TAG, "Error opening camera", e);
                executeCallback(callback, "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
            }
        });
    }
    
    @JavascriptInterface
    public void closeCamera(String callback) {
        mainHandler.post(() -> {
            try {
                isCameraOpen = false;
                
                if (captureSession != null) {
                    captureSession.close();
                    captureSession = null;
                }
                
                if (cameraDevice != null) {
                    cameraDevice.close();
                    cameraDevice = null;
                }
                
                if (imageReader != null) {
                    imageReader.close();
                    imageReader = null;
                }
                
                if (callback != null && !callback.isEmpty()) {
                    executeCallback(callback, "{\"success\":true}");
                }
            } catch (Exception e) {
                if (callback != null && !callback.isEmpty()) {
                    executeCallback(callback, "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
                }
            }
        });
    }
    
    @JavascriptInterface
    public boolean isCameraOpen() {
        return isCameraOpen;
    }
    
    @JavascriptInterface
    public void switchCamera(String callback) {
        if (!isCameraOpen) {
            executeCallback(callback, "{\"success\":false,\"error\":\"Camera not open\"}");
            return;
        }
        
        try {
            // Find opposite camera
            String newCameraId = null;
            String[] ids = cameraManager.getCameraIdList();
            
            for (String id : ids) {
                CameraCharacteristics chars = cameraManager.getCameraCharacteristics(id);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                
                if (currentCameraId.equals(id)) continue;
                
                if (facing != null) {
                    CameraCharacteristics currentChars = cameraManager.getCameraCharacteristics(currentCameraId);
                    Integer currentFacing = currentChars.get(CameraCharacteristics.LENS_FACING);
                    
                    if (!facing.equals(currentFacing)) {
                        newCameraId = id;
                        break;
                    }
                }
            }
            
            if (newCameraId == null && ids.length > 0) {
                for (String id : ids) {
                    if (!id.equals(currentCameraId)) {
                        newCameraId = id;
                        break;
                    }
                }
            }
            
            if (newCameraId != null) {
                closeCamera(null);
                Thread.sleep(200);
                openCamera(newCameraId, 0, 0, callback);
            } else {
                executeCallback(callback, "{\"success\":false,\"error\":\"No other camera available\"}");
            }
        } catch (Exception e) {
            executeCallback(callback, "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
        }
    }
    
    // ==================== AUDIO CONTROL ====================
    
    @SuppressLint("MissingPermission")
    @JavascriptInterface
    public void startAudioCapture(String callback) {
        if (!hasMicrophonePermission()) {
            executeCallback(callback, "{\"success\":false,\"error\":\"Microphone permission not granted\"}");
            return;
        }
        
        if (isRecordingAudio) {
            executeCallback(callback, "{\"success\":false,\"error\":\"Already recording\"}");
            return;
        }
        
        try {
            int sampleRate = 44100;
            int channelConfig = AudioFormat.CHANNEL_IN_MONO;
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
            
            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 2
            );
            
            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                audioRecord.startRecording();
                isRecordingAudio = true;
                
                executeCallback(callback, "{\"success\":true,\"sampleRate\":" + sampleRate + ",\"channels\":1}");
            } else {
                executeCallback(callback, "{\"success\":false,\"error\":\"AudioRecord not initialized\"}");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting audio capture", e);
            executeCallback(callback, "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
        }
    }
    
    @JavascriptInterface
    public void stopAudioCapture(String callback) {
        try {
            isRecordingAudio = false;
            
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            }
            
            if (callback != null && !callback.isEmpty()) {
                executeCallback(callback, "{\"success\":true}");
            }
        } catch (Exception e) {
            if (callback != null && !callback.isEmpty()) {
                executeCallback(callback, "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }
    
    @JavascriptInterface
    public boolean isAudioCapturing() {
        return isRecordingAudio;
    }
    
    // ==================== SETTINGS ====================
    
    @JavascriptInterface
    public String getSupportedResolutions(String cameraId) {
        try {
            String actualId = cameraId.replace("camera-", "");
            CameraCharacteristics chars = cameraManager.getCameraCharacteristics(actualId);
            
            Size[] sizes = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                .getOutputSizes(ImageFormat.JPEG);
            
            JSONArray resolutions = new JSONArray();
            for (Size size : sizes) {
                JSONObject res = new JSONObject();
                res.put("width", size.getWidth());
                res.put("height", size.getHeight());
                resolutions.put(res);
            }
            
            JSONObject result = new JSONObject();
            result.put("resolutions", resolutions);
            return result.toString();
        } catch (Exception e) {
            return "{\"resolutions\":[]}";
        }
    }
    
    // ==================== WEBRTC SIGNALING HELPERS ====================
    
    @JavascriptInterface
    public String getWebRTCCapabilities() {
        try {
            JSONObject caps = new JSONObject();
            caps.put("supportsCamera", hasCamera());
            caps.put("supportsMicrophone", hasMicrophone());
            caps.put("supportsScreenShare", Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
            caps.put("supportsVideoCodec", new JSONArray(Arrays.asList("H264", "VP8", "VP9")));
            caps.put("supportsAudioCodec", new JSONArray(Arrays.asList("OPUS", "AAC")));
            caps.put("maxVideoWidth", 1920);
            caps.put("maxVideoHeight", 1080);
            caps.put("maxVideoFps", 30);
            caps.put("supportsSimulcast", false);
            return caps.toString();
        } catch (Exception e) {
            return "{}";
        }
    }
    
    @JavascriptInterface
    public String createOfferSDP() {
        // This would be called from JavaScript to create WebRTC offer
        // The actual WebRTC is handled by WebView's WebRTC implementation
        try {
            JSONObject sdp = new JSONObject();
            sdp.put("type", "offer");
            sdp.put("sdp", "");
            return sdp.toString();
        } catch (Exception e) {
            return "{\"type\":\"offer\",\"sdp\":\"\"}";
        }
    }
    
    @JavascriptInterface
    public String createAnswerSDP() {
        try {
            JSONObject sdp = new JSONObject();
            sdp.put("type", "answer");
            sdp.put("sdp", "");
            return sdp.toString();
        } catch (Exception e) {
            return "{\"type\":\"answer\",\"sdp\":\"\"}";
        }
    }
    
    // ==================== HELPERS ====================
    
    private void executeCallback(String callback, String result) {
        if (callback == null || callback.isEmpty()) return;
        
        mainHandler.post(() -> {
            try {
                String js = "if(typeof " + callback + " === 'function') {" + 
                    callback + "(" + escapeJsString(result) + ");}";
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    webView.evaluateJavascript(js, null);
                } else {
                    webView.loadUrl("javascript:" + js);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error executing callback", e);
            }
        });
    }
    
    private String escapeJsString(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
