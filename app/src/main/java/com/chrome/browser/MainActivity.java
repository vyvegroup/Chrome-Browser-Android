package com.chrome.browser;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    
    // UI Components
    private FrameLayout webViewContainer;
    private TabLayout tabLayout;
    private ProgressBar progressBar;
    private EditText urlBar;
    private ImageButton btnBack, btnForward, btnRefresh, btnHome, btnMenu;
    private FloatingActionButton fabNewTab;
    private LinearLayout bottomBar;
    private AppBarLayout appBar;
    
    // Tab Management
    private List<WebView> webViewList = new ArrayList<>();
    private List<String> tabTitles = new ArrayList<>();
    private List<String> tabUrls = new ArrayList<>();
    private List<Bitmap> tabFavicons = new ArrayList<>();
    private int currentTab = 0;
    
    // History
    private Stack<String> backHistory = new Stack<>();
    private Stack<String> forwardHistory = new Stack<>();
    
    // Settings
    private boolean isDesktopMode = false;
    private boolean isJavaScriptEnabled = true;
    private boolean isDarkMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupWebView();
        createNewTab("https://www.google.com");
        setupListeners();
        
        // Handle incoming intents
        if (getIntent() != null && getIntent().getData() != null) {
            String url = getIntent().getData().toString();
            loadUrl(url);
        }
    }

    private void initViews() {
        webViewContainer = findViewById(R.id.webViewContainer);
        tabLayout = findViewById(R.id.tabLayout);
        progressBar = findViewById(R.id.progressBar);
        urlBar = findViewById(R.id.urlBar);
        btnBack = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnHome = findViewById(R.id.btnHome);
        btnMenu = findViewById(R.id.btnMenu);
        fabNewTab = findViewById(R.id.fabNewTab);
        bottomBar = findViewById(R.id.bottomBar);
        appBar = findViewById(R.id.appBar);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = getCurrentWebView().getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString());
        
        // Enable cookies
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(getCurrentWebView(), true);
    }

    private void setupListeners() {
        // URL Bar
        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || 
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String url = urlBar.getText().toString().trim();
                if (!url.isEmpty()) {
                    loadUrl(formatUrl(url));
                }
                return true;
            }
            return false;
        });

        urlBar.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                urlBar.selectAll();
            }
        });

        // Navigation buttons
        btnBack.setOnClickListener(v -> {
            if (getCurrentWebView().canGoBack()) {
                getCurrentWebView().goBack();
            }
        });

        btnForward.setOnClickListener(v -> {
            if (getCurrentWebView().canGoForward()) {
                getCurrentWebView().goForward();
            }
        });

        btnRefresh.setOnClickListener(v -> {
            getCurrentWebView().reload();
        });

        btnHome.setOnClickListener(v -> {
            loadUrl("https://www.google.com");
        });

        btnMenu.setOnClickListener(v -> showMenu());

        // New Tab FAB
        fabNewTab.setOnClickListener(v -> {
            createNewTab("https://www.google.com");
        });

        // Tab Layout
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switchToTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Show tab switcher
                showTabSwitcher();
            }
        });
    }

    private String formatUrl(String url) {
        if (url.contains(".") && !url.contains(" ")) {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return "https://" + url;
            }
            return url;
        } else {
            return "https://www.google.com/search?q=" + Uri.encode(url);
        }
    }

    private void loadUrl(String url) {
        getCurrentWebView().loadUrl(url);
        urlBar.setText(url);
    }

    private WebView getCurrentWebView() {
        if (webViewList.isEmpty()) {
            WebView webView = createWebView();
            webViewList.add(webView);
            return webView;
        }
        return webViewList.get(currentTab);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView() {
        WebView webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ));
        
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
                urlBar.setText(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                updateNavigationButtons();
                
                // Update tab title
                view.getTitle();
                String title = view.getTitle() != null ? view.getTitle() : "Loading...";
                updateTabTitle(currentTab, title, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                
                // Handle special URLs
                if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("intent:")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }
                
                return super.shouldOverrideUrlLoading(view, request);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                updateTabTitle(currentTab, title, view.getUrl());
            }

            @Override
            public void onReceivedIcon(WebView view, Bitmap icon) {
                super.onReceivedIcon(view, icon);
                if (icon != null) {
                    tabFavicons.set(currentTab, icon);
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                super.onShowCustomView(view, callback);
                // Handle fullscreen video
            }

            @Override
            public void onHideCustomView() {
                super.onHideCustomView();
                // Exit fullscreen video
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (checkPermission()) {
                downloadFile(url, userAgent, contentDisposition, mimeType);
            } else {
                requestPermission();
            }
        });

        return webView;
    }

    private void createNewTab(String url) {
        WebView webView = createWebView();
        webViewList.add(webView);
        tabTitles.add("New Tab");
        tabUrls.add(url);
        tabFavicons.add(null);
        
        TabLayout.Tab tab = tabLayout.newTab();
        tab.setText("New Tab");
        tabLayout.addTab(tab);
        
        currentTab = webViewList.size() - 1;
        tab.select();
        
        webViewContainer.removeAllViews();
        webViewContainer.addView(webView);
        webView.loadUrl(url);
    }

    private void switchToTab(int position) {
        if (position >= 0 && position < webViewList.size()) {
            currentTab = position;
            webViewContainer.removeAllViews();
            webViewContainer.addView(webViewList.get(position));
            urlBar.setText(tabUrls.get(position));
            updateNavigationButtons();
        }
    }

    private void closeTab(int position) {
        if (webViewList.size() > 1) {
            webViewList.remove(position);
            tabTitles.remove(position);
            tabUrls.remove(position);
            tabFavicons.remove(position);
            tabLayout.removeTabAt(position);
            
            if (currentTab >= webViewList.size()) {
                currentTab = webViewList.size() - 1;
            }
            switchToTab(currentTab);
        } else {
            // Close app if last tab
            finish();
        }
    }

    private void updateTabTitle(int position, String title, String url) {
        if (position >= 0 && position < tabTitles.size()) {
            tabTitles.set(position, title);
            tabUrls.set(position, url);
            TabLayout.Tab tab = tabLayout.getTabAt(position);
            if (tab != null) {
                tab.setText(title.length() > 15 ? title.substring(0, 15) + "..." : title);
            }
        }
    }

    private void updateNavigationButtons() {
        btnBack.setEnabled(getCurrentWebView().canGoBack());
        btnBack.setAlpha(getCurrentWebView().canGoBack() ? 1.0f : 0.3f);
        btnForward.setEnabled(getCurrentWebView().canGoForward());
        btnForward.setAlpha(getCurrentWebView().canGoForward() ? 1.0f : 0.3f);
    }

    private void showMenu() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.menu_bottom_sheet, null);
        bottomSheet.setContentView(sheetView);
        
        // Menu items
        View itemNewTab = sheetView.findViewById(R.id.itemNewTab);
        View itemNewIncognito = sheetView.findViewById(R.id.itemNewIncognito);
        View itemHistory = sheetView.findViewById(R.id.itemHistory);
        View itemDownloads = sheetView.findViewById(R.id.itemDownloads);
        View itemBookmarks = sheetView.findViewById(R.id.itemBookmarks);
        View itemDesktop = sheetView.findViewById(R.id.itemDesktop);
        View itemSettings = sheetView.findViewById(R.id.itemSettings);
        View itemShare = sheetView.findViewById(R.id.itemShare);
        
        itemNewTab.setOnClickListener(v -> {
            createNewTab("https://www.google.com");
            bottomSheet.dismiss();
        });
        
        itemDesktop.setOnClickListener(v -> {
            toggleDesktopMode();
            bottomSheet.dismiss();
        });
        
        itemSettings.setOnClickListener(v -> {
            showSettings();
            bottomSheet.dismiss();
        });
        
        itemShare.setOnClickListener(v -> {
            sharePage();
            bottomSheet.dismiss();
        });
        
        bottomSheet.show();
    }

    private void showTabSwitcher() {
        // Show tab grid view
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.tab_switcher, null);
        bottomSheet.setContentView(sheetView);
        bottomSheet.show();
    }

    private void toggleDesktopMode() {
        isDesktopMode = !isDesktopMode;
        WebSettings settings = getCurrentWebView().getSettings();
        
        if (isDesktopMode) {
            settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            settings.setLoadWithOverviewMode(false);
        } else {
            settings.setUserAgentString(WebSettings.getDefaultUserAgent(this));
            settings.setLoadWithOverviewMode(true);
        }
        
        getCurrentWebView().reload();
        Toast.makeText(this, isDesktopMode ? "Desktop mode enabled" : "Desktop mode disabled", Toast.LENGTH_SHORT).show();
    }

    private void showSettings() {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Settings")
            .setItems(new CharSequence[]{"Enable JavaScript", "Block Pop-ups", "Clear Cache", "Clear History"}, (dialog, which) -> {
                switch (which) {
                    case 0:
                        isJavaScriptEnabled = !isJavaScriptEnabled;
                        getCurrentWebView().getSettings().setJavaScriptEnabled(isJavaScriptEnabled);
                        break;
                    case 2:
                        getCurrentWebView().clearCache(true);
                        Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show();
                        break;
                    case 3:
                        getCurrentWebView().clearHistory();
                        Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
                        break;
                }
            })
            .show();
    }

    private void sharePage() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, getCurrentWebView().getTitle());
        intent.putExtra(Intent.EXTRA_TEXT, getCurrentWebView().getUrl());
        startActivity(Intent.createChooser(intent, "Share page"));
    }

    private void downloadFile(String url, String userAgent, String contentDisposition, String mimeType) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setMimeType(mimeType);
        request.addRequestHeader("User-Agent", userAgent);
        request.setDescription("Downloading file...");
        request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimeType));
        
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        dm.enqueue(request);
        
        Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (getCurrentWebView().canGoBack()) {
            getCurrentWebView().goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (getCurrentWebView() != null) {
            getCurrentWebView().onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (getCurrentWebView() != null) {
            getCurrentWebView().onPause();
        }
    }

    @Override
    protected void onDestroy() {
        for (WebView webView : webViewList) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
