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

@CapacitorPlugin(name = "MediaGallery", permissions = {
    @Permission(alias = "media", strings = { Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO }),
    @Permission(alias = "selected", strings = { Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED }),
    @Permission(alias = "legacy", strings = { Manifest.permission.READ_EXTERNAL_STORAGE })
})
public class MediaGalleryPlugin extends Plugin {
    private ContentObserver observer;

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
        JSArray result = new JSArray();
        try {
            int total = count(MediaStore.Images.Media.EXTERNAL_CONTENT_URI) + count(MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
            int imageCount = count(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            if (offset < imageCount) queryCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image", offset, limit, result);
            int videoOffset = Math.max(0, offset - imageCount);
            int remaining = limit - result.length();
            if (remaining > 0) queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video", videoOffset, remaining, result);
            call.resolve(new JSObject().put("items", result).put("total", total).put("offset", offset).put("hasMore", offset + result.length() < total));
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
            Uri uri = Uri.parse(call.getString("uri", ""));
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
        if (Build.VERSION.SDK_INT >= 34) return getPermissionState("media") == com.getcapacitor.PermissionState.GRANTED || getPermissionState("selected") == com.getcapacitor.PermissionState.GRANTED;
        if (Build.VERSION.SDK_INT >= 33) return getPermissionState("media") == com.getcapacitor.PermissionState.GRANTED;
        return getPermissionState("legacy") == com.getcapacitor.PermissionState.GRANTED;
    }

    private int count(Uri collection) {
        try (Cursor c = getContext().getContentResolver().query(collection, new String[] { MediaStore.MediaColumns._ID }, null, null, null)) {
            return c == null ? 0 : c.getCount();
        }
    }

    private void queryCollection(Uri collection, String kind, int offset, int limit, JSArray output) {
        String[] projection = { MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.MIME_TYPE };
        String order = MediaStore.MediaColumns.DATE_ADDED + " DESC LIMIT " + limit + " OFFSET " + offset;
        try (Cursor cursor = getContext().getContentResolver().query(collection, projection, null, null, order)) {
            if (cursor == null) return;
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            int dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);
            int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE);
            int mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                Uri itemUri = ContentUris.withAppendedId(collection, id);
                JSObject item = new JSObject();
                item.put("id", kind + "-" + id);
                item.put("name", cursor.getString(nameCol));
                item.put("date", cursor.getLong(dateCol) * 1000L);
                item.put("size", cursor.getLong(sizeCol));
                item.put("mime", cursor.getString(mimeCol));
                item.put("kind", kind);
                item.put("uri", itemUri.toString());
                item.put("thumbnail", makeThumbnail(itemUri));
                output.put(item);
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