package com.stars.uvccam;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.serenegiant.usb.Size;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.UVCControl;
import org.json.JSONObject;

public class SettingsManager implements CameraManager.CameraStateListener {
    private static final String TAG = "SettingsManager";
    private static final boolean DEBUG = true;

    private Context mContext;
    private CameraManager mCameraManager;
    private FormatManager mFormatManager;
    private MainActivity mMainActivity;

    // 当前相机设置
    private Size mCurrentSize = new Size(UVCCamera.UVC_VS_FRAME_MJPEG, 
            UVCCamera.DEFAULT_PREVIEW_WIDTH, 
            UVCCamera.DEFAULT_PREVIEW_HEIGHT, 
            UVCCamera.DEFAULT_PREVIEW_FPS, 
            null);

    // UI组件
    private Button mAutoExposureButton;
    private Button mColorModeButton;
    private EditText mExposureTimeInput;
    private EditText mGainInput;
    private EditText mTriggerPeriodInput;
    private EditText mSerialCommandInput1;
    private EditText mSerialCommandInput2;
    private Spinner mFormatSpinner;
    private Spinner mResolutionSpinner;
    private Spinner mFrameRateSpinner;

    // 是否为自动曝光模式
    private boolean mIsAutoExposure = true;
    // 是否为彩色模式
    private boolean mIsColorMode = true;

    public SettingsManager(Context context, CameraManager cameraManager, FormatManager formatManager) {
        mContext = context;
        mCameraManager = cameraManager;
        mFormatManager = formatManager;
        
        if (context instanceof MainActivity) {
            mMainActivity = (MainActivity) context;
        }
        
        // 注册相机状态监听
        mCameraManager.setStateListener(this);
    }

