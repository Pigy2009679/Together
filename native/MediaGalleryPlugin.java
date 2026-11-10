package com.milk.backupgallery;

import android.Manifest;
import android.content.ContentUris;
import android.database.Cursor;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import java.util.ArrayList;

@CapacitorPlugin(
    name = "MediaGallery",
    permissions = {
        @Permission(alias = "media", strings = {
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        }),
        @Permission(alias = "legacy", strings = { Manifest.permission.READ_EXTERNAL_STORAGE })
    }
)
public class MediaGalleryPlugin extends Plugin {
    private ContentObserver observer;

    @Override
    public void load() {
        observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange, Uri uri) {
                notifyListeners("mediaChanged", new JSObject());
            }
        };
        try {
            getContext().getContentResolver().registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer);
            getContext().getContentResolver().registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer);
        } catch (Exception ignored) { }
    }

    @PluginMethod
    public void requestAccess(PluginCall call) {
        String alias = Build.VERSION.SDK_INT >= 33 ? "media" : "legacy";
        if (Build.VERSION.SDK_INT >= 34) {
            requestPermissionForAlias(alias, call, "permissionResult");
        } else {
            requestPermissionForAlias(alias, call, "permissionResult");
        }
    }

    @PluginMethod
    public void getMedia(PluginCall call) {
        if (!hasMediaPermission()) {
            call.reject("Photo and video access has not been granted.", "PERMISSION_REQUIRED");
            return;
        }
        JSArray result = new JSArray();
        try {
            queryCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image", result);
            queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video", result);
            call.resolve(new JSObject().put("items", result));
        } catch (Exception e) {
            call.reject("Could not read your media library: " + e.getMessage());
        }
    }

    @PluginMethod
    public void permissionResult(PluginCall call) {
        call.resolve(new JSObject().put("granted", hasMediaPermission()));
    }

    private boolean hasMediaPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return getPermissionState("media") == com.getcapacitor.PermissionState.GRANTED;
        }
        return getPermissionState("legacy") == com.getcapacitor.PermissionState.GRANTED;
    }

    private void queryCollection(Uri collection, String kind, JSArray output) {
        String[] projection = {
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE
        };
        try (Cursor cursor = getContext().getContentResolver().query(
            collection, projection, null, null,
            MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
            if (cursor == null) return;
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            int dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);
            int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE);
            int mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                JSObject item = new JSObject();
                item.put("id", kind + "-" + id);
                item.put("name", cursor.getString(nameCol));
                item.put("date", cursor.getLong(dateCol) * 1000L);
                item.put("size", cursor.getLong(sizeCol));
                item.put("mime", cursor.getString(mimeCol));
                item.put("kind", kind);
                item.put("uri", ContentUris.withAppendedId(collection, id).toString());
                output.put(item);
            }
        }
    }
}