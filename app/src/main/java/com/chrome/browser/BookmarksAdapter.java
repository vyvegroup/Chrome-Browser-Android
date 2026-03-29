package com.chrome.browser;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BookmarksAdapter extends RecyclerView.Adapter<BookmarksAdapter.BookmarkViewHolder> {
    
    private List<MainActivity.Bookmark> bookmarks;
    private BookmarkListener listener;
    
    public interface BookmarkListener {
        void onBookmarkClick(MainActivity.Bookmark bookmark);
        void onBookmarkDelete(MainActivity.Bookmark bookmark, int position);
        void onBookmarkEdit(MainActivity.Bookmark bookmark);
    }
    
    public BookmarksAdapter(List<MainActivity.Bookmark> bookmarks, BookmarkListener listener) {
        this.bookmarks = bookmarks;
        this.listener = listener;
    }
    
    public void updateBookmarks(List<MainActivity.Bookmark> bookmarks) {
        this.bookmarks = bookmarks;
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public BookmarkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_bookmark, parent, false);
        return new BookmarkViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull BookmarkViewHolder holder, int position) {
        MainActivity.Bookmark bookmark = bookmarks.get(position);
        
        holder.titleText.setText(bookmark.title);
        holder.urlText.setText(bookmark.url);
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBookmarkClick(bookmark);
            }
        });
        
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onBookmarkEdit(bookmark);
            }
            return true;
        });
        
        holder.deleteBtn.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBookmarkDelete(bookmark, holder.getAdapterPosition());
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return bookmarks.size();
    }
    
    static class BookmarkViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;
        TextView urlText;
        ImageButton deleteBtn;
        
        public BookmarkViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.bookmarkTitle);
            urlText = itemView.findViewById(R.id.bookmarkUrl);
            deleteBtn = itemView.findViewById(R.id.bookmarkDelete);
        }
    }
}