    public void initUIComponents(View settingView) {
        // 获取UI组件引用
        mAutoExposureButton = settingView.findViewById(R.id.auto_exposure);
        mColorModeButton = settingView.findViewById(R.id.color_mode);
        mExposureTimeInput = settingView.findViewById(R.id.exposure_time_input);
        mGainInput = settingView.findViewById(R.id.gain_input);
        mTriggerPeriodInput = settingView.findViewById(R.id.trigger_period_input);
        mSerialCommandInput1 = settingView.findViewById(R.id.serial_command_input1);
        mSerialCommandInput2 = settingView.findViewById(R.id.serial_command_input2);
        mFormatSpinner = settingView.findViewById(R.id.format_spinner);
        mResolutionSpinner = settingView.findViewById(R.id.resolution_spinner);
        mFrameRateSpinner = settingView.findViewById(R.id.framerate_spinner);

        // 设置监听器
        mExposureTimeInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                applyExposureSetting();
            }
        });

        mGainInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                applyGainSetting();
            }
        });

        mTriggerPeriodInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                applyTriggerPeriodSetting();
            }
        });

        // 设置按钮点击监听器
        if (mMainActivity != null) {
            mAutoExposureButton.setOnClickListener(mMainActivity);
            mColorModeButton.setOnClickListener(mMainActivity);
        }
    }

    @Override
    public void onCameraOpened(UsbDevice device, Size previewSize) {
        if (previewSize != null) {
            mCurrentSize = previewSize;
        }
        
        // 加载保存的配置
        loadSavedConfig();

        // 应用配置到相机
        applySavedConfigToCamera();

        // 更新UI显示
        updateCameraParameters();
    }

    @Override
    public void onCameraClosed() {
        // 保存当前配置
        saveCurrentConfig();
    }

    @Override
    public void onDeviceAttached(UsbDevice device) {
        // 不需要处理
    }

    private void applySavedConfigToCamera() {
        if (mCameraManager == null || !mCameraManager.isCameraOpened()) {
            Log.d(TAG, "相机未打开，无法应用配置");
            return;
        }

        try {
            // 应用相机格式和分辨率
            mCameraManager.setPreviewSize(mCurrentSize);

            // 应用曝光和增益设置
            UVCControl control = mCameraManager.getUVCControl();
            if (control != null) {
                // 应用自动/手动曝光
                control.setExposureTimeAuto(mIsAutoExposure ? true : false);

                if (!mIsAutoExposure) {
                    // 如果是手动曝光，应用曝光和增益值
                    try {
                        String exposureText = mExposureTimeInput.getText().toString();
                        if (!TextUtils.isEmpty(exposureText)) {
                            int exposure = Integer.parseInt(exposureText);
                            control.setExposureTimeAbsolute(exposure);
                        }

                        String gainText = mGainInput.getText().toString();
                        if (!TextUtils.isEmpty(gainText)) {
                            int gain = Integer.parseInt(gainText);
                            control.setGain(gain);
                        }
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "解析曝光或增益值失败", e);
                    }
                }

                // 应用彩色/黑白模式
                if (mIsColorMode) {
                    control.resetSaturation();
                } else {
                    control.setSaturation(0);
                }
            }

            Log.d(TAG, "已应用保存的配置到相机");
        } catch (Exception e) {
            Log.e(TAG, "应用配置到相机失败", e);
        }
    }

    // 应用曝光设置
    private void applyExposureSetting() {
        if (mCameraManager == null || !mCameraManager.isCameraOpened()) {
            showToast("请先打开相机");
            return;
        }

        UVCControl control = mCameraManager.getUVCControl();
        if (control == null || !control.isExposureTimeAbsoluteEnable()) {
            showToast("曝光时间调整不支持");
            return;
        }

        String exposureText = mExposureTimeInput.getText().toString();
        if (!TextUtils.isEmpty(exposureText)) {
            try {
                int exposureTime = Integer.parseInt(exposureText);
                int[] limits = control.updateExposureTimeAbsoluteLimit();

                // 验证范围
                if (exposureTime < limits[0] || exposureTime > limits[1]) {
                    showToast(String.format("曝光时间需在%d-%d之间", limits[0], limits[1]));
                    return;
                }

                control.setExposureTimeAbsolute(exposureTime);
                showToast("曝光时间已设置为: " + exposureTime + " us");

            } catch (NumberFormatException e) {
                showToast("请输入有效的曝光时间数值");
            }
        }
        saveCurrentConfig();
    }

    // 应用增益设置
    private void applyGainSetting() {
        if (mCameraManager == null || !mCameraManager.isCameraOpened()) {
            showToast("请先打开相机");
            return;
        }

        UVCControl control = mCameraManager.getUVCControl();
        if (control == null || !control.isGainEnable()) {
            showToast("增益调整不支持");
            return;
        }

        String gainText = mGainInput.getText().toString();
        if (!TextUtils.isEmpty(gainText)) {
            try {
                int gain = Integer.parseInt(gainText);
                int[] limits = control.updateGainLimit();

                // 验证范围
                if (gain < limits[0] || gain > limits[1]) {
                    showToast(String.format("增益值需在%d-%d之间", limits[0], limits[1]));
                    return;
                }

                control.setGain(gain);
                showToast("增益值已设置为: " + gain);

            } catch (NumberFormatException e) {
                showToast("请输入有效的增益值数值");
            }
        }
        saveCurrentConfig();
    }

    // 应用触发周期设置
    private void applyTriggerPeriodSetting() {
        if (mCameraManager == null || !mCameraManager.isCameraOpened()) {
            showToast("请先打开相机");
            return;
        }

        try {
            int triggerPeriod = Integer.parseInt(mTriggerPeriodInput.getText().toString());
            // 这里添加实际控制相机的代码
            showToast("触发周期已设置为: " + triggerPeriod + " ms");
            saveCurrentConfig();
        } catch (NumberFormatException e) {
            showToast("请输入有效的触发周期数值");
        }
    }

    // 切换自动曝光
    public void toggleAutoExposure() {
        if (mCameraManager == null || !mCameraManager.isCameraOpened()) {
            showToast("请先打开相机");
            return;
        }

        UVCControl control = mCameraManager.getUVCControl();
        if (control == null) {
            return;
        }

        mIsAutoExposure = !mIsAutoExposure;
        
        if (mIsAutoExposure) {
            control.setExposureTimeAuto(true);
            mAutoExposureButton.setText("自动曝光");
            mExposureTimeInput.setEnabled(false);
            mGainInput.setEnabled(false);
            showToast("切换到自动曝光模式");
        } else {
            control.setExposureTimeAuto(false);
            mAutoExposureButton.setText("手动曝光");
            mExposureTimeInput.setEnabled(true);
            mGainInput.setEnabled(true);
            try {
                String exposureText = mExposureTimeInput.getText().toString();
                if (!TextUtils.isEmpty(exposureText)) {
                    int exposureTime = Integer.parseInt(exposureText);
                    control.setExposureTimeAbsolute(exposureTime);
                }
            } catch (NumberFormatException e) {
                int[] limits = control.updateExposureTimeAbsoluteLimit();
                showToast(String.format("曝光时间需在%d-%d之间", limits[0], limits[1]));
                Log.e(TAG, "Invalid exposure value", e);
            }
            try {
                String gainText = mGainInput.getText().toString();
                if (!TextUtils.isEmpty(gainText)) {
                    int gain = Integer.parseInt(gainText);
                    control.setGain(gain);
                }
            } catch (NumberFormatException e) {
                int[] limits = control.updateGainLimit();
                showToast(String.format("增益值需在%d-%d之间", limits[0], limits[1]));
                Log.e(TAG, "Invalid gain value", e);
            }
            showToast("切换到手动曝光模式");
        }
        
        saveCurrentConfig();
    }

    // 切换彩色/黑白模式
    public void toggleColorMode() {
        if (mCameraManager == null || !mCameraManager.isCameraOpened()) {
            showToast("请先打开相机");
            return;
        }

        UVCControl control = mCameraManager.getUVCControl();
        if (control == null) {
            return;
        }

        mIsColorMode = !mIsColorMode;

        if (mIsColorMode) {
            // 设置为彩色模式
            control.setHueAuto(true);
            control.resetSaturation();
            mColorModeButton.setText("黑白模式");
            showToast(String.format("已切换到彩色模式, saturation： %d",control.getSaturation()));
        } else {
            // 设置为黑白模式
            control.setHueAuto(false);
            control.setSaturation(0);
            mColorModeButton.setText("彩色模式");
            showToast(String.format("已切换到黑白模式, saturation： %d",control.getSaturation()));
        }
    }

    // 更新相机参数显示
    public void updateCameraParameters() {
        if (mCameraManager == null || !mCameraManager.isCameraOpened()) {
            return;
        }

        UVCControl control = mCameraManager.getUVCControl();
        if (control == null) {
            return;
        }

        try {
            // 曝光时间
            if (control.isExposureTimeAbsoluteEnable()) {
                int[] exposureLimits = control.updateExposureTimeAbsoluteLimit();
                int exposureValue = control.getExposureTimeAbsolute();
                mExposureTimeInput.setText(String.valueOf(exposureValue));
                mExposureTimeInput.setHint(String.format("范围: %d-%d", exposureLimits[0], exposureLimits[1]));
                mExposureTimeInput.setEnabled(!mIsAutoExposure);
            } else {
                mExposureTimeInput.setEnabled(false);
                mExposureTimeInput.setHint("不支持曝光调整");
            }

            // 增益值
            if (control.isGainEnable()) {
                int[] gainLimits = control.updateGainLimit();
                int gainValue = control.getGain();
                mGainInput.setText(String.valueOf(gainValue));
                mGainInput.setHint(String.format("范围: %d-%d", gainLimits[0], gainLimits[1]));
                mGainInput.setEnabled(!mIsAutoExposure);
            } else {
                mGainInput.setEnabled(false);
                mGainInput.setHint("不支持增益调整");
            }
            
            // 自动曝光状态
            mIsAutoExposure = control.isExposureTimeAuto();
            mAutoExposureButton.setText(mIsAutoExposure ? "自动曝光" : "手动曝光");

            int saturation = control.getSaturation();
            mIsColorMode = saturation > 0;
            mColorModeButton.setText(mIsColorMode ? "彩色模式" : "黑白模式");
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating camera parameters", e);
        }
    }

    // 从UI保存参数到相机
    public void saveParamsFromUI() {
        if (mCameraManager == null || !mCameraManager.isCameraOpened()) {
            return;
        }

        // 读取并应用曝光时间
        try {
            String exposureText = mExposureTimeInput.getText().toString();
            if (!TextUtils.isEmpty(exposureText)) {
                int exposure = Integer.parseInt(exposureText);
                UVCControl control = mCameraManager.getUVCControl();
                if (control != null && control.isExposureTimeAbsoluteEnable()) {
                    control.setExposureTimeAbsolute(exposure);
                }
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid exposure value", e);
        }

        // 读取并应用增益
        try {
            String gainText = mGainInput.getText().toString();
            if (!TextUtils.isEmpty(gainText)) {
                int gain = Integer.parseInt(gainText);
                UVCControl control = mCameraManager.getUVCControl();
                if (control != null && control.isGainEnable()) {
                    control.setGain(gain);
                }
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid gain value", e);
        }

        // 获取格式设置
        int formatPosition = mFormatSpinner.getSelectedItemPosition();
        int resolutionPosition = mResolutionSpinner.getSelectedItemPosition();
        int frameratePosition = mFrameRateSpinner.getSelectedItemPosition();

        // 应用格式设置
        Size newSize = mFormatManager.getSelectedSize(formatPosition, resolutionPosition, frameratePosition);
        if (newSize != null) {
            mCurrentSize = newSize;
            mCameraManager.setPreviewSize(mCurrentSize);
        }

        saveCurrentConfig();
    }

    // 加载保存的配置
    private void loadSavedConfig() {
        int vendorId = mCameraManager.getCurrentVendorId();
        int productId = mCameraManager.getCurrentProductId();
        
        if (vendorId == -1 || productId == -1) {
            return;
        }
        
        JSONObject config = ConfigManager.loadConfig(mContext, vendorId, productId);
        if (config != null) {
            try {
                // 加载格式、分辨率、帧率
                mCurrentSize.type = config.optInt("format", mCurrentSize.type);
                mCurrentSize.width = config.optInt("width", mCurrentSize.width);
                mCurrentSize.height = config.optInt("height", mCurrentSize.height);
                mCurrentSize.fps = config.optInt("fps", mCurrentSize.fps);

                // 应用设置
                mCameraManager.setPreviewSize(mCurrentSize);

                // 加载其他参数
                mExposureTimeInput.setText(String.valueOf(config.optInt("exposure")));
                mGainInput.setText(String.valueOf(config.optInt("gain")));
                mTriggerPeriodInput.setText(String.valueOf(config.optInt("triggerPeriod")));
                mSerialCommandInput1.setText(config.optString("serial1"));
                mSerialCommandInput2.setText(config.optString("serial2"));

                Log.d(TAG, "Loaded saved config for device");
            } catch (Exception e) {
                Log.e(TAG, "Error applying saved config", e);
            }
        }
    }

    // 保存当前配置
    public void saveCurrentConfig() {
        int vendorId = mCameraManager.getCurrentVendorId();
        int productId = mCameraManager.getCurrentProductId();

        if (vendorId == -1 || productId == -1) {
            return;
        }

        mCurrentSize = mFormatManager.getCurrentSelectedSize();

        int exposure = 0;
        try {
            exposure = Integer.parseInt(mExposureTimeInput.getText().toString());
        } catch (NumberFormatException e) {
            // 忽略错误
        }

        int gain = 0;
        try {
            gain = Integer.parseInt(mGainInput.getText().toString());
        } catch (NumberFormatException e) {
            // 忽略错误
        }

        int triggerPeriod = 0;
        try {
            triggerPeriod = Integer.parseInt(mTriggerPeriodInput.getText().toString());
        } catch (NumberFormatException e) {
            // 忽略错误
        }

        // 增加模式状态
        int isAutoExposure = mIsAutoExposure ? 1 : 0;
        int isColorMode = mIsColorMode ? 1 : 0;

        // 修改CameraConfig.java添加这两个参数
        ConfigManager.saveConfig(mContext, vendorId, productId,
                mCurrentSize.type, mCurrentSize.width, mCurrentSize.height, mCurrentSize.fps,
                exposure, gain, triggerPeriod,
                mSerialCommandInput1.getText().toString(),
                mSerialCommandInput2.getText().toString(),
                isAutoExposure, isColorMode);
    }

    public void updateParamUIFromConfig() {
        // 获取当前设备信息
        int vendorId = mCameraManager.getCurrentVendorId();
        int productId = mCameraManager.getCurrentProductId();

        if (vendorId == -1 || productId == -1 || !mCameraManager.isCameraOpened()) {
            Log.d(TAG, "相机未打开或设备ID无效，无法更新UI");
            return;
        }

        // 从配置文件加载参数
        JSONObject config = ConfigManager.loadConfig(mContext, vendorId, productId);
        if (config != null) {
            // 更新UI控件
            try {
                // 曝光值
                int exposure = config.optInt("exposure", 0);
                if (exposure > 0) {
                    mExposureTimeInput.setText(String.valueOf(exposure));
                }

                // 增益值
                int gain = config.optInt("gain", 0);
                if (gain > 0) {
                    mGainInput.setText(String.valueOf(gain));
                }

                // 触发周期
                int triggerPeriod = config.optInt("triggerPeriod", 0);
                if (triggerPeriod > 0) {
                    mTriggerPeriodInput.setText(String.valueOf(triggerPeriod));
                }

                // 串口指令
                mSerialCommandInput1.setText(config.optString("serial1", ""));
                mSerialCommandInput2.setText(config.optString("serial2", ""));

                // 自动曝光状态
                boolean isAutoExposure = config.optInt("isAutoExposure", 1) == 1;
                mIsAutoExposure = isAutoExposure;
                mAutoExposureButton.setText(isAutoExposure ? "关闭自动曝光" : "开启自动曝光");
                mExposureTimeInput.setEnabled(!isAutoExposure);
                mGainInput.setEnabled(!isAutoExposure);

                // 彩色模式状态
                boolean isColorMode = config.optInt("isColorMode", 1) == 1;
                mIsColorMode = isColorMode;
                if (mColorModeButton != null) {
                    mColorModeButton.setText(isColorMode ? "黑白模式" : "彩色模式");
                }

                Log.d(TAG, "已从配置更新参数UI");
            } catch (Exception e) {
                Log.e(TAG, "更新参数UI失败", e);
            }
        } else {
            Log.d(TAG, "没有找到保存的配置，使用默认设置");

            // 默认设置
            mIsAutoExposure = true;
            mAutoExposureButton.setText("自动曝光");
            mExposureTimeInput.setEnabled(false);
            mGainInput.setEnabled(false);

            mIsColorMode = true;
            if (mColorModeButton != null) {
                mColorModeButton.setText("彩色模式");
            }
        }

        // 同时更新曝光和增益范围
        updateCameraParameters();
    }

    private void showToast(String message) {
        if (mContext != null) {
            Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
        }
    }
}
