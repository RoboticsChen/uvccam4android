package com.stars.uvccam;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.serenegiant.widget.AspectRatioSurfaceView;
import android.widget.FrameLayout;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.List;
import android.hardware.usb.UsbDevice;
import org.json.JSONObject;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.UVCCamera;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String ADMIN_PASSWORD = "3807";
    private static final int REQUEST_STORAGE_PERMISSION = 101;

    // UI组件
    private AspectRatioSurfaceView mCameraPreview;
    private FrameLayout mControlPanelContainer;
    private View mMainControlPanelView;
    private View mParamSettingView;

    // 管理器
    private CameraManager mCameraManager;
    private UIManager mUIManager;
    private FormatManager mFormatManager;
    private SettingsManager mSettingsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("相机控制");

        checkAndRequestPermissions();
        initViews();

        // 初始化管理器
        mCameraManager = new CameraManager(this);
        mFormatManager = new FormatManager(this);
        mSettingsManager = new SettingsManager(this, mCameraManager, mFormatManager);
        mUIManager = new UIManager(this, mCameraManager, mFormatManager, mSettingsManager);

        setupCameraPreview();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (mCameraManager != null) {
            mCameraManager.initialize();

            // 如果相机已连接，尝试获取设备ID并加载配置
            List<UsbDevice> devices = mCameraManager.getDeviceList();
            if (devices != null && !devices.isEmpty()) {
                UsbDevice device = devices.get(0);
                int vendorId = device.getVendorId();
                int productId = device.getProductId();

                JSONObject config = ConfigManager.loadConfig(this, vendorId, productId);
                if (config != null) {
                    int format = config.optInt("format", UVCCamera.UVC_VS_FRAME_MJPEG);
                    int width = config.optInt("width", UVCCamera.DEFAULT_PREVIEW_WIDTH);
                    int height = config.optInt("height", UVCCamera.DEFAULT_PREVIEW_HEIGHT);
                    int fps = config.optInt("fps", UVCCamera.DEFAULT_PREVIEW_FPS);

                    Size savedSize = new Size(format, width, height, fps, null);
                    mCameraManager.setDefaultPreviewSize(savedSize);

                    if (mCameraPreview != null) {
                        mCameraPreview.setAspectRatio(width, height);
                    }
                }
            }
        }
    }

    @Override
    protected void onStop() {
        if (mSettingsManager != null) {
            mSettingsManager.saveCurrentConfig();
        }

        if (mCameraManager != null) {
            mCameraManager.release();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (mCameraManager != null) {
            mCameraManager.release();
            mCameraManager = null;
        }
        super.onDestroy();
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[] {
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                }, REQUEST_STORAGE_PERMISSION);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "存储权限已授予，可以保存图像", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "存储权限被拒绝，无法保存图像", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void initViews() {
        // 初始化相机预览
        mCameraPreview = findViewById(R.id.camera_preview);
        mControlPanelContainer = findViewById(R.id.control_panel_container);

        // 加载控制面板
        LayoutInflater inflater = LayoutInflater.from(this);
        mMainControlPanelView = inflater.inflate(R.layout.main_control_panel, mControlPanelContainer, false);
        mParamSettingView = inflater.inflate(R.layout.param_setting_panel, mControlPanelContainer, false);

        // 默认显示主控制面板
        mControlPanelContainer.addView(mMainControlPanelView);
    }

    private void setupCameraPreview() {
        if (mCameraPreview != null) {
            mCameraPreview.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(@NonNull SurfaceHolder holder) {
                    if (mCameraManager != null) {
                        mCameraManager.addSurface(holder.getSurface());
                    }
                }

                @Override
                public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                    // 不需要处理
                }

                @Override
                public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                    if (mCameraManager != null) {
                        mCameraManager.removeSurface(holder.getSurface());
                    }
                }
            });
        }
    }

    @Override
    public void onClick(View v) {
        if (v == null) return;

        int id = v.getId();

        if (id == R.id.open_camera_button) {
            if (mCameraManager != null) mCameraManager.openCamera();
        } else if (id == R.id.close_camera_button) {
            if (mCameraManager != null) mCameraManager.closeCamera();
        } else if (id == R.id.open_test_1_button) {
            Toast.makeText(this, "开始测试 1", Toast.LENGTH_SHORT).show();
            // 在这里添加测试1的逻辑
        } else if (id == R.id.close_test_1_button) {
            Toast.makeText(this, "关闭测试 1", Toast.LENGTH_SHORT).show();
            // 在这里添加关闭测试1的逻辑
        } else if (id == R.id.open_test_2_button) {
            Toast.makeText(this, "开始测试 2", Toast.LENGTH_SHORT).show();
            // 在这里添加测试2的逻辑
        } else if (id == R.id.close_test_2_button) {
            Toast.makeText(this, "关闭测试 2", Toast.LENGTH_SHORT).show();
            // 在这里添加关闭测试2的逻辑
        } else if (id == R.id.open_test_main_button) {
            Toast.makeText(this, "开始主筛查", Toast.LENGTH_SHORT).show();
            // 在这里添加主筛查的逻辑
        } else if (id == R.id.close_test_main_button) {
            Toast.makeText(this, "关闭主筛查", Toast.LENGTH_SHORT).show();
            // 在这里添加关闭主筛查的逻辑
        } else if (id == R.id.capture) {
            if (mCameraManager != null) mCameraManager.captureImage();
        } else if (id == R.id.button_open_settings) {
            showPasswordDialog();
        } else if (id == R.id.auto_exposure) {
            if (mSettingsManager != null) mSettingsManager.toggleAutoExposure();
        } else if (id == R.id.color_mode) {
            if (mSettingsManager != null) mSettingsManager.toggleColorMode();
        } else if (id == R.id.button_cancel_settings) {
            if (mUIManager != null) mUIManager.showMainControlPanel();
        } else if (id == R.id.button_save_settings) {
            if (mSettingsManager != null) {
                mSettingsManager.saveParamsFromUI();
                mUIManager.showMainControlPanel();
            }
        }
    }

    private void showPasswordDialog() {
        Dialog dialog = new Dialog(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.password_dialog_layout, null);
        dialog.setContentView(dialogView);

        EditText passwordInput = dialogView.findViewById(R.id.password_input);
        Button confirmButton = dialogView.findViewById(R.id.confirm_button);
        Button cancelButton = dialogView.findViewById(R.id.cancel_button);

        confirmButton.setOnClickListener(v -> {
            String password = passwordInput.getText().toString();
            if (ADMIN_PASSWORD.equals(password)) {
                dialog.dismiss();
                if (mUIManager != null) mUIManager.showParamSettingPanel();
            } else {
                Toast.makeText(this, "密码错误", Toast.LENGTH_SHORT).show();
            }
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    // 获取UI组件方法，供管理器使用
    public View getMainControlPanelView() {
        return mMainControlPanelView;
    }

    public View getParamSettingView() {
        return mParamSettingView;
    }

    public FrameLayout getControlPanelContainer() {
        return mControlPanelContainer;
    }

    public AspectRatioSurfaceView getCameraPreview() {
        return mCameraPreview;
    }
}