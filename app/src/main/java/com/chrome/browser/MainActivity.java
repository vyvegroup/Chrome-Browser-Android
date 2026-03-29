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
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String PREFS_NAME = "ChromeBrowserPrefs";
    private static final String KEY_BOOKMARKS = "bookmarks";
    private static final String KEY_HISTORY = "history";

    // UI
    private FrameLayout webViewContainer;
    private ProgressBar progressBar;
    private TextInputEditText urlBar;
    private ImageView securityIcon;
    private ImageButton btnBack, btnForward, btnRefresh, btnHome, btnMenu;
    private Chip tabCountChip;
    private FloatingActionButton fabNewTab;
    private LinearLayout bottomBar;
    private FrameLayout fullscreenContainer;

    // Tabs
    private List<TabInfo> tabs = new ArrayList<>();
    private int currentTabIndex = 0;

    // Settings
    private SharedPreferences preferences;
    private boolean isJavaScriptEnabled = true;
    private boolean isDesktopMode = false;
    private boolean isIncognitoMode = false;

    // Bookmarks & History
    private List<Bookmark> bookmarks = new ArrayList<>();
    private List<HistoryItem> history = new ArrayList<>();

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        loadBookmarks();
        loadHistory();

        initViews();
        createNewTab("https://www.google.com", false);
        setupListeners();

        if (getIntent() != null && getIntent().getData() != null) {
            loadUrl(getIntent().getData().toString());
        }
    }

    private void initViews() {
        webViewContainer = findViewById(R.id.webViewContainer);
        progressBar = findViewById(R.id.progressBar);
        urlBar = findViewById(R.id.urlBar);
        securityIcon = findViewById(R.id.securityIcon);
        btnBack = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnHome = findViewById(R.id.btnHome);
        btnMenu = findViewById(R.id.btnMenu);
        tabCountChip = findViewById(R.id.tabCountChip);
        fabNewTab = findViewById(R.id.fabNewTab);
        bottomBar = findViewById(R.id.bottomBar);
        fullscreenContainer = findViewById(R.id.fullscreenContainer);
    }

    private void setupListeners() {
        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH) {
                String url = urlBar.getText().toString().trim();
                if (!url.isEmpty()) {
                    loadUrl(formatUrl(url));
                    hideKeyboard();
                }
                return true;
            }
            return false;
        });

        btnBack.setOnClickListener(v -> goBack());
        btnForward.setOnClickListener(v -> goForward());
        btnRefresh.setOnClickListener(v -> refresh());
        btnHome.setOnClickListener(v -> loadUrl("https://www.google.com"));
        btnMenu.setOnClickListener(v -> showMenu());
        tabCountChip.setOnClickListener(v -> showTabsGrid());
        fabNewTab.setOnClickListener(v -> createNewTab("https://www.google.com", isIncognitoMode));

        registerForContextMenu(webViewContainer);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
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
        if (url.startsWith("https://")) {
            securityIcon.setImageResource(R.drawable.ic_lock);
        } else {
            securityIcon.setImageResource(R.drawable.ic_lock_open);
        }
    }

    private void goBack() {
        TabInfo tab = getCurrentTab();
        if (tab != null && tab.webView != null && tab.webView.canGoBack()) {
            tab.webView.goBack();
        } else {
            Snackbar.make(bottomBar, "No previous page", Snackbar.LENGTH_SHORT).show();
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
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView(boolean incognito) {
        WebView webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));

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
                progressBar.setVisibility(View.VISIBLE);
                urlBar.setText(url);
                updateSecurityIcon(url);
                TabInfo t = findTab(view);
                if (t != null) t.url = url;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                updateNavigationButtons();
                String title = view.getTitle() != null ? view.getTitle() : "Page";
                TabInfo t = findTab(view);
                if (t != null) { t.title = title; t.url = url; }
                if (!isIncognitoMode && t != null && !t.isIncognito) addToHistory(title, url);
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
            public void onProgressChanged(WebView view, int p) { progressBar.setProgress(p); }
            @Override
            public void onReceivedTitle(WebView view, String title) {
                TabInfo t = findTab(view);
                if (t != null) t.title = title != null ? title : "Page";
            }
            @Override
            public void onReceivedIcon(WebView view, Bitmap icon) {
                TabInfo t = findTab(view);
                if (t != null) t.favicon = icon;
            }
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                webViewContainer.setVisibility(View.GONE);
                fullscreenContainer.setVisibility(View.VISIBLE);
                fullscreenContainer.addView(view);
            }
            @Override
            public void onHideCustomView() {
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

        return webView;
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
            v.findViewById(R.id.action_copy_image_url).setOnClickListener(x -> { copyToClipboard(extra); Snackbar.make(bottomBar, "URL copied", Snackbar.LENGTH_SHORT).show(); d.dismiss(); });
            v.findViewById(R.id.action_search_image).setOnClickListener(x -> { createNewTab("https://www.google.com/searchbyimage?image_url=" + Uri.encode(extra), isIncognitoMode); d.dismiss(); });
        } else if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
            View v = LayoutInflater.from(this).inflate(R.layout.context_menu_link, null);
            d.setContentView(v);
            v.findViewById(R.id.action_open_new_tab).setOnClickListener(x -> { createNewTab(extra, isIncognitoMode); d.dismiss(); });
            v.findViewById(R.id.action_open_incognito).setOnClickListener(x -> { createNewTab(extra, true); d.dismiss(); });
            v.findViewById(R.id.action_copy_link).setOnClickListener(x -> { copyToClipboard(extra); Snackbar.make(bottomBar, "Link copied", Snackbar.LENGTH_SHORT).show(); d.dismiss(); });
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
        tabCountChip.setText(String.valueOf(tabs.size()));
    }

    private void updateIncognitoUI() {
        int bgColor = isIncognitoMode ? 0xFF343A40 : 0xFFFFFFFF;
        findViewById(R.id.appBar).setBackgroundColor(bgColor);
        bottomBar.setBackgroundColor(bgColor);
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
            webViewContainer.removeAllViews();
            webViewContainer.addView(t.webView);
            urlBar.setText(t.url);
            isIncognitoMode = t.isIncognito;
            updateIncognitoUI();
            updateNavigationButtons();
            updateSecurityIcon(t.url);
        }
    }

    private void closeTab(int pos) {
        if (pos >= 0 && pos < tabs.size()) {
            tabs.get(pos).webView.destroy();
            tabs.remove(pos);
            if (tabs.isEmpty()) { finish(); return; }
            if (currentTabIndex >= tabs.size()) currentTabIndex = tabs.size() - 1;
            switchToTab(currentTabIndex);
            updateTabCount();
        }
    }

    // Settings
    private void showSettings() {
        BottomSheetDialog d = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.settings_bottom_sheet, null);
        d.setContentView(v);

        com.google.android.material.switchmaterial.SwitchMaterial js = v.findViewById(R.id.switchJavaScript);
        com.google.android.material.switchmaterial.SwitchMaterial dm = v.findViewById(R.id.switchDarkMode);
        com.google.android.material.switchmaterial.SwitchMaterial dk = v.findViewById(R.id.switchDesktop);

        js.setChecked(isJavaScriptEnabled);
        dk.setChecked(isDesktopMode);

        js.setOnCheckedChangeListener((b, c) -> { isJavaScriptEnabled = c; for(TabInfo t: tabs) t.webView.getSettings().setJavaScriptEnabled(c); });
        dk.setOnCheckedChangeListener((b, c) -> { isDesktopMode = c; applyDesktopMode(); });

        v.findViewById(R.id.btnClearCache).setOnClickListener(x -> { for(TabInfo t: tabs) t.webView.clearCache(true); Snackbar.make(v, "Cache cleared", Snackbar.LENGTH_SHORT).show(); });
        v.findViewById(R.id.btnClearHistory).setOnClickListener(x -> { history.clear(); preferences.edit().remove(KEY_HISTORY).apply(); Snackbar.make(v, "History cleared", Snackbar.LENGTH_SHORT).show(); });
        v.findViewById(R.id.btnClearCookies).setOnClickListener(x -> { CookieManager.getInstance().removeAllCookies(null); Snackbar.make(v, "Cookies cleared", Snackbar.LENGTH_SHORT).show(); });
        v.findViewById(R.id.btnClearAll).setOnClickListener(x -> new MaterialAlertDialogBuilder(this)
            .setTitle("Clear all data?").setMessage("This will clear cache, history, cookies, and bookmarks.")
            .setPositiveButton("Clear", (dlg, w) -> { for(TabInfo t: tabs) t.webView.clearCache(true); history.clear(); bookmarks.clear(); CookieManager.getInstance().removeAllCookies(null); preferences.edit().clear().apply(); Snackbar.make(v, "All data cleared", Snackbar.LENGTH_SHORT).show(); })
            .setNegativeButton("Cancel", null).show());

        d.show();
    }

    private void toggleDesktopMode() {
        isDesktopMode = !isDesktopMode;
        applyDesktopMode();
        Snackbar.make(bottomBar, isDesktopMode ? "Desktop mode enabled" : "Desktop mode disabled", Snackbar.LENGTH_SHORT).show();
    }

    private void applyDesktopMode() {
        for (TabInfo t : tabs) {
            WebSettings s = t.webView.getSettings();
            s.setUserAgentString(isDesktopMode ? "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36" : WebSettings.getDefaultUserAgent(this));
            t.webView.reload();
        }
    }

    // Bookmarks
    private void showBookmarks() {
        BottomSheetDialog d = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.bookmarks_bottom_sheet, null);
        d.setContentView(v);
        RecyclerView rv = v.findViewById(R.id.bookmarksRecyclerView);
        rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
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
            Snackbar.make(bottomBar, "Bookmark added", Snackbar.LENGTH_SHORT).show();
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
        rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
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
        rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        List<Extension> exts = new ArrayList<>();
        exts.add(new Extension("Ad Blocker", "Block ads and trackers", true, R.drawable.ic_shield));
        exts.add(new Extension("Dark Reader", "Force dark mode", false, R.drawable.ic_dark_mode));
        exts.add(new Extension("Video Downloader", "Download videos", false, R.drawable.ic_video));
        ExtensionsAdapter a = new ExtensionsAdapter(exts, new ExtensionsAdapter.ExtensionListener() {
            @Override public void onExtensionToggle(Extension e, boolean en) { Snackbar.make(bottomBar, e.name + (en ? " enabled" : " disabled"), Snackbar.LENGTH_SHORT).show(); }
            @Override public void onExtensionSettings(Extension e) {}
        });
        rv.setAdapter(a);
        d.show();
    }

    public static class Extension {
        String name, description; boolean enabled; int iconRes;
        Extension(String n, String d, boolean e, int i) { name=n; description=d; enabled=e; iconRes=i; }
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
            Snackbar.make(bottomBar, "Download started: " + fn, Snackbar.LENGTH_LONG).show();
        } catch (Exception e) {
            Snackbar.make(bottomBar, "Download failed", Snackbar.LENGTH_SHORT).show();
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
        if (t != null && t.webView != null && t.webView.canGoBack()) t.webView.goBack();
        else if (tabs.size() > 1) closeTab(currentTabIndex);
        else new MaterialAlertDialogBuilder(this).setTitle("Exit?").setPositiveButton("Exit", (d, w) -> super.onBackPressed()).setNegativeButton("Cancel", null).show();
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
    }

    @Override
    protected void onDestroy() {
        for (TabInfo t : tabs) if (t.webView != null) t.webView.destroy();
        super.onDestroy();
    }
}
