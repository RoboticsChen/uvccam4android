package com.stars.uvccam;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Utils {
    private static final String APP_NAME = "UVCCamera";

    public static String getSavePhotoPath(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }

        // 获取基础存储路径
        String baseStoragePath = Environment.getExternalStorageDirectory().getPath()
                + File.separator + APP_NAME;

        // 创建日期格式化工具
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault());

        Date currentDate = new Date();
        String dateString = dateFormat.format(currentDate);
        String timeString = timeFormat.format(currentDate);

        String parentPath = baseStoragePath + File.separator + dateString + File.separator + "photo";
        File folder = new File(parentPath);
        if (!folder.exists()) {
            boolean created = folder.mkdirs();
            if (!created) {
                throw new RuntimeException("无法创建保存目录: " + parentPath);
            }
        }
        return parentPath + File.separator + timeString + ".jpg";
    }

    public static Uri getSavePhotoUri(Context context) {
        return Uri.fromFile(new File(getSavePhotoPath(context)));
    }
}