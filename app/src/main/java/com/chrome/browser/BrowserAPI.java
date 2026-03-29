package com.chrome.browser;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * BrowserAPI v3.0.0 - JavaScript Interface for web-to-native communication
 * 
 * Supports screen sharing for video calls (WebRTC style)
 * 
 * Usage from JavaScript:
 * // Request screen share stream for video calls
 * ChromeBrowserAPI.requestScreenShareStream("callback");
 * // Returns: { success: true, streamId: "screen-capture-id" }
 * 
 * // Then use navigator.mediaDevices.getUserMedia with the streamId
 */
public class BrowserAPI {
    private static final String TAG = "BrowserAPI";
    private static final int REQUEST_SCREEN_CAPTURE = 1001;
    private static final int REQUEST_CAMERA = 1002;
    private static final int REQUEST_MICROPHONE = 1003;
    private static final int REQUEST_STORAGE = 1004;
    
    private final Activity activity;
    private final WebView webView;
    private final Handler mainHandler;
    private final Context context;
    
    // Screen capture
    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private String currentRecordingPath;
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;
    
    // Screen share for video calls (WebRTC style)
    private AtomicBoolean isScreenSharingActive = new AtomicBoolean(false);
    private String currentStreamId = null;
    private Surface screenShareSurface = null;
    private int screenShareResultCode = 0;
    private Intent screenShareData = null;
    
    // Callbacks stored for async operations
    private String screenCaptureCallback;
    private String screenShareCallback;
    private String cameraCallback;
    private String microphoneCallback;
    
    // API version
    private static final String API_VERSION = "3.0.0";
    
    public BrowserAPI(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.context = activity.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // Get screen metrics
        DisplayMetrics metrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
        
        // Initialize projection manager
        projectionManager = (MediaProjectionManager) activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }
    
    // ==================== API INFO ====================
    
    @JavascriptInterface
    public String getVersion() {
        return API_VERSION;
    }
    
    @JavascriptInterface
    public boolean isAvailable() {
        return true;
    }
    
    @JavascriptInterface
    public String getDeviceInfo() {
        try {
            JSONObject info = new JSONObject();
            info.put("platform", "Android");
            info.put("osVersion", Build.VERSION.RELEASE);
            info.put("sdkVersion", Build.VERSION.SDK_INT);
            info.put("manufacturer", Build.MANUFACTURER);
            info.put("model", Build.MODEL);
            info.put("brand", Build.BRAND);
            info.put("device", Build.DEVICE);
            info.put("product", Build.PRODUCT);
            info.put("screenWidth", screenWidth);
            info.put("screenHeight", screenHeight);
            info.put("density", screenDensity);
            info.put("packageName", context.getPackageName());
            info.put("appVersion", "1.4.0");
            info.put("apiVersion", API_VERSION);
            info.put("screenShareAvailable", Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
            info.put("webRTCSupported", true);
            return info.toString();
        } catch (Exception e) {
            return "{}";
        }
    }
    
    // ==================== SCREEN SHARE FOR VIDEO CALLS ====================
    
    /**
     * Check if screen sharing is available
     */
    @JavascriptInterface
    public boolean isScreenShareAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }
    
    /**
     * Check if currently screen sharing
     */
    @JavascriptInterface
    public boolean isScreenSharing() {
        return isScreenSharingActive.get();
    }
    
    /**
     * Request screen share stream for video calls
     * Returns a streamId that can be used with getUserMedia
     */
    @JavascriptInterface
    public void requestScreenShareStream(String callback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            executeCallback(callback, "{\"success\":false,\"error\":\"Screen share requires Android 5.0+\"}");
            return;
        }
        
        screenShareCallback = callback;
        
        mainHandler.post(() -> {
            try {
                activity.startActivityForResult(
                    projectionManager.createScreenCaptureIntent(),
                    REQUEST_SCREEN_CAPTURE
                );
            } catch (Exception e) {
                executeCallback(callback, "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
            }
        });
    }
    
    /**
     * Handle screen share permission result
     */
    public boolean handleScreenShareResult(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            if (screenShareCallback != null) {
                executeCallback(screenShareCallback, "{\"success\":false,\"error\":\"Permission denied\"}");
            }
            return false;
        }
        
