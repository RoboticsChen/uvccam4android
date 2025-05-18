package com.stars.uvccam;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class ConfigManager {
    private static final String TAG = "CameraConfig";
    private static final String CONFIG_DIR = "camera_configs";

    public static void saveConfig(Context context, int vendorId, int productId,
                                  int format, int width, int height, int fps,
                                  int exposure, int gain, int triggerPeriod, String serial1, String serial2,
                                  int isAutoExposure, int isColorMode) {
        JSONObject config = new JSONObject();
        try {
            config.put("format", format);
            config.put("width", width);
            config.put("height", height);
            config.put("fps", fps);
            config.put("exposure", exposure);
            config.put("gain", gain);
            config.put("triggerPeriod", triggerPeriod);
            config.put("serial1", serial1);
            config.put("serial2", serial2);
            config.put("isAutoExposure", isAutoExposure);
            config.put("isColorMode", isColorMode);

            String filename = getConfigFilename(vendorId, productId);
            File configDir = new File(context.getFilesDir(), CONFIG_DIR);
            if (!configDir.exists()) {
                configDir.mkdirs();
            }

            File configFile = new File(configDir, filename);
            FileOutputStream fos = new FileOutputStream(configFile);
            fos.write(config.toString().getBytes());
            fos.close();
            Log.d(TAG, "Config saved for " + filename);
        } catch (JSONException | IOException e) {
            Log.e(TAG, "Error saving config", e);
        }
    }

    public static JSONObject loadConfig(Context context, int vendorId, int productId) {
        String filename = getConfigFilename(vendorId, productId);
        File configFile = new File(new File(context.getFilesDir(), CONFIG_DIR), filename);

        if (!configFile.exists()) {
            return null;
        }

        try {
            FileInputStream fis = new FileInputStream(configFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            return new JSONObject(sb.toString());
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error loading config", e);
            return null;
        }
    }

    private static String getConfigFilename(int vendorId, int productId) {
        return String.format("cam_%04x_%04x.json", vendorId, productId);
    }
}