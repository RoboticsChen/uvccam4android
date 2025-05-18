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
    private static final String TAG = "ConfigManager";
    private static final String CONFIG_DIR = "camera_configs";

    public static void saveConfig(Context context, int vendorId, int productId,
                                  int format, int width, int height, int fps,
                                  int exposure, int gain, int triggerPeriod, String serial1, String serial2,
                                  int isAutoExposure, int isColorMode) {
        if (vendorId <= 0 || productId <= 0 || context == null) {
            Log.e(TAG, "无效的参数，无法保存配置");
            return;
        }

        JSONObject config = new JSONObject();
        try {
            config.put("format", format);
            config.put("width", width);
            config.put("height", height);
            config.put("fps", fps);
            config.put("exposure", exposure);
            config.put("gain", gain);
            config.put("triggerPeriod", triggerPeriod);
            config.put("serial1", serial1 != null ? serial1 : "");
            config.put("serial2", serial2 != null ? serial2 : "");
            config.put("isAutoExposure", isAutoExposure);
            config.put("isColorMode", isColorMode);

            String filename = getConfigFilename(vendorId, productId);
            File configDir = new File(context.getFilesDir(), CONFIG_DIR);
            if (!configDir.exists() && !configDir.mkdirs()) {
                Log.e(TAG, "无法创建配置目录");
                return;
            }

            File configFile = new File(configDir, filename);
            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                fos.write(config.toString().getBytes());
            }
            Log.d(TAG, "配置已保存: " + filename);
        } catch (JSONException | IOException e) {
            Log.e(TAG, "保存配置失败", e);
        }
    }

    public static JSONObject loadConfig(Context context, int vendorId, int productId) {
        if (vendorId <= 0 || productId <= 0 || context == null) {
            Log.e(TAG, "无效的参数，无法加载配置");
            return null;
        }

        String filename = getConfigFilename(vendorId, productId);
        File configFile = new File(new File(context.getFilesDir(), CONFIG_DIR), filename);

        if (!configFile.exists()) {
            return null;
        }

        try (FileInputStream fis = new FileInputStream(configFile);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return new JSONObject(sb.toString());
        } catch (IOException | JSONException e) {
            Log.e(TAG, "加载配置失败", e);
            return null;
        }
    }

    private static String getConfigFilename(int vendorId, int productId) {
        return String.format("cam_%04x_%04x.json", vendorId, productId);
    }
}