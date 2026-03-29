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

/**
 * BrowserAPI - JavaScript Interface for web-to-native communication
 * Allows web pages to access native Android features
 * 
 * Usage from JavaScript:
 * // Check if API is available
 * if (window.ChromeBrowserAPI) {
 *     // Get device info
 *     const info = JSON.parse(ChromeBrowserAPI.getDeviceInfo());
 *     
 *     // Start screen capture
 *     ChromeBrowserAPI.startScreenCapture("myCallback");
 *     
 *     // Show notification
 *     ChromeBrowserAPI.showNotification("Title", "Message", "info");
 * }
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
    
    // Callbacks stored for async operations
    private String screenCaptureCallback;
    private String cameraCallback;
    private String microphoneCallback;
    
    // API version
    private static final String API_VERSION = "2.0.0";
    
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
    
    /**
     * Get API version
     */
    @JavascriptInterface
    public String getVersion() {
        return API_VERSION;
    }
    
    /**
     * Check if API is available
     */
    @JavascriptInterface
    public boolean isAvailable() {
        return true;
    }
    
    /**
     * Get device information
     */
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
            info.put("appVersion", "1.3.0");
            info.put("apiVersion", API_VERSION);
            return info.toString();
        } catch (Exception e) {
            return "{}";
        }
    }
    
    // ==================== PERMISSIONS ====================
    
    /**
     * Check if a permission is granted
     */
    @JavascriptInterface
    public boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Request permissions
     */
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
    
    /**
     * Copy text to clipboard
     */
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
    
    /**
     * Get text from clipboard
     */
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
    
    /**
     * Show a native notification
     */
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
            
            // Create intent to open browser
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
    
    /**
     * Show a notification with action buttons
     */
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
    
    // ==================== SCREEN CAPTURE ====================
    
    /**
     * Check if screen recording is available
     */
    @JavascriptInterface
    public boolean isScreenCaptureAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }
    
    /**
     * Check if currently recording
     */
    @JavascriptInterface
    public boolean isRecording() {
        return isRecording;
    }
    
    /**
     * Start screen capture (requests permission first)
     * Similar to navigator.mediaDevices.getDisplayMedia()
     */
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
    
    /**
     * Handle screen capture permission result
     */
    public boolean handleScreenCaptureResult(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            if (screenCaptureCallback != null) {
                executeCallback(screenCaptureCallback, "{\"success\":false,\"error\":\"Permission denied\"}");
            }
            return false;
        }
        
        try {
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
            
            // Create recording file
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File recordingsDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES), "ChromeBrowser");
            recordingsDir.mkdirs();
            currentRecordingPath = new File(recordingsDir, "Screen_" + timeStamp + ".mp4").getAbsolutePath();
            
            // Setup media recorder
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setOutputFile(currentRecordingPath);
            mediaRecorder.setVideoSize(screenWidth, screenHeight);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setVideoEncodingBitRate(5000000);
            mediaRecorder.setVideoFrameRate(30);
            mediaRecorder.prepare();
            
            // Create virtual display
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
    
    /**
     * Stop screen capture
     */
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
            
            // Scan file to make it visible in gallery
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
    
    // ==================== FILE SYSTEM ====================
    
    /**
     * Save text to a file
     */
    @JavascriptInterface
    public boolean saveTextFile(String filename, String content) {
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(downloadsDir, filename);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(content.getBytes("UTF-8"));
            fos.close();
            
            // Make file visible
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
    
    /**
     * Save binary data (base64) to a file
     */
    @JavascriptInterface
    public boolean saveBinaryFile(String filename, String base64Data) {
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(downloadsDir, filename);
            
            // Decode base64
            byte[] data = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
            
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data);
            fos.close();
            
            // Make file visible
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
    
    /**
     * Get downloads directory path
     */
    @JavascriptInterface
    public String getDownloadsPath() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
    }
    
    /**
     * Check if a file exists
     */
    @JavascriptInterface
    public boolean fileExists(String path) {
        return new File(path).exists();
    }
    
    /**
     * Delete a file
     */
    @JavascriptInterface
    public boolean deleteFile(String path) {
        try {
            return new File(path).delete();
        } catch (Exception e) {
            return false;
        }
    }
    
    // ==================== CAMERA ====================
    
    /**
     * Check if camera is available
     */
    @JavascriptInterface
    public boolean isCameraAvailable() {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }
    
    /**
     * Open camera to take a picture
     */
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
    
    /**
     * Share content using Android share sheet
     */
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
    
    /**
     * Share an image from URL
     */
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
    
    /**
     * Open URL in external browser
     */
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
    
    /**
     * Open URL in the browser's current tab
     */
    @JavascriptInterface
    public void navigateTo(String url) {
        mainHandler.post(() -> webView.loadUrl(url));
    }
    
    /**
     * Reload current page
     */
    @JavascriptInterface
    public void reload() {
        mainHandler.post(() -> webView.reload());
    }
    
    /**
     * Go back in history
     */
    @JavascriptInterface
    public void goBack() {
        mainHandler.post(() -> {
            if (webView.canGoBack()) webView.goBack();
        });
    }
    
    /**
     * Go forward in history
     */
    @JavascriptInterface
    public void goForward() {
        mainHandler.post(() -> {
            if (webView.canGoForward()) webView.goForward();
        });
    }
    
    /**
     * Get current URL
     */
    @JavascriptInterface
    public String getCurrentUrl() {
        return webView.getUrl();
    }
    
    /**
     * Get page title
     */
    @JavascriptInterface
    public String getPageTitle() {
        return webView.getTitle();
    }
    
    // ==================== VIBRATION ====================
    
    /**
     * Vibrate device
     */
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
    
    /**
     * Vibrate with pattern
     */
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
    
    /**
     * Save data to local storage
     */
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
    
    /**
     * Get data from local storage
     */
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
    
    /**
     * Remove data from local storage
     */
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
    
    /**
     * Clear all local storage
     */
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
    
    /**
     * Get network information
     */
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
    
    /**
     * Get battery information
     */
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
    
    /**
     * Log to Android logcat
     */
    @JavascriptInterface
    public void log(String tag, String message) {
        Log.d(tag, message);
    }
    
    /**
     * Log error to Android logcat
     */
    @JavascriptInterface
    public void logError(String tag, String message) {
        Log.e(tag, message);
    }
    
    // ==================== EXECUTE JAVASCRIPT ====================
    
    /**
     * Execute JavaScript in the WebView
     */
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
