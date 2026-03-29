package com.chrome.browser;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int FILE_PICK_REQUEST = 1002;
    private static final int REQUEST_SCREEN_CAPTURE = 1003;
    private static final String PREFS_NAME = "ChromeBrowserPrefs";
    private static final String KEY_BOOKMARKS = "bookmarks";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_TABS = "tabs";
    private static final String KEY_CURRENT_TAB = "current_tab";
    private static final String KEY_JAVASCRIPT = "javascript_enabled";
    private static final String KEY_DESKTOP_MODE = "desktop_mode";
    private static final String KEY_EXTENSIONS = "extensions";
    private static final String KEY_AD_BLOCK = "ad_block_enabled";

    // UI
    private FrameLayout webViewContainer;
    private LinearProgressIndicator progressIndicator;
    private TextInputEditText urlBar;
    private ImageView securityIcon;
    private ImageButton btnBack, btnForward, btnRefresh, btnHome, btnMenu, btnTabs;
    private TextView tabCountText;
    private FrameLayout tabCountContainer;
    private LinearLayout navigationRow, floatingBarContainer;
    private MaterialCardView floatingUrlCard, pageHeader;
    private TextView headerTitle;
    private ImageView headerSecurityIcon;
    private SwipeRefreshLayout swipeRefresh;
    
    // For animations
    private boolean isUrlBarExpanded = false;
    private Handler uiHandler = new Handler(Looper.getMainLooper());

    // Tabs
    private List<TabInfo> tabs = new ArrayList<>();
    private int currentTabIndex = 0;

    // Settings
    private SharedPreferences preferences;
    private boolean isJavaScriptEnabled = true;
    private boolean isDesktopMode = false;
    private boolean isIncognitoMode = false;
    private boolean isAdBlockEnabled = true;

    // Bookmarks & History
    private List<Bookmark> bookmarks = new ArrayList<>();
    private List<HistoryItem> history = new ArrayList<>();
    
    // Extensions
    private List<Extension> extensions = new ArrayList<>();
    
    // DevTools
    private DevToolsServer devToolsServer;
    
    // Browser API
    private BrowserAPI browserAPI;

    public static class TabInfo {
        String id;
        WebView webView;
        String title;
        String url;
        Bitmap favicon;
        boolean isIncognito;

        TabInfo(String id, WebView webView, boolean isIncognito) {
            this.id = id;
            this.webView = webView;
            this.isIncognito = isIncognito;
            this.title = "New Tab";
            this.url = "about:blank";
        }
        
        TabInfo(String id, String url, String title, boolean isIncognito) {
            this.id = id;
            this.url = url;
            this.title = title;
            this.isIncognito = isIncognito;
            this.webView = null;
            this.favicon = null;
        }
    }

    public static class Bookmark {
        String title, url;
        long timestamp;
        Bookmark(String title, String url, long timestamp) {
            this.title = title; this.url = url; this.timestamp = timestamp;
        }
    }

    public static class HistoryItem {
        String title, url;
        long timestamp;
        HistoryItem(String title, String url, long timestamp) {
            this.title = title; this.url = url; this.timestamp = timestamp;
        }
    }

    public static class Extension {
        String id;
        String name;
        String description;
        String version;
        String source; // "store", "zip", "userscript"
        boolean enabled;
        String script;
        int iconRes;
        
        Extension(String id, String name, String desc, String version, String source, boolean enabled, String script) {
            this.id = id;
            this.name = name;
            this.description = desc;
            this.version = version;
            this.source = source;
            this.enabled = enabled;
            this.script = script;
            this.iconRes = R.drawable.ic_extensions;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        loadSettings();
        loadBookmarks();
        loadHistory();
        loadExtensions();

        initViews();
        setupListeners();
        setupSwipeRefresh();
        
        // Start DevTools server
        startDevToolsServer();
        
        // Restore tabs or create new one
        if (!restoreTabs()) {
            createNewTab("https://www.google.com", false);
        }

        if (getIntent() != null && getIntent().getData() != null) {
            loadUrl(getIntent().getData().toString());
        }
    }
    
    private void loadSettings() {
        isJavaScriptEnabled = preferences.getBoolean(KEY_JAVASCRIPT, true);
        isDesktopMode = preferences.getBoolean(KEY_DESKTOP_MODE, false);
        isAdBlockEnabled = preferences.getBoolean(KEY_AD_BLOCK, true);
    }
    
    private void saveSettings() {
        preferences.edit()
            .putBoolean(KEY_JAVASCRIPT, isJavaScriptEnabled)
            .putBoolean(KEY_DESKTOP_MODE, isDesktopMode)
            .putBoolean(KEY_AD_BLOCK, isAdBlockEnabled)
            .apply();
    }
    
    private void setupSwipeRefresh() {
        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.chrome_blue),
            ContextCompat.getColor(this, R.color.chrome_red),
            ContextCompat.getColor(this, R.color.chrome_yellow),
            ContextCompat.getColor(this, R.color.chrome_green)
        );
        swipeRefresh.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.white));
        swipeRefresh.setProgressViewEndTarget(true, 200);
        
        swipeRefresh.setOnRefreshListener(() -> {
            TabInfo tab = getCurrentTab();
            if (tab != null && tab.webView != null) {
                tab.webView.reload();
            }
            uiHandler.postDelayed(() -> swipeRefresh.setRefreshing(false), 1500);
        });
    }

    private void initViews() {
        webViewContainer = findViewById(R.id.webViewContainer);
        progressIndicator = findViewById(R.id.progressIndicator);
        urlBar = findViewById(R.id.urlBar);
        securityIcon = findViewById(R.id.securityIcon);
        btnBack = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnHome = findViewById(R.id.btnHome);
        btnMenu = findViewById(R.id.btnMenu);
        btnTabs = findViewById(R.id.btnTabs);
        tabCountText = findViewById(R.id.tabCountText);
        tabCountContainer = findViewById(R.id.tabCountContainer);
        navigationRow = findViewById(R.id.navigationRow);
        floatingBarContainer = findViewById(R.id.floatingBarContainer);
        floatingUrlCard = findViewById(R.id.floatingUrlCard);
        pageHeader = findViewById(R.id.pageHeader);
        headerTitle = findViewById(R.id.headerTitle);
        headerSecurityIcon = findViewById(R.id.headerSecurityIcon);
    }

    private void setupListeners() {
        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH) {
                String url = urlBar.getText().toString().trim();
                if (!url.isEmpty()) {
                    loadUrl(formatUrl(url));
                    hideKeyboard();
                    collapseUrlBar();
                }
                return true;
            }
            return false;
        });

        urlBar.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                expandUrlBar();
            }
        });

        btnBack.setOnClickListener(v -> goBack());
        btnForward.setOnClickListener(v -> goForward());
        btnRefresh.setOnClickListener(v -> refresh());
        btnHome.setOnClickListener(v -> loadUrl("https://www.google.com"));
        btnMenu.setOnClickListener(v -> showMenu());
        tabCountContainer.setOnClickListener(v -> showTabsGrid());
        btnTabs.setOnClickListener(v -> showTabsGrid());
        
        findViewById(R.id.btnShowUrl).setOnClickListener(v -> {
            pageHeader.setVisibility(View.GONE);
            floatingBarContainer.setVisibility(View.VISIBLE);
            urlBar.requestFocus();
            showKeyboard();
        });

        // Touch listener to collapse URL bar
        webViewContainer.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (urlBar.hasFocus()) {
                    hideKeyboard();
                    collapseUrlBar();
                }
            }
            return false;
        });
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
    }
    
    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            urlBar.requestFocus();
            imm.showSoftInput(urlBar, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private String formatUrl(String url) {
        if (url.startsWith("chrome://")) return url;
        if (url.contains(".") && !url.contains(" ") && !url.startsWith("http"))
            return "https://" + url;
        if (!url.contains(".") || url.contains(" "))
            return "https://www.google.com/search?q=" + Uri.encode(url);
        return url;
    }

    private void loadUrl(String url) {
        TabInfo tab = getCurrentTab();
        if (tab != null && tab.webView != null) {
            tab.webView.loadUrl(url);
            urlBar.setText(url);
            updateSecurityIcon(url);
        }
    }

    private void updateSecurityIcon(String url) {
        int icon = url.startsWith("https://") ? R.drawable.ic_lock : R.drawable.ic_lock_open;
        int color = url.startsWith("https://") ? R.color.chrome_green : R.color.chrome_secondary;
        securityIcon.setImageResource(icon);
        securityIcon.setColorFilter(ContextCompat.getColor(this, color));
        headerSecurityIcon.setImageResource(icon);
    }

    private void goBack() {
        TabInfo tab = getCurrentTab();
        if (tab != null && tab.webView != null && tab.webView.canGoBack()) {
            tab.webView.goBack();
        } else {
            showSnackbar("No previous page");
        }
    }

    private void goForward() {
        TabInfo tab = getCurrentTab();
        if (tab != null && tab.webView != null && tab.webView.canGoForward()) {
            tab.webView.goForward();
        }
    }

    private void refresh() {
        TabInfo tab = getCurrentTab();
        if (tab != null && tab.webView != null) tab.webView.reload();
    }

    private TabInfo getCurrentTab() {
        if (tabs.isEmpty()) return null;
        if (currentTabIndex >= tabs.size()) currentTabIndex = tabs.size() - 1;
        return tabs.get(currentTabIndex);
    }

    private void showSnackbar(String message) {
        Snackbar.make(floatingUrlCard, message, Snackbar.LENGTH_SHORT).show();
    }
    
    private void expandUrlBar() {
        if (!isUrlBarExpanded) {
            navigationRow.setVisibility(View.VISIBLE);
            navigationRow.setAlpha(0f);
            navigationRow.animate().alpha(1f).setDuration(200).start();
            isUrlBarExpanded = true;
        }
    }
    
    private void collapseUrlBar() {
        if (isUrlBarExpanded) {
            navigationRow.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    navigationRow.setVisibility(View.GONE);
                })
                .start();
            isUrlBarExpanded = false;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void createNewTab(String url, boolean incognito) {
        WebView webView = createWebView(incognito);
        TabInfo tab = new TabInfo(UUID.randomUUID().toString(), webView, incognito);
        tabs.add(tab);
        currentTabIndex = tabs.size() - 1;
        webViewContainer.removeAllViews();
        webViewContainer.addView(webView);
        if (url != null) webView.loadUrl(url);
        updateTabCount();
        updateIncognitoUI();
        saveTabs();
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private void createNewTabFromSaved(TabInfo savedTab) {
        WebView webView = createWebView(savedTab.isIncognito);
        TabInfo tab = new TabInfo(savedTab.id, webView, savedTab.isIncognito);
        tab.title = savedTab.title;
        tab.url = savedTab.url;
        tabs.add(tab);
        currentTabIndex = tabs.size() - 1;
        webViewContainer.removeAllViews();
        webViewContainer.addView(webView);
        webView.loadUrl(savedTab.url);
        updateTabCount();
        updateIncognitoUI();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView(boolean incognito) {
        WebView webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(isJavaScriptEnabled);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setCacheMode(incognito ? WebSettings.LOAD_NO_CACHE : WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        // Enable DevTools
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        if (isDesktopMode) {
            s.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
        }

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(!incognito);
        cm.setAcceptThirdPartyCookies(webView, !incognito);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressIndicator.setVisibility(View.VISIBLE);
                progressIndicator.setProgress(0);
                urlBar.setText(url);
                updateSecurityIcon(url);
                TabInfo t = findTab(view);
                if (t != null) t.url = url;
                swipeRefresh.setRefreshing(false);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressIndicator.hide();
                updateNavigationButtons();
                String title = view.getTitle() != null ? view.getTitle() : "Page";
                TabInfo t = findTab(view);
                if (t != null) { 
                    t.title = title; 
                    t.url = url;
                    updateHeader(t);
                    
                    // Inject user scripts
                    injectUserScripts(view, t.isIncognito);
                }
                if (!isIncognitoMode && t != null && !t.isIncognito) addToHistory(title, url);
                saveTabs();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("tel:")) { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse(url))); return true; }
                if (url.startsWith("mailto:")) { startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse(url))); return true; }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                progressIndicator.setProgress(p);
            }
            @Override
            public void onReceivedTitle(WebView view, String title) {
                TabInfo t = findTab(view);
                if (t != null) {
                    t.title = title != null ? title : "Page";
                    updateHeader(t);
                }
            }
            @Override
            public void onReceivedIcon(WebView view, Bitmap icon) {
                TabInfo t = findTab(view);
                if (t != null) t.favicon = icon;
            }
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                webViewContainer.setVisibility(View.GONE);
                FrameLayout fullscreenContainer = findViewById(R.id.fullscreenContainer);
                fullscreenContainer.setVisibility(View.VISIBLE);
                fullscreenContainer.addView(view);
            }
            @Override
            public void onHideCustomView() {
                FrameLayout fullscreenContainer = findViewById(R.id.fullscreenContainer);
                fullscreenContainer.removeAllViews();
                fullscreenContainer.setVisibility(View.GONE);
                webViewContainer.setVisibility(View.VISIBLE);
            }
        });

        webView.setDownloadListener((url, ua, cd, mt, cl) -> {
            if (checkPermission()) downloadFile(url, ua, cd, mt);
            else requestPermission();
        });

        webView.setOnLongClickListener(v -> {
            WebView.HitTestResult r = webView.getHitTestResult();
            handleLongPress(r);
            return true;
        });
        
        // Add Browser API JavaScript Interface
        if (browserAPI == null) {
            browserAPI = new BrowserAPI(this, webView);
        }
        webView.addJavascriptInterface(browserAPI, "ChromeBrowserAPI");

        return webView;
    }
    
    private void injectUserScripts(WebView webView, boolean isIncognito) {
        if (isIncognito) return;
        
        StringBuilder scriptBuilder = new StringBuilder();
        for (Extension ext : extensions) {
            if (ext.enabled && ext.script != null && !ext.script.isEmpty()) {
                scriptBuilder.append(ext.script).append("\n");
            }
        }
        
        if (scriptBuilder.length() > 0) {
            String wrappedScript = "(function() { " + scriptBuilder.toString() + " })();";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                webView.evaluateJavascript(wrappedScript, null);
            }
        }
    }
    
    private void updateHeader(TabInfo tab) {
        headerTitle.setText(tab.title);
    }

    private TabInfo findTab(WebView w) {
        for (TabInfo t : tabs) if (t.webView == w) return t;
        return null;
    }

    private void handleLongPress(WebView.HitTestResult r) {
        int type = r.getType();
        String extra = r.getExtra();
        BottomSheetDialog d = new BottomSheetDialog(this);

        if (type == WebView.HitTestResult.IMAGE_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
            View v = LayoutInflater.from(this).inflate(R.layout.context_menu_image, null);
            d.setContentView(v);
            v.findViewById(R.id.action_open_image).setOnClickListener(x -> { createNewTab(extra, isIncognitoMode); d.dismiss(); });
            v.findViewById(R.id.action_save_image).setOnClickListener(x -> { if(checkPermission()) downloadFile(extra, "Mozilla/5.0", "image", "image/*"); d.dismiss(); });
            v.findViewById(R.id.action_copy_image_url).setOnClickListener(x -> { copyToClipboard(extra); showSnackbar("URL copied"); d.dismiss(); });
            v.findViewById(R.id.action_search_image).setOnClickListener(x -> { createNewTab("https://www.google.com/searchbyimage?image_url=" + Uri.encode(extra), isIncognitoMode); d.dismiss(); });
        } else if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
            View v = LayoutInflater.from(this).inflate(R.layout.context_menu_link, null);
            d.setContentView(v);
            v.findViewById(R.id.action_open_new_tab).setOnClickListener(x -> { createNewTab(extra, isIncognitoMode); d.dismiss(); });
            v.findViewById(R.id.action_open_incognito).setOnClickListener(x -> { createNewTab(extra, true); d.dismiss(); });
            v.findViewById(R.id.action_copy_link).setOnClickListener(x -> { copyToClipboard(extra); showSnackbar("Link copied"); d.dismiss(); });
            v.findViewById(R.id.action_download_link).setOnClickListener(x -> { if(checkPermission()) downloadFile(extra, "Mozilla/5.0", "link", "*/*"); d.dismiss(); });
        } else {
            return;
        }
        d.show();
    }

    private void copyToClipboard(String text) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("text", text));
    }

    private void updateNavigationButtons() {
        TabInfo t = getCurrentTab();
        boolean canBack = t != null && t.webView != null && t.webView.canGoBack();
        boolean canFwd = t != null && t.webView != null && t.webView.canGoForward();
        btnBack.setEnabled(canBack);
        btnBack.setAlpha(canBack ? 1f : 0.3f);
        btnForward.setEnabled(canFwd);
        btnForward.setAlpha(canFwd ? 1f : 0.3f);
    }

    private void updateTabCount() {
        tabCountText.setText(String.valueOf(tabs.size()));
    }

    private void updateIncognitoUI() {
        int bgColor = isIncognitoMode ? 0xFF343A40 : 0xFFFFFFFF;
        floatingUrlCard.setCardBackgroundColor(bgColor);
    }

    // Tab Persistence
    private void saveTabs() {
        try {
            JSONArray tabsArray = new JSONArray();
            for (TabInfo tab : tabs) {
                if (!tab.isIncognito) {
                    JSONObject obj = new JSONObject();
                    obj.put("id", tab.id);
                    obj.put("url", tab.url);
                    obj.put("title", tab.title);
                    tabsArray.put(obj);
                }
            }
            preferences.edit()
                .putString(KEY_TABS, tabsArray.toString())
                .putInt(KEY_CURRENT_TAB, currentTabIndex)
                .apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private boolean restoreTabs() {
        try {
            String tabsJson = preferences.getString(KEY_TABS, "[]");
            JSONArray tabsArray = new JSONArray(tabsJson);
            
            if (tabsArray.length() == 0) return false;
            
            for (int i = 0; i < tabsArray.length(); i++) {
                JSONObject obj = tabsArray.getJSONObject(i);
                TabInfo tab = new TabInfo(
                    obj.getString("id"),
                    obj.getString("url"),
                    obj.getString("title"),
                    false
                );
                tabs.add(tab);
            }
            
            currentTabIndex = preferences.getInt(KEY_CURRENT_TAB, 0);
            if (currentTabIndex >= tabs.size()) currentTabIndex = 0;
            
            TabInfo currentTab = tabs.get(currentTabIndex);
            WebView webView = createWebView(false);
            currentTab.webView = webView;
            webViewContainer.addView(webView);
            webView.loadUrl(currentTab.url);
            
            updateTabCount();
            updateIncognitoUI();
            
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Menu
    private void showMenu() {
        BottomSheetDialog d = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.menu_bottom_sheet, null);
        d.setContentView(v);

        v.findViewById(R.id.itemNewTab).setOnClickListener(x -> { createNewTab("https://www.google.com", false); d.dismiss(); });
        v.findViewById(R.id.itemNewIncognito).setOnClickListener(x -> { createNewTab("https://www.google.com", true); d.dismiss(); });
        v.findViewById(R.id.itemHistory).setOnClickListener(x -> { showHistory(); d.dismiss(); });
        v.findViewById(R.id.itemDownloads).setOnClickListener(x -> { startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)); d.dismiss(); });
        v.findViewById(R.id.itemBookmarks).setOnClickListener(x -> { showBookmarks(); d.dismiss(); });
        v.findViewById(R.id.itemAddBookmark).setOnClickListener(x -> { addCurrentToBookmarks(); d.dismiss(); });
        v.findViewById(R.id.itemDesktop).setOnClickListener(x -> { toggleDesktopMode(); d.dismiss(); });
        v.findViewById(R.id.itemShare).setOnClickListener(x -> { sharePage(); d.dismiss(); });
        v.findViewById(R.id.itemExtensions).setOnClickListener(x -> { showExtensions(); d.dismiss(); });
        v.findViewById(R.id.itemSettings).setOnClickListener(x -> { showSettings(); d.dismiss(); });

        d.show();
    }

    // Tabs Grid
    private void showTabsGrid() {
        BottomSheetDialog d = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.tabs_grid, null);
        d.setContentView(v);

        RecyclerView rv = v.findViewById(R.id.tabsRecyclerView);
        rv.setLayoutManager(new GridLayoutManager(this, 2));
        final TabsAdapter[] adapterHolder = new TabsAdapter[1];
        adapterHolder[0] = new TabsAdapter(tabs, currentTabIndex, new TabsAdapter.TabListener() {
            @Override public void onTabClick(int pos) { switchToTab(pos); d.dismiss(); }
            @Override public void onTabClose(int pos) { closeTab(pos); adapterHolder[0].updateTabs(tabs, currentTabIndex); if(tabs.isEmpty()) { d.dismiss(); finish(); }}
            @Override public void onTabGroup(int pos) { /* Tab grouping */ }
        });
        rv.setAdapter(adapterHolder[0]);

        v.findViewById(R.id.fabNewTabGrid).setOnClickListener(x -> { createNewTab("https://www.google.com", isIncognitoMode); d.dismiss(); });
        v.findViewById(R.id.btnDone).setOnClickListener(x -> d.dismiss());

        d.show();
    }

    private void switchToTab(int pos) {
        if (pos >= 0 && pos < tabs.size()) {
            currentTabIndex = pos;
            TabInfo t = tabs.get(pos);
            
            if (t.webView == null) {
                WebView webView = createWebView(t.isIncognito);
                t.webView = webView;
                webViewContainer.removeAllViews();
                webViewContainer.addView(webView);
                webView.loadUrl(t.url);
            } else {
                webViewContainer.removeAllViews();
                webViewContainer.addView(t.webView);
            }
            
            urlBar.setText(t.url);
            isIncognitoMode = t.isIncognito;
            updateIncognitoUI();
            updateNavigationButtons();
            updateSecurityIcon(t.url);
            updateHeader(t);
            saveTabs();
        }
    }

    private void closeTab(int pos) {
        if (pos >= 0 && pos < tabs.size()) {
            if (tabs.get(pos).webView != null) {
                tabs.get(pos).webView.destroy();
            }
            tabs.remove(pos);
            if (tabs.isEmpty()) { finish(); return; }
            if (currentTabIndex >= tabs.size()) currentTabIndex = tabs.size() - 1;
            switchToTab(currentTabIndex);
            updateTabCount();
            saveTabs();
        }
    }

    // Settings
    private void showSettings() {
        BottomSheetDialog d = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.settings_bottom_sheet, null);
        d.setContentView(v);

        MaterialSwitch js = v.findViewById(R.id.switchJavaScript);
        MaterialSwitch dm = v.findViewById(R.id.switchDarkMode);
        MaterialSwitch dk = v.findViewById(R.id.switchDesktop);
        MaterialSwitch ab = v.findViewById(R.id.switchAdBlock);

        js.setChecked(isJavaScriptEnabled);
        dk.setChecked(isDesktopMode);
        ab.setChecked(isAdBlockEnabled);

        js.setOnCheckedChangeListener((b, c) -> { 
            isJavaScriptEnabled = c; 
            for(TabInfo t: tabs) if (t.webView != null) t.webView.getSettings().setJavaScriptEnabled(c);
            saveSettings();
        });
        dk.setOnCheckedChangeListener((b, c) -> { 
            isDesktopMode = c; 
            applyDesktopMode();
            saveSettings();
        });
        ab.setOnCheckedChangeListener((b, c) -> {
            isAdBlockEnabled = c;
            saveSettings();
        });

        v.findViewById(R.id.btnClearCache).setOnClickListener(x -> { 
            for(TabInfo t: tabs) if (t.webView != null) t.webView.clearCache(true); 
            showSnackbar("Cache cleared"); 
        });
        v.findViewById(R.id.btnClearHistory).setOnClickListener(x -> { 
            history.clear(); 
            preferences.edit().remove(KEY_HISTORY).apply(); 
            showSnackbar("History cleared"); 
        });
        v.findViewById(R.id.btnClearCookies).setOnClickListener(x -> { 
            CookieManager.getInstance().removeAllCookies(null); 
            showSnackbar("Cookies cleared"); 
        });
        v.findViewById(R.id.btnClearAll).setOnClickListener(x -> new MaterialAlertDialogBuilder(this)
            .setTitle("Clear all data?").setMessage("This will clear cache, history, cookies, and bookmarks.")
            .setPositiveButton("Clear", (dlg, w) -> { 
                for(TabInfo t: tabs) if (t.webView != null) t.webView.clearCache(true); 
                history.clear(); 
                bookmarks.clear(); 
                CookieManager.getInstance().removeAllCookies(null); 
                preferences.edit().clear().apply(); 
                showSnackbar("All data cleared"); 
            })
            .setNegativeButton("Cancel", null).show());
        
        v.findViewById(R.id.itemDevTools).setOnClickListener(x -> {
            d.dismiss();
            openDevTools();
        });
        
        v.findViewById(R.id.btnCloseSettings).setOnClickListener(x -> d.dismiss());

        d.show();
    }
    
    private void openDevTools() {
        TabInfo currentTab = getCurrentTab();
        String url = currentTab != null ? currentTab.url : "about:blank";
        
        Intent intent = new Intent(this, DevToolsActivity.class);
        intent.putExtra("url", url);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void toggleDesktopMode() {
        isDesktopMode = !isDesktopMode;
        applyDesktopMode();
        saveSettings();
        showSnackbar(isDesktopMode ? "Desktop mode enabled" : "Desktop mode disabled");
    }

    private void applyDesktopMode() {
        for (TabInfo t : tabs) {
            if (t.webView != null) {
                WebSettings s = t.webView.getSettings();
                s.setUserAgentString(isDesktopMode ? "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36" : WebSettings.getDefaultUserAgent(this));
                t.webView.reload();
            }
        }
    }

    // Bookmarks
    private void showBookmarks() {
        BottomSheetDialog d = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.bookmarks_bottom_sheet, null);
        d.setContentView(v);
        RecyclerView rv = v.findViewById(R.id.bookmarksRecyclerView);
        rv.setLayoutManager(new LinearLayoutManager(this));
        final BookmarksAdapter[] adapterHolder = new BookmarksAdapter[1];
        adapterHolder[0] = new BookmarksAdapter(bookmarks, new BookmarksAdapter.BookmarkListener() {
            @Override public void onBookmarkClick(Bookmark b) { loadUrl(b.url); d.dismiss(); }
            @Override public void onBookmarkDelete(Bookmark b, int p) { bookmarks.remove(p); saveBookmarks(); adapterHolder[0].updateBookmarks(bookmarks); }
            @Override public void onBookmarkEdit(Bookmark b) {}
        });
        rv.setAdapter(adapterHolder[0]);
        v.findViewById(R.id.emptyBookmarks).setVisibility(bookmarks.isEmpty() ? View.VISIBLE : View.GONE);
        d.show();
    }

    private void addCurrentToBookmarks() {
        TabInfo t = getCurrentTab();
        if (t != null) {
            bookmarks.add(0, new Bookmark(t.title, t.url, System.currentTimeMillis()));
            saveBookmarks();
            showSnackbar("Bookmark added");
        }
    }

    private void saveBookmarks() {
        try {
            JSONArray a = new JSONArray();
            for (Bookmark b : bookmarks) { JSONObject o = new JSONObject(); o.put("title", b.title); o.put("url", b.url); o.put("timestamp", b.timestamp); a.put(o); }
            preferences.edit().putString(KEY_BOOKMARKS, a.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadBookmarks() {
        try {
            JSONArray a = new JSONArray(preferences.getString(KEY_BOOKMARKS, "[]"));
            for (int i = 0; i < a.length(); i++) { JSONObject o = a.getJSONObject(i); bookmarks.add(new Bookmark(o.getString("title"), o.getString("url"), o.getLong("timestamp"))); }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // History
    private void showHistory() {
        BottomSheetDialog d = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.history_bottom_sheet, null);
        d.setContentView(v);
        RecyclerView rv = v.findViewById(R.id.historyRecyclerView);
        rv.setLayoutManager(new LinearLayoutManager(this));
        final HistoryAdapter[] adapterHolder = new HistoryAdapter[1];
        adapterHolder[0] = new HistoryAdapter(history, new HistoryAdapter.HistoryListener() {
            @Override public void onHistoryClick(HistoryItem h) { loadUrl(h.url); d.dismiss(); }
            @Override public void onHistoryDelete(HistoryItem h, int p) { history.remove(p); saveHistory(); adapterHolder[0].updateHistory(history); }
        });
        rv.setAdapter(adapterHolder[0]);
        v.findViewById(R.id.emptyHistory).setVisibility(history.isEmpty() ? View.VISIBLE : View.GONE);
        d.show();
    }

    private void addToHistory(String title, String url) {
        history.add(0, new HistoryItem(title, url, System.currentTimeMillis()));
        if (history.size() > 1000) history = history.subList(0, 1000);
        saveHistory();
    }

    private void saveHistory() {
        try {
            JSONArray a = new JSONArray();
            for (HistoryItem h : history) { JSONObject o = new JSONObject(); o.put("title", h.title); o.put("url", h.url); o.put("timestamp", h.timestamp); a.put(o); }
            preferences.edit().putString(KEY_HISTORY, a.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadHistory() {
        try {
            JSONArray a = new JSONArray(preferences.getString(KEY_HISTORY, "[]"));
            for (int i = 0; i < a.length(); i++) { JSONObject o = a.getJSONObject(i); history.add(new HistoryItem(o.getString("title"), o.getString("url"), o.getLong("timestamp"))); }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // Extensions
    private void showExtensions() {
        BottomSheetDialog d = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.extensions_bottom_sheet, null);
        d.setContentView(v);
        
        RecyclerView rv = v.findViewById(R.id.extensionsRecyclerView);
        rv.setLayoutManager(new LinearLayoutManager(this));
        final ExtensionsAdapter[] adapterHolder = new ExtensionsAdapter[1];
        adapterHolder[0] = new ExtensionsAdapter(extensions, new ExtensionsAdapter.ExtensionListener() {
            @Override public void onExtensionToggle(Extension e, boolean en) { 
                e.enabled = en;
                saveExtensions();
                showSnackbar(e.name + (en ? " enabled" : " disabled")); 
            }
            @Override public void onExtensionSettings(Extension e) {}
        });
        rv.setAdapter(adapterHolder[0]);
        
        v.findViewById(R.id.emptyExtensions).setVisibility(extensions.isEmpty() ? View.VISIBLE : View.GONE);
        
        v.findViewById(R.id.btnClose).setOnClickListener(x -> d.dismiss());
        
        v.findViewById(R.id.btnAddFromStore).setOnClickListener(x -> {
            showSnackbar("Opening Chrome Web Store...");
            createNewTab("https://chrome.google.com/webstore/category/extensions", false);
            d.dismiss();
        });
        
        v.findViewById(R.id.btnAddFromZip).setOnClickListener(x -> {
            openFilePicker("application/zip");
            d.dismiss();
        });
        
        v.findViewById(R.id.btnAddUserScript).setOnClickListener(x -> {
            openFilePicker("text/javascript");
            d.dismiss();
        });

        d.show();
    }
    
    private void openFilePicker(String mimeType) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(mimeType);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Select File"), FILE_PICK_REQUEST);
        } catch (Exception e) {
            showSnackbar("No file manager available");
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_SCREEN_CAPTURE && browserAPI != null) {
            browserAPI.handleScreenCaptureResult(resultCode, data);
            return;
        }
        
        if (requestCode == FILE_PICK_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                handleExtensionFile(uri);
            }
        }
    }
    
    private void handleExtensionFile(Uri uri) {
        String fileName = uri.getLastPathSegment();
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (fileName != null && fileName.endsWith(".zip")) {
                // Handle ZIP extension
                installZipExtension(is);
            } else if (fileName != null && (fileName.endsWith(".js") || fileName.endsWith(".user.js"))) {
                // Handle UserScript
                installUserScript(is, fileName);
            }
        } catch (Exception e) {
            showSnackbar("Failed to load extension: " + e.getMessage());
        }
    }
    
    private void installZipExtension(InputStream is) {
        try {
            File extDir = new File(getFilesDir(), "extensions/" + UUID.randomUUID().toString());
            extDir.mkdirs();
            
            ZipInputStream zis = new ZipInputStream(is);
            ZipEntry entry;
            String manifest = null;
            StringBuilder scriptBuilder = new StringBuilder();
            
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("manifest.json")) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zis));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    manifest = sb.toString();
                } else if (entry.getName().endsWith(".js")) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zis));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        scriptBuilder.append(line).append("\n");
                    }
                }
                zis.closeEntry();
            }
            zis.close();
            
            if (manifest != null) {
                JSONObject json = new JSONObject(manifest);
                String name = json.optString("name", "Extension");
                String version = json.optString("version", "1.0");
                String desc = json.optString("description", "");
                
                Extension ext = new Extension(
                    UUID.randomUUID().toString(),
                    name, desc, version, "zip",
                    true, scriptBuilder.toString()
                );
                extensions.add(ext);
                saveExtensions();
                showSnackbar(name + " installed successfully");
            }
        } catch (Exception e) {
            showSnackbar("Failed to install extension");
        }
    }
    
    private void installUserScript(InputStream is, String fileName) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder script = new StringBuilder();
            String name = fileName.replace(".user.js", "").replace(".js", "");
            String line;
            
            while ((line = reader.readLine()) != null) {
                script.append(line).append("\n");
            }
            reader.close();
            
            Extension ext = new Extension(
                UUID.randomUUID().toString(),
                name, "UserScript", "1.0", "userscript",
                true, script.toString()
            );
            extensions.add(ext);
            saveExtensions();
            showSnackbar(name + " installed successfully");
        } catch (Exception e) {
            showSnackbar("Failed to install userscript");
        }
    }
    
    private void saveExtensions() {
        try {
            JSONArray a = new JSONArray();
            for (Extension e : extensions) {
                JSONObject o = new JSONObject();
                o.put("id", e.id);
                o.put("name", e.name);
                o.put("description", e.description);
                o.put("version", e.version);
                o.put("source", e.source);
                o.put("enabled", e.enabled);
                o.put("script", e.script);
                a.put(o);
            }
            preferences.edit().putString(KEY_EXTENSIONS, a.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }
    
    private void loadExtensions() {
        try {
            JSONArray a = new JSONArray(preferences.getString(KEY_EXTENSIONS, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Extension e = new Extension(
                    o.getString("id"),
                    o.getString("name"),
                    o.getString("description"),
                    o.getString("version"),
                    o.getString("source"),
                    o.getBoolean("enabled"),
                    o.optString("script", "")
                );
                extensions.add(e);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
    
    // DevTools Server
    private void startDevToolsServer() {
        devToolsServer = new DevToolsServer();
        devToolsServer.start();
    }

    // Download
    private void downloadFile(String url, String ua, String cd, String mt) {
        try {
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            r.setMimeType(mt);
            r.addRequestHeader("User-Agent", ua);
            r.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
            String fn = URLUtil.guessFileName(url, cd, mt);
            r.setTitle(fn);
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fn);
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(r);
            showSnackbar("Download started: " + fn);
        } catch (Exception e) {
            showSnackbar("Download failed");
        }
    }

    // Share
    private void sharePage() {
        TabInfo t = getCurrentTab();
        if (t != null) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, t.title);
            i.putExtra(Intent.EXTRA_TEXT, t.url);
            startActivity(Intent.createChooser(i, "Share"));
        }
    }

    // Permissions
    private boolean checkPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? Environment.isExternalStorageManager() :
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onBackPressed() {
        TabInfo t = getCurrentTab();
        if (t != null && t.webView != null && t.webView.canGoBack()) {
            t.webView.goBack();
        } else if (tabs.size() > 1) {
            closeTab(currentTabIndex);
        } else {
            new MaterialAlertDialogBuilder(this)
                .setTitle("Exit?")
                .setPositiveButton("Exit", (d, w) -> super.onBackPressed())
                .setNegativeButton("Cancel", null)
                .show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        TabInfo t = getCurrentTab();
        if (t != null && t.webView != null) t.webView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        TabInfo t = getCurrentTab();
        if (t != null && t.webView != null) t.webView.onPause();
        saveTabs();
    }

    @Override
    protected void onStop() {
        super.onStop();
        saveTabs();
        saveSettings();
    }

    @Override
    protected void onDestroy() {
        for (TabInfo t : tabs) if (t.webView != null) t.webView.destroy();
        if (devToolsServer != null) devToolsServer.stop();
        super.onDestroy();
    }
    
    // Simple DevTools Server for PC debugging
    private class DevToolsServer {
        private ServerSocket serverSocket;
        private boolean running = false;
        
        void start() {
            new Thread(() -> {
                try {
                    serverSocket = new ServerSocket(9222);
                    running = true;
                    Log.d("DevTools", "DevTools server started on port 9222");
                    
                    while (running) {
                        try {
                            Socket client = serverSocket.accept();
                            // Handle connection
                        } catch (Exception e) {
                            if (running) e.printStackTrace();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
        
        void stop() {
            running = false;
            try {
                if (serverSocket != null) serverSocket.close();
            } catch (Exception e) {}
        }
    }
}
