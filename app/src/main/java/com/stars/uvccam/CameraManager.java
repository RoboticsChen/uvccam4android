package com.stars.uvccam;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.widget.Toast;
import com.herohan.uvcapp.CameraHelper;
import com.herohan.uvcapp.ICameraHelper;
import com.herohan.uvcapp.ImageCapture;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.UVCControl;
import com.serenegiant.usb.Format;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.File;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.ArrayList;

public class CameraManager {
    private static final String TAG = "CameraManager";

    private final Context mContext;
    private ICameraHelper mCameraHelper;
    private final AtomicBoolean mIsCameraOpened = new AtomicBoolean(false);
    private final Object mCameraLock = new Object();

    // 当前连接的设备信息
    private int mCurrentVendorId = -1;
    private int mCurrentProductId = -1;
    private int mPreviewWidth = UVCCamera.DEFAULT_PREVIEW_WIDTH;
    private int mPreviewHeight = UVCCamera.DEFAULT_PREVIEW_HEIGHT;
    private int mPreviewFormat = UVCCamera.UVC_VS_FRAME_MJPEG;
    private int mPreviewFps = UVCCamera.DEFAULT_PREVIEW_FPS;
    
    // 相机状态回调接口
    public interface CameraStateListener {
        void onCameraOpened(UsbDevice device, Size previewSize);
        void onCameraClosed();
        void onDeviceAttached(UsbDevice device);
    }

    private CameraStateListener mStateListener;

    public CameraManager(Context context) {
        mContext = context;
    }

    public void setStateListener(CameraStateListener listener) {
        mStateListener = listener;
    }

    public void initialize() {
        synchronized(mCameraLock) {
            if (mCameraHelper == null) {
                mCameraHelper = new CameraHelper();
                mCameraHelper.setStateCallback(mUVCStateListener);
            }
        }
    }

    public void release() {
        synchronized(mCameraLock) {
            if (mIsCameraOpened.get()) {
                closeCamera();
            }

            if (mCameraHelper != null) {
                mCameraHelper.release();
                mCameraHelper = null;
            }
            mIsCameraOpened.set(false);
        }
    }

    public void openCamera() {
        synchronized(mCameraLock) {
            if (mCameraHelper == null || mIsCameraOpened.get()) {
                showToast(mIsCameraOpened.get() ? "相机已经打开" : "相机初始化中");
                return;
            }

            final List<UsbDevice> list = mCameraHelper.getDeviceList();
            if (list != null && !list.isEmpty()) {
                mCameraHelper.selectDevice(list.get(0));
            } else {
                showToast("没有找到相机设备");
            }
        }
    }

    public void closeCamera() {
        synchronized(mCameraLock) {
            if (mCameraHelper == null || !mIsCameraOpened.get()) {
                return;
            }

            try {
                Log.d(TAG, "停止预览");
                mCameraHelper.stopPreview();
                Thread.sleep(50);

                // 先移除所有Surface以停止帧处理
                if (mContext instanceof MainActivity) {
                    MainActivity activity = (MainActivity) mContext;
                    SurfaceHolder holder = activity.getCameraPreview().getHolder();
                    if (holder != null && holder.getSurface() != null && holder.getSurface().isValid()) {
                        Log.d(TAG, "移除Surface");
                        mCameraHelper.removeSurface(holder.getSurface());
                    }
                }

                Log.d(TAG, "等待资源释放");
                Thread.sleep(200);

                Log.d(TAG, "正在关闭相机");
                mCameraHelper.closeCamera();
                Log.d(TAG, "相机已关闭");

                Log.d(TAG, "等待资源释放");
                Thread.sleep(200);
            } catch (Exception e) {
                Log.e(TAG, "关闭相机时出错", e);
            }
        }
    }

    public void addSurface(Surface surface) {
        if (surface != null && surface.isValid() && mCameraHelper != null) {
            mCameraHelper.addSurface(surface, false);
        }
    }

    public void removeSurface(Surface surface) {
        if (surface != null && surface.isValid() && mCameraHelper != null) {
            mCameraHelper.removeSurface(surface);
        }
    }

