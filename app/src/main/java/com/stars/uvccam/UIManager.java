package com.stars.uvccam;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import com.serenegiant.usb.Size;
import com.serenegiant.widget.AspectRatioSurfaceView;

public class UIManager implements CameraManager.CameraStateListener {
    private static final String TAG = "UIManager";

    private final Context mContext;
    private final MainActivity mMainActivity;
    private final CameraManager mCameraManager;
    private final FormatManager mFormatManager;
    private final SettingsManager mSettingsManager;

    // UI组件
    private final View mMainControlPanelView;
    private final View mParamSettingView;
    private final FrameLayout mControlPanelContainer;
    private final AspectRatioSurfaceView mCameraPreview;

    // 主面板控制按钮
    private Button mOpenCameraButton;
    private Button mCloseCameraButton;
    private Button mOpenTest1Button;
    private Button mCloseTest1Button;
    private Button mOpenTest2Button;
    private Button mCloseTest2Button;
    private Button mOpenTestMainButton;
    private Button mCloseTestMainButton;
    private Button mCaptureButton;
    private Button mOpenSettingsButton;

    public UIManager(Context context, CameraManager cameraManager, FormatManager formatManager, SettingsManager settingsManager) {
        mContext = context;
        mCameraManager = cameraManager;
        mFormatManager = formatManager;
        mSettingsManager = settingsManager;

        if (context instanceof MainActivity) {
            mMainActivity = (MainActivity) context;
            mMainControlPanelView = mMainActivity.getMainControlPanelView();
            mParamSettingView = mMainActivity.getParamSettingView();
            mControlPanelContainer = mMainActivity.getControlPanelContainer();
            mCameraPreview = mMainActivity.getCameraPreview();

            // 初始化UI组件
            initControlPanels();
        } else {
            mMainActivity = null;
            mMainControlPanelView = null;
            mParamSettingView = null;
            mControlPanelContainer = null;
            mCameraPreview = null;
        }

        // 注册相机状态监听
        cameraManager.setStateListener(this);
    }

    private void initControlPanels() {
        if (mMainActivity == null || mMainControlPanelView == null) return;

        // 获取主控制面板按钮
        mOpenCameraButton = mMainControlPanelView.findViewById(R.id.open_camera_button);
        mCloseCameraButton = mMainControlPanelView.findViewById(R.id.close_camera_button);
        mOpenTest1Button = mMainControlPanelView.findViewById(R.id.open_test_1_button);
        mCloseTest1Button = mMainControlPanelView.findViewById(R.id.close_test_1_button);
        mOpenTest2Button = mMainControlPanelView.findViewById(R.id.open_test_2_button);
        mCloseTest2Button = mMainControlPanelView.findViewById(R.id.close_test_2_button);
        mOpenTestMainButton = mMainControlPanelView.findViewById(R.id.open_test_main_button);
        mCloseTestMainButton = mMainControlPanelView.findViewById(R.id.close_test_main_button);
        mCaptureButton = mMainControlPanelView.findViewById(R.id.capture);
        mOpenSettingsButton = mMainControlPanelView.findViewById(R.id.button_open_settings);

        // 设置点击监听器
        if (mOpenCameraButton != null) mOpenCameraButton.setOnClickListener(mMainActivity);
        if (mCloseCameraButton != null) mCloseCameraButton.setOnClickListener(mMainActivity);
        if (mOpenTest1Button != null) mOpenTest1Button.setOnClickListener(mMainActivity);
        if (mCloseTest1Button != null) mCloseTest1Button.setOnClickListener(mMainActivity);
        if (mOpenTest2Button != null) mOpenTest2Button.setOnClickListener(mMainActivity);
        if (mCloseTest2Button != null) mCloseTest2Button.setOnClickListener(mMainActivity);
        if (mOpenTestMainButton != null) mOpenTestMainButton.setOnClickListener(mMainActivity);
        if (mCloseTestMainButton != null) mCloseTestMainButton.setOnClickListener(mMainActivity);
        if (mCaptureButton != null) mCaptureButton.setOnClickListener(mMainActivity);
        if (mOpenSettingsButton != null) mOpenSettingsButton.setOnClickListener(mMainActivity);

        // 禁用控制项，直到相机打开
        setControlsEnabled(false);

        // 初始化参数设置面板
        if (mParamSettingView != null) {
            Button cancelSettingsButton = mParamSettingView.findViewById(R.id.button_cancel_settings);
            Button saveSettingsButton = mParamSettingView.findViewById(R.id.button_save_settings);

            if (cancelSettingsButton != null) cancelSettingsButton.setOnClickListener(mMainActivity);
            if (saveSettingsButton != null) saveSettingsButton.setOnClickListener(mMainActivity);

            // 初始化设置管理器UI组件
            mSettingsManager.initUIComponents(mParamSettingView);

            // 初始化格式管理器
            mFormatManager.initialize(mParamSettingView, mCameraManager);
        }
    }