        try {
            // Store the result for later use
            screenShareResultCode = resultCode;
            screenShareData = data;
            
            // Create a unique stream ID
            currentStreamId = "screen-capture-" + System.currentTimeMillis();
            
            // Create the media projection
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
            
            // Register callback for when projection stops
            mediaProjection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    isScreenSharingActive.set(false);
                    currentStreamId = null;
                    notifyScreenShareEnded();
                }
            }, mainHandler);
            
            isScreenSharingActive.set(true);
            
            // Return success with stream ID
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("streamId", currentStreamId);
            result.put("width", screenWidth);
            result.put("height", screenHeight);
            result.put("frameRate", 30);
            result.put("deviceId", "screen-capture-device");
            
            if (screenShareCallback != null) {
                executeCallback(screenShareCallback, result.toString());
            }
            
            showToast("Screen sharing started");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error starting screen share", e);
            if (screenShareCallback != null) {
                executeCallback(screenShareCallback, "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
            }
            return false;
        }
    }
    
    /**
     * Create virtual display for screen capture
     * This is called internally when a video track is needed
     */
    @JavascriptInterface
    public String createVirtualDisplay(int width, int height, int density) {
        if (mediaProjection == null) {
            return "{\"success\":false,\"error\":\"No active media projection\"}";
        }
        
        try {
            // Use provided dimensions or defaults
            int w = width > 0 ? width : screenWidth;
            int h = height > 0 ? height : screenHeight;
            int d = density > 0 ? density : screenDensity;
            
            // Create virtual display
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenShare",
                w, h, d,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                null, // Surface will be set by WebRTC
                null, null
            );
            
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("width", w);
            result.put("height", h);
            return result.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error creating virtual display", e);
            return "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
        }
    }
    
    /**
     * Set the surface for screen capture (for WebRTC)
     */
    @JavascriptInterface
    public boolean setScreenShareSurface(String surfaceData) {
        // Note: In real WebRTC implementation, the surface would be passed
        // through native code. Here we just track that surface is ready.
        return isScreenSharingActive.get();
    }
    
    /**
     * Stop screen sharing
     */
    @JavascriptInterface
    public void stopScreenShare(String callback) {
        if (!isScreenSharingActive.get()) {
            executeCallback(callback, "{\"success\":false,\"error\":\"Not screen sharing\"}");
            return;
        }
        
        try {
            isScreenSharingActive.set(false);
            currentStreamId = null;
            
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }
            
            if (mediaProjection != null) {
                mediaProjection.stop();
                mediaProjection = null;
            }
            
            screenShareResultCode = 0;
            screenShareData = null;
            
            showToast("Screen sharing stopped");
            executeCallback(callback, "{\"success\":true}");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping screen share", e);
            executeCallback(callback, "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * Get current screen share stream info
     */
    @JavascriptInterface
    public String getScreenShareStreamInfo() {
        try {
            if (!isScreenSharingActive.get() || currentStreamId == null) {
                return "{\"active\":false}";
            }
            
            JSONObject info = new JSONObject();
            info.put("active", true);
            info.put("streamId", currentStreamId);
            info.put("width", screenWidth);
            info.put("height", screenHeight);
            info.put("frameRate", 30);
            info.put("deviceId", "screen-capture-device");
            return info.toString();
        } catch (Exception e) {
            return "{\"active\":false}";
        }
    }
    
    /**
     * Get available media devices
     */
    @JavascriptInterface
    public String getMediaDevices() {
        try {
            JSONObject result = new JSONObject();
            JSONArray devices = new JSONArray();
            
            // Screen capture device
            JSONObject screenDevice = new JSONObject();
            screenDevice.put("deviceId", "screen-capture-device");
            screenDevice.put("kind", "videoinput");
            screenDevice.put("label", "Screen");
            screenDevice.put("groupId", "screen-group");
            devices.put(screenDevice);
            
            // Camera (if available)
            if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
                JSONObject cameraDevice = new JSONObject();
                cameraDevice.put("deviceId", "camera-device");
                cameraDevice.put("kind", "videoinput");
                cameraDevice.put("label", "Camera");
                cameraDevice.put("groupId", "camera-group");
                devices.put(cameraDevice);
            }
            
            // Microphone (if available)
            if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
                JSONObject micDevice = new JSONObject();
                micDevice.put("deviceId", "microphone-device");
                micDevice.put("kind", "audioinput");
                micDevice.put("label", "Microphone");
                micDevice.put("groupId", "audio-group");
                devices.put(micDevice);
            }
            
            result.put("devices", devices);
            return result.toString();
        } catch (Exception e) {
            return "{\"devices\":[]}";
        }
    }
    
    /**
     * Check if media permission is granted
     */
    @JavascriptInterface
    public String checkMediaPermission(String mediaType) {
        try {
            JSONObject result = new JSONObject();
            
            switch (mediaType) {
                case "camera":
                    result.put("granted", ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED);
                    break;
                case "microphone":
                    result.put("granted", ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
                    break;
                case "screen":
                    result.put("granted", isScreenSharingActive.get());
                    break;
                default:
                    result.put("granted", false);
            }
            
            return result.toString();
        } catch (Exception e) {
            return "{\"granted\":false}";
        }
    }
    
    /**
     * Request media permission
     */
    @JavascriptInterface
    public void requestMediaPermission(String mediaType, String callback) {
        try {
            switch (mediaType) {
                case "camera":
                    ActivityCompat.requestPermissions(activity, 
                        new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
                    break;
                case "microphone":
                    ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
                    break;
                case "screen":
                    requestScreenShareStream(callback);
                    return;
            }
            executeCallback(callback, "{\"requested\":true}");
        } catch (Exception e) {
            executeCallback(callback, "{\"requested\":false,\"error\":\"" + e.getMessage() + "\"}");
        }
    }
    
    private void notifyScreenShareEnded() {
        mainHandler.post(() -> {
            try {
                String js = "if(typeof window.onScreenShareEnded === 'function') { window.onScreenShareEnded(); }";
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    webView.evaluateJavascript(js, null);
                } else {
                    webView.loadUrl("javascript:" + js);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error notifying screen share ended", e);
            }
        });
    }
    
    // ==================== SCREEN RECORDING ====================
    
    @JavascriptInterface
    public boolean isRecording() {
        return isRecording;
    }
    
    @JavascriptInterface
    public void startScreenCapture(String callback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            executeCallback(callback, "{\"success\":false,\"error\":\"Screen capture requires Android 5.0+\"}");
            return;
        }
        
        screenCaptureCallback = callback;
        
        mainHandler.post(() -> {
            try {
                activity.startActivityForResult(
                    projectionManager.createScreenCaptureIntent(),
                    REQUEST_SCREEN_CAPTURE
                );
            } catch (Exception e) {
                executeCallback(callback, "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
            }
        });
    }
    
    public boolean handleScreenCaptureResult(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            if (screenCaptureCallback != null) {
                executeCallback(screenCaptureCallback, "{\"success\":false,\"error\":\"Permission denied\"}");
            }
            return false;
        }
        
        try {
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
            
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File recordingsDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES), "ChromeBrowser");
            recordingsDir.mkdirs();
            currentRecordingPath = new File(recordingsDir, "Screen_" + timeStamp + ".mp4").getAbsolutePath();
            
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setOutputFile(currentRecordingPath);
            mediaRecorder.setVideoSize(screenWidth, screenHeight);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setVideoEncodingBitRate(5000000);
            mediaRecorder.setVideoFrameRate(30);
            mediaRecorder.prepare();
            
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder.getSurface(), null, null
            );
            
            mediaRecorder.start();
            isRecording = true;
            
            if (screenCaptureCallback != null) {
                executeCallback(screenCaptureCallback, "{\"success\":true,\"message\":\"Recording started\"}");
            }
            
            showToast("Screen recording started");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error starting screen capture", e);
            if (screenCaptureCallback != null) {
                executeCallback(screenCaptureCallback, "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
            }
            return false;
        }
    }
    
    @JavascriptInterface
    public void stopScreenCapture(String callback) {
        if (!isRecording) {
            executeCallback(callback, "{\"success\":false,\"error\":\"Not recording\"}");
            return;
        }
        
        try {
            isRecording = false;
            
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            }
            
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }
            
            if (mediaProjection != null) {
                mediaProjection.stop();
                mediaProjection = null;
            }
            
            Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            scanIntent.setData(Uri.fromFile(new File(currentRecordingPath)));
            context.sendBroadcast(scanIntent);
            
            showToast("Screen recording saved");
            executeCallback(callback, "{\"success\":true,\"path\":\"" + currentRecordingPath + "\"}");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping screen capture", e);
            executeCallback(callback, "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
        }
    }
    
    // ==================== PERMISSIONS ====================
    
    @JavascriptInterface
    public boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }
    
    @JavascriptInterface
    public void requestPermissions(String permissionsJson, String callback) {
        try {
            JSONArray arr = new JSONArray(permissionsJson);
            List<String> permsToRequest = new ArrayList<>();
            
            for (int i = 0; i < arr.length(); i++) {
                String perm = arr.getString(i);
                if (!hasPermission(perm)) {
                    permsToRequest.add(perm);
                }
            }
            
            if (permsToRequest.isEmpty()) {
                executeCallback(callback, "{\"granted\":true}");
            } else {
                ActivityCompat.requestPermissions(activity, 
                    permsToRequest.toArray(new String[0]), 
                    REQUEST_STORAGE);
            }
        } catch (Exception e) {
            executeCallback(callback, "{\"granted\":false,\"error\":\"" + e.getMessage() + "\"}");
        }
    }
    
    // ==================== CLIPBOARD ====================
    
    @JavascriptInterface
    public boolean copyToClipboard(String text) {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("text", text);
            clipboard.setPrimaryClip(clip);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    @JavascriptInterface
    public String getClipboardText() {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = clipboard.getPrimaryClip().getItemAt(0).getText();
                return text != null ? text.toString() : "";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting clipboard text", e);
        }
        return "";
    }
    
    // ==================== NOTIFICATIONS ====================
    
    @JavascriptInterface
    public boolean showNotification(String title, String message, String iconType) {
        try {
            NotificationManager notificationManager = 
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            
            String channelId = "chrome_browser_api";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    channelId, 
                    "Browser Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                );
                channel.setDescription("Notifications from web pages");
                channel.enableLights(true);
                channel.enableVibration(true);
                notificationManager.createNotificationChannel(channel);
            }
            
            Intent intent = activity.getIntent();
            PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message));
            
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error showing notification", e);
            return false;
        }
    }
    
    @JavascriptInterface
    public boolean showNotificationWithAction(String title, String message, String actionLabel, String actionUrl) {
        try {
            NotificationManager notificationManager = 
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            
            String channelId = "chrome_browser_api_actions";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    channelId, 
                    "Browser Action Notifications",
                    NotificationManager.IMPORTANCE_HIGH
                );
                notificationManager.createNotificationChannel(channel);
            }
            
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(actionUrl));
            PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .addAction(android.R.drawable.ic_menu_view, actionLabel, pendingIntent);
            
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error showing notification with action", e);
            return false;
        }
    }
    
    // ==================== FILE SYSTEM ====================
    
    @JavascriptInterface
    public boolean saveTextFile(String filename, String content) {
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(downloadsDir, filename);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(content.getBytes("UTF-8"));
            fos.close();
            
            Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            scanIntent.setData(Uri.fromFile(file));
            context.sendBroadcast(scanIntent);
            
            showToast("File saved: " + filename);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error saving file", e);
            return false;
        }
    }
    
    @JavascriptInterface
    public boolean saveBinaryFile(String filename, String base64Data) {
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(downloadsDir, filename);
            
            byte[] data = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
            
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data);
            fos.close();
            
            Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            scanIntent.setData(Uri.fromFile(file));
            context.sendBroadcast(scanIntent);
            
            showToast("File saved: " + filename);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error saving binary file", e);
            return false;
        }
    }
    
    @JavascriptInterface
    public String getDownloadsPath() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
    }
    
    @JavascriptInterface
    public boolean fileExists(String path) {
        return new File(path).exists();
    }
    
    @JavascriptInterface
    public boolean deleteFile(String path) {
        try {
            return new File(path).delete();
        } catch (Exception e) {
            return false;
        }
    }
    
    // ==================== CAMERA ====================
    
    @JavascriptInterface
    public boolean isCameraAvailable() {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }
    
    @JavascriptInterface
    public void takePicture(String callback) {
        cameraCallback = callback;
        
        if (!isCameraAvailable()) {
            executeCallback(callback, "{\"success\":false,\"error\":\"No camera available\"}");
            return;
        }
        
        mainHandler.post(() -> {
            try {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (intent.resolveActivity(context.getPackageManager()) != null) {
                    activity.startActivityForResult(intent, REQUEST_CAMERA);
                } else {
                    executeCallback(callback, "{\"success\":false,\"error\":\"No camera app found\"}");
                }
            } catch (Exception e) {
                executeCallback(callback, "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
            }
        });
    }
    
    // ==================== SHARING ====================
    
    @JavascriptInterface
    public boolean share(String title, String text, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, title);
            
            String shareText = text;
            if (url != null && !url.isEmpty()) {
                shareText = text + "\n" + url;
            }
            intent.putExtra(Intent.EXTRA_TEXT, shareText);
            
            Intent chooser = Intent.createChooser(intent, title);
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error sharing", e);
            return false;
        }
    }
    
    @JavascriptInterface
    public boolean shareImage(String title, String imagePath) {
        try {
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) return false;
            
            Uri uri = FileProvider.getUriForFile(context, 
                context.getPackageName() + ".fileprovider", imageFile);
            
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            Intent chooser = Intent.createChooser(intent, title);
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error sharing image", e);
            return false;
        }
    }
    
    // ==================== WEB / URL ====================
    
    @JavascriptInterface
    public boolean openExternal(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    @JavascriptInterface
    public void navigateTo(String url) {
        mainHandler.post(() -> webView.loadUrl(url));
    }
    
    @JavascriptInterface
    public void reload() {
        mainHandler.post(() -> webView.reload());
    }
    
    @JavascriptInterface
    public void goBack() {
        mainHandler.post(() -> {
            if (webView.canGoBack()) webView.goBack();
        });
    }
    
    @JavascriptInterface
    public void goForward() {
        mainHandler.post(() -> {
            if (webView.canGoForward()) webView.goForward();
        });
    }
    
    @JavascriptInterface
    public String getCurrentUrl() {
        return webView.getUrl();
    }
    
    @JavascriptInterface
    public String getPageTitle() {
        return webView.getTitle();
    }
    
    // ==================== VIBRATION ====================
    
    @JavascriptInterface
    public boolean vibrate(long duration) {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(
                        duration, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(duration);
                }
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error vibrating", e);
        }
        return false;
    }
    
    @JavascriptInterface
    public boolean vibratePattern(String patternJson) {
        try {
            JSONArray arr = new JSONArray(patternJson);
            long[] pattern = new long[arr.length()];
            for (int i = 0; i < arr.length(); i++) {
                pattern[i] = arr.getLong(i);
            }
            
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else {
                    vibrator.vibrate(pattern, -1);
                }
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error vibrating with pattern", e);
        }
        return false;
    }
    
    // ==================== STORAGE ====================
    
    @JavascriptInterface
    public boolean setLocalData(String key, String value) {
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences(
                "BrowserAPI_Storage", Context.MODE_PRIVATE);
            prefs.edit().putString(key, value).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    @JavascriptInterface
    public String getLocalData(String key, String defaultValue) {
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences(
                "BrowserAPI_Storage", Context.MODE_PRIVATE);
            return prefs.getString(key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    @JavascriptInterface
    public boolean removeLocalData(String key) {
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences(
                "BrowserAPI_Storage", Context.MODE_PRIVATE);
            prefs.edit().remove(key).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    @JavascriptInterface
    public boolean clearLocalData() {
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences(
                "BrowserAPI_Storage", Context.MODE_PRIVATE);
            prefs.edit().clear().apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    // ==================== NETWORK ====================
    
    @JavascriptInterface
    public String getNetworkInfo() {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            
            JSONObject info = new JSONObject();
            info.put("isConnected", activeNetwork != null && activeNetwork.isConnectedOrConnecting());
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
                if (caps != null) {
                    info.put("hasWifi", caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
                    info.put("hasCellular", caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
                    info.put("hasEthernet", caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
                    info.put("downlinkSpeed", caps.getLinkDownstreamBandwidthKbps());
                    info.put("uplinkSpeed", caps.getLinkUpstreamBandwidthKbps());
                }
            }
            
            return info.toString();
        } catch (Exception e) {
            return "{\"isConnected\":false}";
        }
    }
    
    // ==================== BATTERY ====================
    
    @JavascriptInterface
    public String getBatteryInfo() {
        try {
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            
            JSONObject info = new JSONObject();
            info.put("level", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
            info.put("isCharging", bm.isCharging());
            info.put("chargeTimeRemaining", bm.computeChargeTimeRemaining());
            
            return info.toString();
        } catch (Exception e) {
            return "{\"level\":-1}";
        }
    }
    
    // ==================== CONSOLE ====================
    
    @JavascriptInterface
    public void log(String tag, String message) {
        Log.d(tag, message);
    }
    
    @JavascriptInterface
    public void logError(String tag, String message) {
        Log.e(tag, message);
    }
    
    // ==================== EXECUTE JAVASCRIPT ====================
    
    @JavascriptInterface
    public void executeScript(String script) {
        mainHandler.post(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                webView.evaluateJavascript(script, null);
            } else {
                webView.loadUrl("javascript:" + script);
            }
        });
    }
    
    // ==================== HELPER METHODS ====================
    
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
    
    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }
}
