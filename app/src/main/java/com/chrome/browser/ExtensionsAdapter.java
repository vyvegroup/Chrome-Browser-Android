package com.chrome.browser;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ExtensionsAdapter extends RecyclerView.Adapter<ExtensionsAdapter.ExtensionViewHolder> {
    
    private List<MainActivity.Extension> extensions;
    private ExtensionListener listener;
    
    public interface ExtensionListener {
        void onExtensionToggle(MainActivity.Extension extension, boolean enabled);
        void onExtensionSettings(MainActivity.Extension extension);
    }
    
    public ExtensionsAdapter(List<MainActivity.Extension> extensions, ExtensionListener listener) {
        this.extensions = extensions;
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public ExtensionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_extension, parent, false);
        return new ExtensionViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ExtensionViewHolder holder, int position) {
        MainActivity.Extension extension = extensions.get(position);
        
        holder.nameText.setText(extension.name);
        holder.descText.setText(extension.description);
        holder.iconImage.setImageResource(extension.iconRes);
        holder.enableSwitch.setChecked(extension.enabled);
        
        holder.enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) {
                listener.onExtensionToggle(extension, isChecked);
            }
        });
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onExtensionSettings(extension);
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return extensions.size();
    }
    
    static class ExtensionViewHolder extends RecyclerView.ViewHolder {
        ImageView iconImage;
        TextView nameText;
        TextView descText;
        Switch enableSwitch;
        
        public ExtensionViewHolder(@NonNull View itemView) {
            super(itemView);
            iconImage = itemView.findViewById(R.id.extensionIcon);
            nameText = itemView.findViewById(R.id.extensionName);
            descText = itemView.findViewById(R.id.extensionDesc);
            enableSwitch = itemView.findViewById(R.id.extensionSwitch);
        }
    }
}