    public boolean isCameraOpened() {
        return mIsCameraOpened.get();
    }

    public int getCurrentVendorId() {
        return mCurrentVendorId;
    }

    public int getCurrentProductId() {
        return mCurrentProductId;
    }

    public Size getPreviewSize() {
        if (mCameraHelper != null && mIsCameraOpened.get()) {
            return mCameraHelper.getPreviewSize();
        }
        return new Size(mPreviewFormat, mPreviewWidth, mPreviewHeight, mPreviewFps, null);
    }

    public void captureImage() {
        if (!mIsCameraOpened.get() || mCameraHelper == null) {
            showToast("相机未打开，无法拍照");
            return;
        }

        try {
            String filePath = Utils.getSavePhotoPath(mContext);
            File file = new File(filePath);

            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    showToast("无法创建保存目录");
                    return;
                }
            }

            ImageCapture.OutputFileOptions options =
                    new ImageCapture.OutputFileOptions.Builder(file).build();

            mCameraHelper.takePicture(options, new ImageCapture.OnImageCaptureCallback() {
                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                    Uri savedUri = outputFileResults.getSavedUri();
                    String path = savedUri != null ? savedUri.getPath() : file.getAbsolutePath();
                    showToast("图像已保存至: " + path);

                    if (mContext != null) {
                        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        mediaScanIntent.setData(savedUri != null ? savedUri : Uri.fromFile(file));
                        mContext.sendBroadcast(mediaScanIntent);
                    }
                }

                @Override
                public void onError(int imageCaptureError, @NonNull String message, @Nullable Throwable cause) {
                    showToast("保存图像失败: " + message);
                    Log.e(TAG, "保存图像失败: " + message, cause);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "拍照过程中发生错误", e);
            showToast("拍照失败: " + e.getMessage());
        }
    }

    public void setPreviewSize(Size size) {
        if (mCameraHelper == null || !mIsCameraOpened.get() || size == null) {
            Log.w(TAG, "无法设置预览尺寸：相机未打开或尺寸无效");
            return;
        }

        try {
            Log.d(TAG, "设置预览尺寸: " + size.type + ", " +
                    size.width + "x" + size.height + ", " + size.fps + "fps");

            Surface previewSurface = null;
            if (mContext instanceof MainActivity) {
                MainActivity activity = (MainActivity) mContext;
                SurfaceHolder holder = activity.getCameraPreview().getHolder();
                if (holder != null && holder.getSurface() != null && holder.getSurface().isValid()) {
                    previewSurface = holder.getSurface();
                    mCameraHelper.removeSurface(previewSurface);
                }
            }

            mCameraHelper.stopPreview();
            mCameraHelper.setPreviewSize(size);

            mPreviewWidth = size.width;
            mPreviewHeight = size.height;
            mPreviewFormat = size.type;
            mPreviewFps = size.fps;

            mCameraHelper.startPreview();

            if (previewSurface != null && previewSurface.isValid()) {
                mCameraHelper.addSurface(previewSurface, false);
            }

            if (mContext instanceof MainActivity) {
                MainActivity activity = (MainActivity) mContext;
                activity.runOnUiThread(() ->
                        activity.getCameraPreview().setAspectRatio(size.width, size.height));
            }

            showToast(String.format("已应用格式: %s, %dx%d, %dfps",
                    (size.type == UVCCamera.UVC_VS_FRAME_MJPEG ? "MJPEG" : "YUV"),
                    size.width, size.height, size.fps));

        } catch (Exception e) {
            Log.e(TAG, "设置预览尺寸失败", e);
            showToast("设置预览尺寸失败");

            try {
                mCameraHelper.startPreview();
                if (mContext instanceof MainActivity) {
                    MainActivity activity = (MainActivity) mContext;
                    SurfaceHolder holder = activity.getCameraPreview().getHolder();
                    if (holder != null && holder.getSurface() != null && holder.getSurface().isValid()) {
                        mCameraHelper.addSurface(holder.getSurface(), false);
                    }
                }
            } catch (Exception ex) {
                Log.e(TAG, "恢复预览失败", ex);
            }
        }
    }

    public UVCControl getUVCControl() {
        if (mCameraHelper != null && mIsCameraOpened.get()) {
            return mCameraHelper.getUVCControl();
        }
        return null;
    }

    public List<com.serenegiant.usb.Format> getSupportedFormatList() {
        if (mCameraHelper != null && mIsCameraOpened.get()) {
            return mCameraHelper.getSupportedFormatList();
        }
        return null;
    }

    private void showToast(String message) {
        if (mContext != null) {
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show());
        }
    }

    private final ICameraHelper.StateCallback mUVCStateListener = new ICameraHelper.StateCallback() {
        @Override
        public void onAttach(UsbDevice device) {
            Log.v(TAG, "onAttach: " + device.getDeviceName());
            if (mStateListener != null) {
                mStateListener.onDeviceAttached(device);
            }
            mCameraHelper.selectDevice(device);
        }

        @Override
        public void onDeviceOpen(UsbDevice device, boolean isFirstOpen) {
            Log.v(TAG, "onDeviceOpen: " + isFirstOpen);
            mCameraHelper.openCamera();
        }

        @Override
        public void onCameraOpen(UsbDevice device) {
            Log.v(TAG, "onCameraOpen");

            mCurrentVendorId = device.getVendorId();
            mCurrentProductId = device.getProductId();
            mIsCameraOpened.set(true);

            // 先加载并应用配置
            loadSavedCameraParameters();

            // 添加短暂延时确保配置已应用，然后再获取预览尺寸
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                // 在延时后获取当前尺寸(此时应已是配置的值)并更新预览框
                Size size = getPreviewSize();
                if (size != null && mContext instanceof MainActivity) {
                    MainActivity activity = (MainActivity) mContext;
                    activity.runOnUiThread(() ->
                            activity.getCameraPreview().setAspectRatio(size.width, size.height));
                }

                // 通知监听器
                if (mStateListener != null) {
                    mStateListener.onCameraOpened(device, size);
                }

                // 开始预览
                startCameraPreview();
            }, 100); // 短暂延迟让设置生效
        }

        @Override
        public void onCameraClose(UsbDevice device) {
            Log.v(TAG, "onCameraClose");

            synchronized(mCameraLock) {
                if (mCameraHelper != null) {
                    try {
                        if (mContext instanceof MainActivity) {
                            MainActivity activity = (MainActivity) mContext;
                            SurfaceHolder holder = activity.getCameraPreview().getHolder();
                            if (holder != null && holder.getSurface() != null && holder.getSurface().isValid()) {
                                mCameraHelper.removeSurface(holder.getSurface());
                            }
                        }
                        mCameraHelper.stopPreview();
                    } catch (Exception e) {
                        Log.e(TAG, "停止预览失败", e);
                    }
                }

                mIsCameraOpened.set(false);
            }

            if (mStateListener != null) {
                mStateListener.onCameraClosed();
            }
        }

        @Override
        public void onDeviceClose(UsbDevice device) {
            Log.v(TAG, "onDeviceClose");
        }

        @Override
        public void onDetach(UsbDevice device) {
            Log.v(TAG, "onDetach");
        }

        @Override
        public void onCancel(UsbDevice device) {
            Log.v(TAG, "onCancel");
        }
    };

    private void startCameraPreview() {
        synchronized(mCameraLock) {
            try {
                if (mCameraHelper != null && mIsCameraOpened.get()) {
                    mCameraHelper.startPreview();
                    Log.d(TAG, "相机预览已启动");
                }
            } catch (Exception e) {
                Log.e(TAG, "启动预览失败", e);
            }
        }
    }

    private void loadSavedCameraParameters() {
        try {
            // 先获取相机支持的格式列表
            List<Format> supportedFormats = mCameraHelper.getSupportedFormatList();
            if (supportedFormats == null || supportedFormats.isEmpty()) {
                Log.w(TAG, "无法获取相机支持的格式列表，使用默认参数");
                return;
            }

            // 构建可用的格式映射，用于验证
            HashMap<Integer, List<Size>> supportedSizesMap = buildSupportedSizesMap(supportedFormats);

            // 加载保存的配置
            JSONObject config = ConfigManager.loadConfig(mContext, mCurrentVendorId, mCurrentProductId);
            Size configSize = null;

            if (config != null) {
                int format = config.optInt("format", mPreviewFormat);
                int width = config.optInt("width", mPreviewWidth);
                int height = config.optInt("height", mPreviewHeight);
                int fps = config.optInt("fps", mPreviewFps);

                Log.d(TAG, "加载已保存的相机参数: 格式=" + format +
                        ", 分辨率=" + width + "x" + height +
                        ", 帧率=" + fps);

                configSize = new Size(format, width, height, fps, null);

                // 验证配置的参数是否被相机支持
                if (isSizeSupported(configSize, supportedSizesMap)) {
                    Log.d(TAG, "配置的参数受支持，应用配置");
                    setPreviewSize(configSize);
                    return;
                } else {
                    Log.w(TAG, "配置的参数不受支持，将使用第一组支持的参数");
                }
            } else {
                Log.d(TAG, "未找到已保存的配置，将使用第一组支持的参数");
            }

            // 如果配置不存在或不受支持，使用第一组支持的参数
            Size firstSupportedSize = getFirstSupportedSize(supportedSizesMap);
            if (firstSupportedSize != null) {
                Log.d(TAG, "使用第一组受支持的参数: 格式=" + firstSupportedSize.type +
                        ", 分辨率=" + firstSupportedSize.width + "x" + firstSupportedSize.height +
                        ", 帧率=" + firstSupportedSize.fps);

                // 应用第一组支持的参数
                setPreviewSize(firstSupportedSize);

                // 更新配置文件，保存新使用的参数
                saveNewConfigParameters(firstSupportedSize);
            } else {
                Log.e(TAG, "无法找到相机支持的参数，使用默认值");
            }
        } catch (Exception e) {
            Log.e(TAG, "加载已保存的相机参数失败", e);
        }
    }

    // 构建相机支持的格式映射
    private HashMap<Integer, List<Size>> buildSupportedSizesMap(List<Format> supportedFormats) {
        HashMap<Integer, List<Size>> result = new HashMap<>();

        for (Format format : supportedFormats) {
            int type;
            if (format.type == UVCCamera.UVC_VS_FORMAT_MJPEG) {
                type = UVCCamera.UVC_VS_FRAME_MJPEG;
            } else if (format.type == UVCCamera.UVC_VS_FORMAT_UNCOMPRESSED) {
                type = UVCCamera.UVC_VS_FRAME_UNCOMPRESSED;
            } else {
                continue;  // 跳过不支持的格式类型
            }

            if (!result.containsKey(type)) {
                result.put(type, new ArrayList<>());
            }

            List<Size> sizeList = result.get(type);

            // 为每个描述符和帧率创建Size对象
            for (Format.Descriptor descriptor : format.frameDescriptors) {
                for (Format.Interval interval : descriptor.intervals) {
                    Size size = new Size(type, descriptor.width, descriptor.height, interval.fps, null);
                    sizeList.add(size);
                }
            }
        }

        return result;
    }

    // 检查指定的Size是否被相机支持
    private boolean isSizeSupported(Size size, HashMap<Integer, List<Size>> supportedSizesMap) {
        if (!supportedSizesMap.containsKey(size.type)) {
            return false;
        }

        List<Size> sizeList = supportedSizesMap.get(size.type);
        for (Size supportedSize : sizeList) {
            if (supportedSize.width == size.width &&
                    supportedSize.height == size.height &&
                    supportedSize.fps == size.fps) {
                return true;
            }
        }

        return false;
    }

    // 获取第一组支持的参数
    private Size getFirstSupportedSize(HashMap<Integer, List<Size>> supportedSizesMap) {
        // 优先尝试MJPEG格式
        if (supportedSizesMap.containsKey(UVCCamera.UVC_VS_FRAME_MJPEG) &&
                !supportedSizesMap.get(UVCCamera.UVC_VS_FRAME_MJPEG).isEmpty()) {
            return supportedSizesMap.get(UVCCamera.UVC_VS_FRAME_MJPEG).get(0);
        }

        // 其次尝试YUV格式
        if (supportedSizesMap.containsKey(UVCCamera.UVC_VS_FRAME_UNCOMPRESSED) &&
                !supportedSizesMap.get(UVCCamera.UVC_VS_FRAME_UNCOMPRESSED).isEmpty()) {
            return supportedSizesMap.get(UVCCamera.UVC_VS_FRAME_UNCOMPRESSED).get(0);
        }

        return null;
    }

    // 保存新的配置参数
    private void saveNewConfigParameters(Size size) {
        if (size == null) return;

        int exposure = 0;
        int gain = 0;
        int triggerPeriod = 0;
        int isAutoExposure = 1;  // 默认自动曝光
        int isColorMode = 1;     // 默认彩色模式

        // 尝试从已有配置读取其他参数
        JSONObject config = ConfigManager.loadConfig(mContext, mCurrentVendorId, mCurrentProductId);
        if (config != null) {
            exposure = config.optInt("exposure", 0);
            gain = config.optInt("gain", 0);
            triggerPeriod = config.optInt("triggerPeriod", 0);
            isAutoExposure = config.optInt("isAutoExposure", 1);
            isColorMode = config.optInt("isColorMode", 1);
        }

        // 保存更新后的配置
        ConfigManager.saveConfig(mContext, mCurrentVendorId, mCurrentProductId,
                size.type, size.width, size.height, size.fps,
                exposure, gain, triggerPeriod, "", "",
                isAutoExposure, isColorMode);

        Log.d(TAG, "已更新配置文件，保存新使用的参数");
    }

    public List<UsbDevice> getDeviceList() {
        if (mCameraHelper != null) {
            return mCameraHelper.getDeviceList();
        }
        return null;
    }

    public void setDefaultPreviewSize(Size size) {
        if (size != null) {
            mPreviewFormat = size.type;
            mPreviewWidth = size.width;
            mPreviewHeight = size.height;
            mPreviewFps = size.fps;

            Log.d(TAG, "设置默认预览尺寸: 格式=" + mPreviewFormat +
                    ", 分辨率=" + mPreviewWidth + "x" + mPreviewHeight +
                    ", 帧率=" + mPreviewFps);
        }
    }

    // 退出参数面板时重新加载参数
    public void reloadSavedParameters() {
        try {
            JSONObject config = ConfigManager.loadConfig(mContext, mCurrentVendorId, mCurrentProductId);
            if (config != null) {
                // 加载并应用预览尺寸
                int format = config.optInt("format", mPreviewFormat);
                int width = config.optInt("width", mPreviewWidth);
                int height = config.optInt("height", mPreviewHeight);
                int fps = config.optInt("fps", mPreviewFps);

                Log.d(TAG, "重新加载相机参数: 格式=" + format +
                        ", 分辨率=" + width + "x" + height +
                        ", 帧率=" + fps);

                Size savedSize = new Size(format, width, height, fps, null);
                setPreviewSize(savedSize);

                // 获取UVC控制接口
                UVCControl control = getUVCControl();
                if (control != null) {
                    // 加载并应用曝光设置
                    boolean isAutoExposure = config.optInt("isAutoExposure", 1) == 1;
                    control.setExposureTimeAuto(isAutoExposure);

                    // 如果是手动曝光，应用曝光时间和增益
                    if (!isAutoExposure) {
                        int exposure = config.optInt("exposure", 0);
                        if (exposure > 0 && control.isExposureTimeAbsoluteEnable()) {
                            control.setExposureTimeAbsolute(exposure);
                        }

                        int gain = config.optInt("gain", 0);
                        if (gain > 0 && control.isGainEnable()) {
                            control.setGain(gain);
                        }
                    }

                    // 加载并应用彩色/黑白模式
                    boolean isColorMode = config.optInt("isColorMode", 1) == 1;
                    if (isColorMode) {
                        control.resetSaturation();
                    } else {
                        control.setSaturation(0);
                    }

                    Log.d(TAG, "已重新应用曝光和颜色设置: " +
                            "自动曝光=" + isAutoExposure +
                            ", 彩色模式=" + isColorMode);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "重新加载相机参数失败", e);
        }
    }
}