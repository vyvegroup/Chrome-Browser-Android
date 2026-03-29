package com.chrome.browser;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

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
        
        // Set source badge
        String sourceText = extension.source;
        if ("store".equals(extension.source)) {
            sourceText = "Chrome Store";
        } else if ("zip".equals(extension.source)) {
            sourceText = "ZIP";
        } else if ("userscript".equals(extension.source)) {
            sourceText = "UserScript";
        }
        holder.sourceText.setText(sourceText);
        
        // Set icon background color based on source
        int bgColor = 0xFFE8F0FE;
        if ("userscript".equals(extension.source)) {
            bgColor = 0xFFFFF3E0;
        } else if ("zip".equals(extension.source)) {
            bgColor = 0xFFE6F4EA;
        }
        holder.iconContainer.setCardBackgroundColor(bgColor);
        
        holder.enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) {
                listener.onExtensionToggle(extension, isChecked);
            }
        });
        
        holder.settingsBtn.setOnClickListener(v -> {
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
        MaterialCardView iconContainer;
        ImageView iconImage;
        TextView nameText;
        TextView descText;
        TextView sourceText;
        MaterialSwitch enableSwitch;
        ImageButton settingsBtn;
        
        public ExtensionViewHolder(@NonNull View itemView) {
            super(itemView);
            iconContainer = itemView.findViewById(R.id.iconContainer);
            iconImage = itemView.findViewById(R.id.extensionIcon);
            nameText = itemView.findViewById(R.id.extensionName);
            descText = itemView.findViewById(R.id.extensionDescription);
            sourceText = itemView.findViewById(R.id.extensionSource);
            enableSwitch = itemView.findViewById(R.id.extensionSwitch);
            settingsBtn = itemView.findViewById(R.id.btnSettings);
        }
    }
}
