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

        if (url.contains("/_capacitor_file_/")) {
            return resolveCapacitorFilePath(url);
        } else if (url.startsWith("http")) {
            return url;
        } else if (url.startsWith("application")) {
            return resolveApplicationPath(url);
        } else if (url.contains("assets")) {
            return "file:///android_asset/" + url;
        }

        return null;
    }

    // Resolves a _capacitor_file_ URL to a validated file:// path.
    // Capacitor's convertFileSrc() produces: http://localhost/_capacitor_file_/absolute/path
    // The extracted path is canonicalized and verified to stay within the app's
    // internal data dir or external files dir before being returned.
    private String resolveCapacitorFilePath(String url) {
        int prefixIndex = url.indexOf("/_capacitor_file_");
        if (prefixIndex == -1) {
            return null;
        }

        String filePath = url.substring(prefixIndex + "/_capacitor_file_".length());
        try {
            filePath = java.net.URLDecoder.decode(filePath, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 is always supported on Android; fall through with the raw path
            Log.e(TAG, "Failed to decode Capacitor file URL, using raw path: " + url, e);
        }

        try {
            // Validate that the resolved path is within app boundaries
            String resolvedPath = new File(filePath).getCanonicalPath();
            String dataDir = context.getDataDir().getCanonicalPath();
            File externalFilesDir = context.getExternalFilesDir(null);

            if (isPathWithinDirectory(resolvedPath, dataDir)
                    || (externalFilesDir != null && isPathWithinDirectory(resolvedPath, externalFilesDir.getCanonicalPath()))) {
                return "file://" + resolvedPath;
            }

            Log.e(TAG, "Blocked path outside app boundaries: " + url);
        } catch (java.io.IOException e) {
            Log.e(TAG, "Failed to resolve Capacitor file path: " + url, e);
        }

        return null;
    }

    // Resolves an application:// URL to a validated path within getFilesDir().
    // Extracts the relative segment after the last "files/", joins it onto
    // the app's files directory, then canonicalizes and validates before returning.
    private String resolveApplicationPath(String url) {
        int filesIndex = url.lastIndexOf("files/");
        if (filesIndex == -1) {
            return null;
        }

        try {
            String filesDir = context.getFilesDir().getCanonicalPath();
            String relativePath = url.substring(filesIndex + 6);
            String resolvedPath = new File(filesDir, relativePath).getCanonicalPath();

            if (isPathWithinDirectory(resolvedPath, filesDir) && new File(resolvedPath).exists()) {
                return resolvedPath;
            }
        } catch (java.io.IOException e) {
            Log.e(TAG, "Failed to resolve application URL path: " + url, e);
        }

        return null;
    }

    // Returns true if canonicalPath is strictly inside canonicalBaseDir.
    // The separator is appended to the base before the check so that
    // e.g. /data/data/com.app does not match /data/data/com.app.evil
    private boolean isPathWithinDirectory(String canonicalPath, String canonicalBaseDir) {
        return canonicalPath.startsWith(canonicalBaseDir + File.separator);
    }
}