    public void showMainControlPanel() {
        if (mControlPanelContainer == null || mMainControlPanelView == null) return;

        mControlPanelContainer.removeAllViews();
        mControlPanelContainer.addView(mMainControlPanelView);

        // 重新绑定控制面板按钮（避免引用丢失）
        mOpenCameraButton = mMainControlPanelView.findViewById(R.id.open_camera_button);
        mCloseCameraButton = mMainControlPanelView.findViewById(R.id.close_camera_button);
        mOpenTest1Button = mMainControlPanelView.findViewById(R.id.open_test_1_button);
        mCloseTest1Button = mMainControlPanelView.findViewById(R.id.close_test_1_button);
        mOpenTest2Button = mMainControlPanelView.findViewById(R.id.open_test_2_button);
        mCloseTest2Button = mMainControlPanelView.findViewById(R.id.close_test_2_button);
        mOpenTestMainButton = mMainControlPanelView.findViewById(R.id.open_test_main_button);
        mCloseTestMainButton = mMainControlPanelView.findViewById(R.id.close_test_main_button);
        mCaptureButton = mMainControlPanelView.findViewById(R.id.capture);
        mOpenSettingsButton = mMainControlPanelView.findViewById(R.id.button_open_settings);

        if (mCameraManager != null && mCameraManager.isCameraOpened()) {
            // 重新加载并应用配置文件参数
            mCameraManager.reloadSavedParameters();
        }

        // 设置按钮状态
        boolean cameraOpened = mCameraManager != null && mCameraManager.isCameraOpened();
        setControlsEnabled(cameraOpened);
    }

    public void showParamSettingPanel() {
        if (mControlPanelContainer == null || mParamSettingView == null) return;

        mControlPanelContainer.removeAllViews();
        mControlPanelContainer.addView(mParamSettingView);

        // 重置UI组件
        mSettingsManager.initUIComponents(mParamSettingView);

        // 加载参数并更新UI控件
        mSettingsManager.updateParamUIFromConfig();

        // 更新格式下拉框
        mFormatManager.updateFormats(mCameraManager);
    }

    private void setControlsEnabled(boolean enabled) {
        if (mCloseCameraButton != null) mCloseCameraButton.setEnabled(enabled);
        if (mOpenTest1Button != null) mOpenTest1Button.setEnabled(enabled);
        if (mCloseTest1Button != null) mCloseTest1Button.setEnabled(enabled);
        if (mOpenTest2Button != null) mOpenTest2Button.setEnabled(enabled);
        if (mCloseTest2Button != null) mCloseTest2Button.setEnabled(enabled);
        if (mOpenTestMainButton != null) mOpenTestMainButton.setEnabled(enabled);
        if (mCloseTestMainButton != null) mCloseTestMainButton.setEnabled(enabled);
        if (mCaptureButton != null) mCaptureButton.setEnabled(enabled);
        if (mOpenSettingsButton != null) mOpenSettingsButton.setEnabled(enabled);

        if (mOpenCameraButton != null) mOpenCameraButton.setEnabled(!enabled);
    }

    @Override
    public void onCameraOpened(UsbDevice device, Size previewSize) {
        Log.d(TAG, "onCameraOpened: " + device.getDeviceName());

        if (mCameraPreview != null && previewSize != null) {
            mCameraPreview.setAspectRatio(previewSize.width, previewSize.height);
        }

        // 启用控制项
        if (mMainActivity != null) {
            mMainActivity.runOnUiThread(() -> setControlsEnabled(true));
        }
    }

    @Override
    public void onCameraClosed() {
        Log.d(TAG, "onCameraClosed");

        // 禁用控制项
        if (mMainActivity != null) {
            mMainActivity.runOnUiThread(() -> setControlsEnabled(false));
        }
    }

    @Override
    public void onDeviceAttached(UsbDevice device) {
        Log.d(TAG, "onDeviceAttached: " + device.getDeviceName());
        // 不需要处理
    }
}