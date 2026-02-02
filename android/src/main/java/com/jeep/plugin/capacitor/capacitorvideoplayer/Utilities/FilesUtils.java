package com.jeep.plugin.capacitor.capacitorvideoplayer.Utilities;

import android.content.Context;
import android.util.Log;
import java.io.File;

public class FilesUtils {

    private static final String TAG = "FilesUtils";
    private final Context context;

    public FilesUtils(Context context) {
        this.context = context;
    }

    public String getFilePath(String url) {
        if (url.startsWith("file:///")) {
            return url;
        }
        String path = null;

        // Handle Capacitor file URLs (created by Capacitor.convertFileSrc())
        // These URLs are in the format: http://localhost/_capacitor_file_/path/to/file
        // We need to extract the actual file path for ExoPlayer to use
        if (url.contains("/_capacitor_file_/")) {
            String capacitorPrefix = "/_capacitor_file_";
            int prefixIndex = url.indexOf(capacitorPrefix);
            if (prefixIndex != -1) {
                // Extract the actual file path after the prefix
                String filePath = url.substring(prefixIndex + capacitorPrefix.length());
                // Decode URL encoding if present
                try {
                    filePath = java.net.URLDecoder.decode(filePath, "UTF-8");
                } catch (java.io.UnsupportedEncodingException e) {
                    // If decoding fails, use the original path
                    Log.e(TAG, "Failed to decode Capacitor file URL: " + url, e);
                }
                // Return as file:// URI for ExoPlayer
                path = "file://" + filePath;
            }
        } else if (url.length() >= 4 && url.substring(0, 4).equals("http")) {
            // Regular HTTP/HTTPS URLs
            path = url;
        } else {
            if (url.length() >= 11 && url.substring(0, 11).equals("application")) {
                String filesDir = context.getFilesDir() + "/";
                path = filesDir + url.substring(url.lastIndexOf("files/") + 6);
                File file = new File(path);
                if (!file.exists()) {
                    path = null;
                }
            } else if (url.contains("assets")) {
                path = "file:///android_asset/" + url;
            } else {
                path = null;
            }
        }
        return path;
    }
}
