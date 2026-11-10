package com.milk.backupgallery;

import android.Manifest;
import android.content.ContentUris;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.database.Cursor;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Size;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

@CapacitorPlugin(name = "MediaGallery", permissions = {
    @Permission(alias = "media", strings = { Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO }),
    @Permission(alias = "selected", strings = { Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED }),
    @Permission(alias = "legacy", strings = { Manifest.permission.READ_EXTERNAL_STORAGE })
})
public class MediaGalleryPlugin extends Plugin {
    private ContentObserver observer;

    private static class MediaEntry {
        long id, date, size;
        String name, mime, kind;
        Uri uri;
    }

    @Override public void load() {
        observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange, Uri uri) { notifyListeners("mediaChanged", new JSObject()); }
        };
        try {
            getContext().getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer);
            getContext().getContentResolver().registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer);
        } catch (Exception ignored) { }
    }

    @PluginMethod public void requestAccess(PluginCall call) {
        String alias = Build.VERSION.SDK_INT >= 33 ? "media" : "legacy";
        if (Build.VERSION.SDK_INT >= 34) requestPermissionForAliases(new String[] { "media", "selected" }, call, "permissionResult");
        else requestPermissionForAlias(alias, call, "permissionResult");
    }

    @PluginMethod public void permissionResult(PluginCall call) {
        call.resolve(new JSObject().put("granted", hasMediaPermission()));
    }

    @PluginMethod public void getMedia(PluginCall call) {
        if (!hasMediaPermission()) { call.reject("Photo and video access has not been granted.", "PERMISSION_REQUIRED"); return; }
        int offset = Math.max(0, call.getInt("offset", 0));
        int limit = Math.min(60, Math.max(1, call.getInt("limit", 40)));
        String query = call.getString("query", "").trim();
        String filter = call.getString("filter", "all");
        try {
            ArrayList<MediaEntry> all = new ArrayList<>();
            if (canReadImages() && ("all".equals(filter) || "image".equals(filter))) collect(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image", query, all);
            if (canReadVideos() && ("all".equals(filter) || "video".equals(filter))) collect(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video", query, all);
            Collections.sort(all, (a, b) -> Long.compare(b.date, a.date));
            int total = all.size();
            JSArray result = new JSArray();
            int end = Math.min(total, offset + limit);
            for (int i = offset; i < end; i++) {
                MediaEntry entry = all.get(i);
                JSObject item = new JSObject();
                item.put("id", entry.kind + "-" + entry.id);
                item.put("name", entry.name);
                item.put("date", entry.date);
                item.put("size", entry.size);
                item.put("mime", entry.mime);
                item.put("kind", entry.kind);
                item.put("uri", entry.uri.toString());
                item.put("thumbnail", makeThumbnail(entry.uri));
                result.put(item);
            }
            call.resolve(new JSObject().put("items", result).put("total", total).put("offset", offset).put("hasMore", end < total));
        } catch (Exception e) { call.reject("Could not read your media library: " + e.getMessage()); }
    }

    @PluginMethod public void openAppSettings(PluginCall call) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            call.resolve();
        } catch (Exception e) { call.reject("Could not open Android app settings.", e); }
    }

    @PluginMethod public void shareMedia(PluginCall call) {
        try {
            String raw = call.getString("uri");
            if (raw == null || raw.isEmpty()) { call.reject("No media selected."); return; }
            Uri uri = Uri.parse(raw);
            String mime = call.getString("mime", "*/*");
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(mime);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(Intent.createChooser(intent, "Share with"));
            call.resolve();
        } catch (Exception e) { call.reject("Could not share this media.", e); }
    }

    private boolean hasMediaPermission() {
        if (Build.VERSION.SDK_INT >= 34) return canReadImages() || canReadVideos() || getContext().checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (Build.VERSION.SDK_INT >= 33) return canReadImages() || canReadVideos();
        return getContext().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private boolean canReadImages() {
        if (Build.VERSION.SDK_INT >= 34 && getContext().checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == android.content.pm.PackageManager.PERMISSION_GRANTED) return true;
        if (Build.VERSION.SDK_INT >= 33) return getContext().checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        return getContext().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private boolean canReadVideos() {
        if (Build.VERSION.SDK_INT >= 34 && getContext().checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == android.content.pm.PackageManager.PERMISSION_GRANTED) return true;
        if (Build.VERSION.SDK_INT >= 33) return getContext().checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        return getContext().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void collect(Uri collection, String kind, String query, ArrayList<MediaEntry> all) {
        String[] projection = { MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.MIME_TYPE };
        String selection = query.isEmpty() ? null : MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?";
        String[] selectionArgs = query.isEmpty() ? null : new String[] { "%" + query.replace("%", "\\%").replace("_", "\\_") + "%" };
        try (Cursor cursor = getContext().getContentResolver().query(collection, projection, selection, selectionArgs, null)) {
            if (cursor == null) return;
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            int dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);
            int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE);
            int mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE);
            while (cursor.moveToNext()) {
                MediaEntry entry = new MediaEntry();
                entry.id = cursor.getLong(idCol);
                entry.name = cursor.getString(nameCol);
                entry.date = cursor.getLong(dateCol) * 1000L;
                entry.size = cursor.getLong(sizeCol);
                entry.mime = cursor.getString(mimeCol);
                entry.kind = kind;
                entry.uri = ContentUris.withAppendedId(collection, entry.id);
                all.add(entry);
            }
        }
    }

    private String makeThumbnail(Uri uri) {
        if (Build.VERSION.SDK_INT < 29) return "";
        try {
            Bitmap bitmap = getContext().getContentResolver().loadThumbnail(uri, new Size(320, 320), null);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 72, bytes);
            bitmap.recycle();
            return "data:image/jpeg;base64," + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP);
        } catch (Exception ignored) { return ""; }
    }
}