package com.chrome.browser;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {
    
    private List<MainActivity.HistoryItem> history;
    private HistoryListener listener;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
    
    public interface HistoryListener {
        void onHistoryClick(MainActivity.HistoryItem item);
        void onHistoryDelete(MainActivity.HistoryItem item, int position);
    }
    
    public HistoryAdapter(List<MainActivity.HistoryItem> history, HistoryListener listener) {
        this.history = history;
        this.listener = listener;
    }
    
    public void updateHistory(List<MainActivity.HistoryItem> history) {
        this.history = history;
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_history, parent, false);
        return new HistoryViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        MainActivity.HistoryItem item = history.get(position);
        
        holder.titleText.setText(item.title);
        holder.urlText.setText(item.url);
        holder.dateText.setText(dateFormat.format(new Date(item.timestamp)));
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onHistoryClick(item);
            }
        });
        
        holder.deleteBtn.setOnClickListener(v -> {
            if (listener != null) {
                listener.onHistoryDelete(item, holder.getAdapterPosition());
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return history.size();
    }
    
    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;
        TextView urlText;
        TextView dateText;
        ImageButton deleteBtn;
        
        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.historyTitle);
            urlText = itemView.findViewById(R.id.historyUrl);
            dateText = itemView.findViewById(R.id.historyDate);
            deleteBtn = itemView.findViewById(R.id.historyDelete);
        }
    }
}
