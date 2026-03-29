package com.chrome.browser;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DevToolsActivity extends AppCompatActivity {

    private TextView tvTargetUrl;
    private LinearLayout consoleView, elementsView, networkView, applicationView, sourcesView;
    private LinearLayout consoleOutput;
    private EditText consoleInput;
    private ScrollView consoleScrollView;
    private RecyclerView networkRecyclerView;
    private TextView tvElements, tvSources;
    private TextView tvLocalStorageCount, tvSessionStorageCount, tvCookiesCount;
    
    private String targetUrl;
    private List<NetworkRequest> networkRequests = new ArrayList<>();
    private NetworkAdapter networkAdapter;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devtools);
        
        targetUrl = getIntent().getStringExtra("url");
        
        initViews();
        setupListeners();
        loadDevToolsData();
    }
    
    private void initViews() {
        tvTargetUrl = findViewById(R.id.tvTargetUrl);
        consoleView = findViewById(R.id.consoleView);
        elementsView = findViewById(R.id.elementsView);
        networkView = findViewById(R.id.networkView);
        applicationView = findViewById(R.id.applicationView);
        sourcesView = findViewById(R.id.sourcesView);
        consoleInput = findViewById(R.id.consoleInput);
        tvElements = findViewById(R.id.tvElements);
        tvSources = findViewById(R.id.tvSources);
        tvLocalStorageCount = findViewById(R.id.tvLocalStorageCount);
        tvSessionStorageCount = findViewById(R.id.tvSessionStorageCount);
        tvCookiesCount = findViewById(R.id.tvCookiesCount);
        
        // Create console output and scroll view programmatically
        consoleScrollView = findViewById(R.id.consoleScrollView);
        consoleOutput = findViewById(R.id.consoleOutput);
        
        networkRecyclerView = findViewById(R.id.networkRecyclerView);
        networkRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        networkAdapter = new NetworkAdapter(networkRequests);
        networkRecyclerView.setAdapter(networkAdapter);
        
        if (targetUrl != null) {
            tvTargetUrl.setText(targetUrl);
        }
    }
    
    private void setupListeners() {
        findViewById(R.id.btnBackDevTools).setOnClickListener(v -> finish());
        
        findViewById(R.id.btnElements).setOnClickListener(v -> showTab("elements"));
        findViewById(R.id.btnConsole).setOnClickListener(v -> showTab("console"));
        findViewById(R.id.btnNetwork).setOnClickListener(v -> showTab("network"));
        findViewById(R.id.btnSources).setOnClickListener(v -> showTab("sources"));
        findViewById(R.id.btnApplication).setOnClickListener(v -> showTab("application"));
        
        findViewById(R.id.btnExecute).setOnClickListener(v -> executeCommand());
        
        consoleInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                executeCommand();
                return true;
            }
            return false;
        });
        
        findViewById(R.id.itemLocalStorage).setOnClickListener(v -> showStorageInfo("local"));
        findViewById(R.id.itemSessionStorage).setOnClickListener(v -> showStorageInfo("session"));
        findViewById(R.id.itemCookies).setOnClickListener(v -> showStorageInfo("cookies"));
    }
    
    private void loadDevToolsData() {
        loadElementsView();
        loadNetworkRequests();
        loadStorageInfo();
        loadSources();
        
        // Add welcome message to console
        addConsoleEntry("DevTools ready", "#4EC9B0");
        addConsoleEntry("Connected to: " + (targetUrl != null ? targetUrl : "unknown"), "#9CDCFE");
    }
    
    private void showTab(String tab) {
        consoleView.setVisibility("console".equals(tab) ? View.VISIBLE : View.GONE);
        elementsView.setVisibility("elements".equals(tab) ? View.VISIBLE : View.GONE);
        networkView.setVisibility("network".equals(tab) ? View.VISIBLE : View.GONE);
        applicationView.setVisibility("application".equals(tab) ? View.VISIBLE : View.GONE);
        sourcesView.setVisibility("sources".equals(tab) ? View.VISIBLE : View.GONE);
    }
    
    private void executeCommand() {
        String command = consoleInput.getText().toString().trim();
        if (command.isEmpty()) return;
        
        addConsoleEntry("> " + command, "#569CD6");
        consoleInput.setText("");
        
        // Simulate execution result
        try {
            if (command.startsWith("console.log")) {
                String result = command.substring("console.log(".length(), command.length() - 1);
                addConsoleEntry(result.replace("\"", ""), "#D4D4D4");
            } else if (command.equals("window.location")) {
                addConsoleEntry("\"" + targetUrl + "\"", "#CE9178");
            } else if (command.equals("document.title")) {
                addConsoleEntry("\"Page Title\"", "#CE9178");
            } else if (command.startsWith("var") || command.startsWith("let") || command.startsWith("const")) {
                addConsoleEntry("undefined", "#569CD6");
            } else {
                addConsoleEntry("undefined", "#569CD6");
            }
        } catch (Exception e) {
            addConsoleEntry("Error: " + e.getMessage(), "#F14C4C");
        }
        
        if (consoleScrollView != null) {
            consoleScrollView.post(() -> consoleScrollView.fullScroll(View.FOCUS_DOWN));
        }
    }
    
    private void loadElementsView() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"en\">\n");
        sb.append("  <head>\n");
        sb.append("    <meta charset=\"UTF-8\">\n");
        sb.append("    <meta name=\"viewport\" content=\"width=device-width\">\n");
        sb.append("    <title>Page</title>\n");
        sb.append("  </head>\n");
        sb.append("  <body>\n");
        sb.append("    <div id=\"app\">\n");
        sb.append("      <h1>Hello World</h1>\n");
        sb.append("      <p>Welcome to the page</p>\n");
        sb.append("    </div>\n");
        sb.append("  </body>\n");
        sb.append("</html>");
        
        tvElements.setText(sb.toString());
    }
    
    private void loadNetworkRequests() {
        networkRequests.clear();
        networkRequests.add(new NetworkRequest("index.html", "200", "document", "12.5 KB", "45ms"));
        networkRequests.add(new NetworkRequest("style.css", "200", "stylesheet", "4.2 KB", "12ms"));
        networkRequests.add(new NetworkRequest("script.js", "200", "script", "28.3 KB", "89ms"));
        networkRequests.add(new NetworkRequest("logo.png", "200", "image", "15.7 KB", "34ms"));
        networkRequests.add(new NetworkRequest("data.json", "200", "xhr", "2.1 KB", "156ms"));
        networkAdapter.notifyDataSetChanged();
    }
    
    private void loadStorageInfo() {
        tvLocalStorageCount.setText("3 items");
        tvSessionStorageCount.setText("1 item");
        tvCookiesCount.setText("12 items");
    }
    
    private void loadSources() {
        StringBuilder sb = new StringBuilder();
        sb.append("// Main Application Scripts\n\n");
        sb.append("// index.js\n");
        sb.append("const app = {\n");
        sb.append("  init() {\n");
        sb.append("    console.log('App initialized');\n");
        sb.append("    this.loadComponents();\n");
        sb.append("  },\n");
        sb.append("  \n");
        sb.append("  loadComponents() {\n");
        sb.append("    // Load UI components\n");
        sb.append("  }\n");
        sb.append("};\n\n");
        sb.append("app.init();\n");
        
        tvSources.setText(sb.toString());
    }
    
    private void addConsoleEntry(String text, String color) {
        if (consoleOutput == null) return;
        
        TextView entry = new TextView(this);
        entry.setText(text);
        entry.setTextColor(Color.parseColor(color));
        entry.setTextSize(13);
        entry.setTypeface(android.graphics.Typeface.MONOSPACE);
        entry.setPadding(16, 8, 16, 8);
        consoleOutput.addView(entry);
    }
    
    private void showStorageInfo(String type) {
        showTab("console");
        
        String title = "";
        String content = "";
        
        switch (type) {
            case "local":
                title = "Local Storage Contents:";
                content = "{\n  \"userId\": \"12345\",\n  \"theme\": \"dark\",\n  \"lastVisit\": \"" + new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()) + "\"\n}";
                break;
            case "session":
                title = "Session Storage Contents:";
                content = "{\n  \"sessionId\": \"abc-xyz-123\"\n}";
                break;
            case "cookies":
                title = "Cookies:";
                content = "_ga=GA1.2.123456789.1234567890\n_fbp=fb.1.1234567890.123456789\nsession_token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";
                break;
        }
        
        addConsoleEntry(title, "#9CDCFE");
        addConsoleEntry(content, "#CE9178");
        if (consoleScrollView != null) {
            consoleScrollView.post(() -> consoleScrollView.fullScroll(View.FOCUS_DOWN));
        }
    }
    
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
    
    // Network Request model
    public static class NetworkRequest {
        String name;
        String status;
        String type;
        String size;
        String time;
        
        NetworkRequest(String name, String status, String type, String size, String time) {
            this.name = name;
            this.status = status;
            this.type = type;
            this.size = size;
            this.time = time;
        }
    }
    
    // Network Adapter
    public static class NetworkAdapter extends RecyclerView.Adapter<NetworkAdapter.ViewHolder> {
        
        private List<NetworkRequest> requests;
        
        NetworkAdapter(List<NetworkRequest> requests) {
            this.requests = requests;
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_network_request, parent, false);
            return new ViewHolder(v);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            NetworkRequest req = requests.get(position);
            holder.tvName.setText(req.name);
            holder.tvStatus.setText(req.status);
            holder.tvType.setText(req.type);
            holder.tvSize.setText(req.size);
            holder.tvTime.setText(req.time);
            
            int statusColor = "200".equals(req.status) ? 0xFF4EC9B0 : 0xFFF14C4C;
            holder.tvStatus.setTextColor(statusColor);
        }
        
        @Override
        public int getItemCount() {
            return requests.size();
        }
        
        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvStatus, tvType, tvSize, tvTime;
            
            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tvName);
                tvStatus = v.findViewById(R.id.tvStatus);
                tvType = v.findViewById(R.id.tvType);
                tvSize = v.findViewById(R.id.tvSize);
                tvTime = v.findViewById(R.id.tvTime);
            }
        }
    }
}
