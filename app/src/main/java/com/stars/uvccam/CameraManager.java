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

import java.util.List;
import java.io.File;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;

public class CameraManager {
    private static final String TAG = "CameraManager";
    private static final boolean DEBUG = true;

    private Context mContext;
    private ICameraHelper mCameraHelper;
    private boolean mIsCameraOpened = false;
    
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
        if (DEBUG) Log.d(TAG, "initialize");
        if (mCameraHelper == null) {
            mCameraHelper = new CameraHelper();
            mCameraHelper.setStateCallback(mUVCStateListener);
        }
    }
    
    public void release() {
        if (DEBUG) Log.d(TAG, "release");
        if (mCameraHelper != null) {
            if (mIsCameraOpened) {
                closeCamera();
            }
            mCameraHelper.release();
            mCameraHelper = null;
        }
        mIsCameraOpened = false;
    }
    
    public void openCamera() {
        if (mCameraHelper != null && !mIsCameraOpened) {
            final List<UsbDevice> list = mCameraHelper.getDeviceList();
            if (list != null && list.size() > 0) {
                mCameraHelper.selectDevice(list.get(0));
            } else {
                showToast("没有找到相机设备");
            }
        } else if (mIsCameraOpened) {
            showToast("相机已经打开");
        }
    }

    public void closeCamera() {
        if (mCameraHelper != null && mIsCameraOpened) {
            try {
                // First remove all surfaces to stop frame processing
                if (mContext instanceof MainActivity) {
                    MainActivity activity = (MainActivity) mContext;
                    SurfaceHolder holder = activity.getCameraPreview().getHolder();
                    if (holder != null && holder.getSurface() != null) {
                        mCameraHelper.removeSurface(holder.getSurface());
                    }
                }

                // Add a small delay to allow MediaCodec to complete operations
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Sleep interrupted", e);
                }

                // Now close the camera
                mCameraHelper.closeCamera();
                mIsCameraOpened = false;
            } catch (Exception e) {
                Log.e(TAG, "Error closing camera", e);
            }
        }
    }
    
    public void addSurface(Surface surface) {
        if (mCameraHelper != null) {
            mCameraHelper.addSurface(surface, false);
        }
    }
    
    public void removeSurface(Surface surface) {
        if (mCameraHelper != null) {
            mCameraHelper.removeSurface(surface);
        }
    }
    
    public boolean isCameraOpened() {
        return mIsCameraOpened;
    }
    
    public int getCurrentVendorId() {
        return mCurrentVendorId;
    }
    
    public int getCurrentProductId() {
        return mCurrentProductId;
    }
    
    public Size getPreviewSize() {
        if (mCameraHelper != null && mIsCameraOpened) {
            return mCameraHelper.getPreviewSize();
        }
        return new Size(mPreviewFormat, mPreviewWidth, mPreviewHeight, mPreviewFps, null);
    }

    /**
     * 捕获当前预览帧并保存为图像文件
     */
    public void captureImage() {
        if (!mIsCameraOpened || mCameraHelper == null) {
            showToast("相机未打开，无法拍照");
            return;
        }

        try {
            // 获取保存路径
            String filePath = Utils.getSavePhotoPath(mContext);
            File file = new File(filePath);

            // 确保父目录存在
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 创建输出选项
            ImageCapture.OutputFileOptions options =
                    new ImageCapture.OutputFileOptions.Builder(file).build();

            // 拍照并保存
            mCameraHelper.takePicture(options, new ImageCapture.OnImageCaptureCallback() {
                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                    // 图像保存成功
                    Uri savedUri = outputFileResults.getSavedUri();
                    String path = savedUri != null ? savedUri.getPath() : file.getAbsolutePath();
                    showToast("图像已保存至: " + path);

                    // 通知媒体库更新
                    if (mContext != null) {
                        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        mediaScanIntent.setData(savedUri != null ? savedUri : Uri.fromFile(file));
                        mContext.sendBroadcast(mediaScanIntent);
                    }

                    Log.d(TAG, "图像已保存: " + path);
                }

                @Override
                public void onError(int imageCaptureError, @NonNull String message, @Nullable Throwable cause) {
                    // 图像保存失败
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
        if (mCameraHelper != null && mIsCameraOpened) {
            try {
                Log.d(TAG, "设置预览尺寸: " + size.type + ", " +
                        size.width + "x" + size.height + ", " + size.fps + "fps");

                // 保存当前Surface
                Surface previewSurface = null;
                if (mContext instanceof MainActivity) {
                    MainActivity activity = (MainActivity) mContext;
                    SurfaceHolder holder = activity.getCameraPreview().getHolder();
                    if (holder != null) {
                        previewSurface = holder.getSurface();
                        // 先移除Surface
                        mCameraHelper.removeSurface(previewSurface);
                    }
                }

                // 停止预览
                mCameraHelper.stopPreview();

                // 应用新的格式设置
                mCameraHelper.setPreviewSize(size);

                // 更新当前预览尺寸
                mPreviewWidth = size.width;
                mPreviewHeight = size.height;
                mPreviewFormat = size.type;
                mPreviewFps = size.fps;

                // 重新开始预览
                mCameraHelper.startPreview();

                // 重新添加Surface
                if (previewSurface != null) {
                    mCameraHelper.addSurface(previewSurface, false);
                }

                // 更新UI显示预览尺寸
                if (mContext instanceof MainActivity) {
                    MainActivity activity = (MainActivity) mContext;
                    activity.runOnUiThread(() -> {
                        activity.getCameraPreview().setAspectRatio(size.width, size.height);
                    });
                }

                showToast(String.format("已应用格式: %s, %dx%d, %dfps",
                        (size.type == UVCCamera.UVC_VS_FRAME_MJPEG ? "MJPEG" : "YUV"),
                        size.width, size.height, size.fps));

            } catch (Exception e) {
                Log.e(TAG, "设置预览尺寸失败", e);
                showToast("设置预览尺寸失败: " + e.getMessage());

                // 尝试恢复预览
                try {
                    mCameraHelper.startPreview();

                    // 尝试重新添加Surface
                    if (mContext instanceof MainActivity) {
                        MainActivity activity = (MainActivity) mContext;
                        SurfaceHolder holder = activity.getCameraPreview().getHolder();
                        if (holder != null) {
                            mCameraHelper.addSurface(holder.getSurface(), false);
                        }
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "恢复预览失败", ex);
                }
            }
        } else {
            Log.w(TAG, "无法设置预览尺寸：相机未打开");
        }
    }
    
    public UVCControl getUVCControl() {
        if (mCameraHelper != null && mIsCameraOpened) {
            return mCameraHelper.getUVCControl();
        }
        return null;
    }
    
    public List<com.serenegiant.usb.Format> getSupportedFormatList() {
        if (mCameraHelper != null && mIsCameraOpened) {
            return mCameraHelper.getSupportedFormatList();
        }
        return null;
    }
    
    private void showToast(String message) {
        if (mContext != null) {
            Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
        }
    }
    
    private final ICameraHelper.StateCallback mUVCStateListener = new ICameraHelper.StateCallback() {
        @Override
        public void onAttach(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onAttach: " + device.getDeviceName());
            if (mStateListener != null) {
                mStateListener.onDeviceAttached(device);
            }
            mCameraHelper.selectDevice(device);
        }

        @Override
        public void onDeviceOpen(UsbDevice device, boolean isFirstOpen) {
            if (DEBUG) Log.v(TAG, "onDeviceOpen: " + isFirstOpen);
            mCameraHelper.openCamera();
        }

        @Override
        public void onCameraOpen(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onCameraOpen");
            
            mCurrentVendorId = device.getVendorId();
            mCurrentProductId = device.getProductId();
            mIsCameraOpened = true;

            loadSavedCameraParameters();

            // 获取当前预览尺寸
            Size size = mCameraHelper.getPreviewSize();

            if (size != null) {
                mPreviewWidth = size.width;
                mPreviewHeight = size.height;
                mPreviewFps = size.fps;
                mPreviewFormat = size.type;

                // 更新预览窗比例
                if (mContext instanceof MainActivity) {
                    MainActivity activity = (MainActivity) mContext;
                    activity.runOnUiThread(() -> {
                        activity.getCameraPreview().setAspectRatio(size.width, size.height);
                    });
                }
            }

            // 通知监听器相机已打开 - 先通知，让监听器有机会加载配置
            if (mStateListener != null) {
                mStateListener.onCameraOpened(device, size);
            }

            // 延迟一点开始预览，确保配置已应用
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    // 开始预览
                    if (mCameraHelper != null && mIsCameraOpened) {
                        mCameraHelper.startPreview();
                        Log.d(TAG, "相机预览已启动");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "启动预览失败", e);
                }
            }, 100); // 延迟100ms
        }

        @Override
        public void onCameraClose(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onCameraClose");
            
            if (mCameraHelper != null) {
                // 停止预览
                try {
                    if (mContext instanceof MainActivity) {
                        MainActivity activity = (MainActivity) mContext;
                        SurfaceHolder holder = activity.getCameraPreview().getHolder();
                        if (holder != null && holder.getSurface() != null) {
                            mCameraHelper.removeSurface(holder.getSurface());
                        }
                    }
                    mCameraHelper.stopPreview();
                } catch (Exception e) {
                    Log.e(TAG, "Error stopping preview", e);
                }
            }
            
            mIsCameraOpened = false;
            
            // 通知监听器
            if (mStateListener != null) {
                mStateListener.onCameraClosed();
            }
        }

        @Override
        public void onDeviceClose(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onDeviceClose");
        }

        @Override
        public void onDetach(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onDetach");
        }

        @Override
        public void onCancel(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onCancel");
        }
    };

    private void loadSavedCameraParameters() {
        try {
            JSONObject config = ConfigManager.loadConfig(mContext, mCurrentVendorId, mCurrentProductId);
            if (config != null) {
                // 从配置中获取格式参数
                int format = config.optInt("format", mPreviewFormat);
                int width = config.optInt("width", mPreviewWidth);
                int height = config.optInt("height", mPreviewHeight);
                int fps = config.optInt("fps", mPreviewFps);

                Log.d(TAG, "加载已保存的相机参数: 格式=" + format +
                        ", 分辨率=" + width + "x" + height +
                        ", 帧率=" + fps);

                // 创建新的尺寸对象
                Size savedSize = new Size(format, width, height, fps, null);

                // 应用保存的格式设置
                setPreviewSize(savedSize);
            }
        } catch (Exception e) {
            Log.e(TAG, "加载已保存的相机参数失败", e);
        }
    }

    // 获取当前已连接的USB设备列表
    public List<UsbDevice> getDeviceList() {
        if (mCameraHelper != null) {
            return mCameraHelper.getDeviceList();
        }
        return null;
    }

    // 设置默认的预览尺寸，在相机打开前调用
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
}
