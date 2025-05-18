package com.stars.uvccam;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Utils {
    private static String BaseStoragePath = null;
    private static final String APP_NAME = "UVCCamera";

    public static void checkBaseStoragePath(Context context) {
        if (BaseStoragePath == null) {
            BaseStoragePath = Environment.getExternalStorageDirectory().getPath() + File.separator + APP_NAME;
        }
    }

    public static String getSavePhotoPath(Context context) {
        checkBaseStoragePath(context);

        // 创建日期格式化工具
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault());

        Date currentDate = new Date();
        String dateString = dateFormat.format(currentDate);
        String timeString = timeFormat.format(currentDate);

        String parentPath = BaseStoragePath + File.separator + dateString + File.separator + "photo";
        File folder = new File(parentPath);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return parentPath + File.separator + timeString + ".jpg";
    }

    public static Uri getSavePhotoUri(Context context) {
        return Uri.fromFile(new File(getSavePhotoPath(context)));
    }
}
