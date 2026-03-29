package com.chrome.browser;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TabsAdapter extends RecyclerView.Adapter<TabsAdapter.TabViewHolder> {
    
    private List<MainActivity.TabInfo> tabs;
    private int currentTabIndex;
    private TabListener listener;
    
    public interface TabListener {
        void onTabClick(int position);
        void onTabClose(int position);
        void onTabGroup(int position);
    }
    
    public TabsAdapter(List<MainActivity.TabInfo> tabs, int currentTabIndex, TabListener listener) {
        this.tabs = tabs;
        this.currentTabIndex = currentTabIndex;
        this.listener = listener;
    }
    
    public void updateTabs(List<MainActivity.TabInfo> tabs, int currentTabIndex) {
        this.tabs = tabs;
        this.currentTabIndex = currentTabIndex;
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public TabViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_tab_card, parent, false);
        return new TabViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull TabViewHolder holder, int position) {
        MainActivity.TabInfo tab = tabs.get(position);
        
        holder.titleText.setText(tab.title != null ? tab.title : "New Tab");
        holder.urlText.setText(tab.url != null ? tab.url : "");
        
        if (tab.favicon != null) {
            holder.favicon.setImageBitmap(tab.favicon);
        } else {
            holder.favicon.setImageResource(R.drawable.ic_launcher);
        }
        
        // Highlight current tab
        if (position == currentTabIndex) {
            holder.cardView.setStrokeColor(0xFF4285F4);
            holder.cardView.setStrokeWidth(2);
        } else {
            holder.cardView.setStrokeWidth(0);
        }
        
        // Incognito indicator
        if (tab.isIncognito) {
            holder.incognitoIcon.setVisibility(View.VISIBLE);
        } else {
            holder.incognitoIcon.setVisibility(View.GONE);
        }
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTabClick(holder.getAdapterPosition());
            }
        });
        
        holder.closeBtn.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTabClose(holder.getAdapterPosition());
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return tabs.size();
    }
    
    static class TabViewHolder extends RecyclerView.ViewHolder {
        com.google.android.material.card.MaterialCardView cardView;
        ImageView favicon;
        ImageView incognitoIcon;
        TextView titleText;
        TextView urlText;
        ImageButton closeBtn;
        
        public TabViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.tabCard);
            favicon = itemView.findViewById(R.id.tabFavicon);
            incognitoIcon = itemView.findViewById(R.id.incognitoIcon);
            titleText = itemView.findViewById(R.id.tabTitle);
            urlText = itemView.findViewById(R.id.tabUrl);
            closeBtn = itemView.findViewById(R.id.tabClose);
        }
    }
}
