package com.stars.uvccam;

import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import org.json.JSONObject;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.herohan.uvcapp.CameraHelper;
import com.herohan.uvcapp.ICameraHelper;
import com.serenegiant.usb.Format;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.UVCControl;
import com.serenegiant.widget.AspectRatioSurfaceView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, View.OnFocusChangeListener {

    private static final boolean DEBUG = true;
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String RESOLUTION_SEPARATOR = "x";

    private int mCurrentVendorId = -1;
    private int mCurrentProductId = -1;

    private int mPreviewWidth = UVCCamera.DEFAULT_PREVIEW_WIDTH;
    private int mPreviewHeight = UVCCamera.DEFAULT_PREVIEW_WIDTH;
    private int mPreviewFormat = UVCCamera.UVC_VS_FRAME_MJPEG;
    private int mPreviewFps = UVCCamera.DEFAULT_PREVIEW_FPS;

    private ICameraHelper mCameraHelper;
    private AspectRatioSurfaceView mCameraPreview;

    // UI Components
    private Button mOpenCameraButton;
    private Button mCloseCameraButton;
    private EditText mExposureTimeInput;
    private EditText mGainInput;
    private Spinner mFormatSpinner;
    private Spinner mResolutionSpinner;
    private Spinner mFrameRateSpinner;
    private Button mTestButton1;
    private Button mTestButton2;
    private Button mTestButtonMain;
    private EditText mTriggerPeriodInput;
    private EditText mSerialCommandInput1;
    private EditText mSerialCommandInput2;

    // Video format data structures
    private List<Format> mFormatList = new ArrayList<>();
    private List<Integer> mTypeList = new ArrayList<>();
    private LinkedHashMap<String, List<Integer>> mResolutionMap = new LinkedHashMap<>();
    private List<String> mResolutionList = new ArrayList<>();
    private List<Integer> mFrameRateList = new ArrayList<>();
    private LinkedHashMap<Integer, String> mTypeAndNameMap = new LinkedHashMap<>();
    private LinkedHashMap<Integer, LinkedHashMap<String, List<Integer>>> mTypeAndResolutionMap = new LinkedHashMap<>();

    // Current selected format data
    private Size mCurrentSize = new Size(mPreviewFormat, mPreviewWidth, mPreviewHeight, mPreviewFps, mFrameRateList);

    private ArrayAdapter<String> mFormatAdapter;
    private ArrayAdapter<String> mResolutionAdapter;
    private ArrayAdapter<String> mFrameRateAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("相机控制");

        initViews();
    }

    @Override
    protected void onStart() {
        super.onStart();
        initCameraHelper();
    }

    @Override
    protected void onStop() {
        saveCurrentConfig();
        super.onStop();
        clearCameraHelper();
    }

    private void initViews() {
        // 初始化相机预览区域
        mCameraPreview = findViewById(R.id.camera_preview);
        mCameraPreview.setAspectRatio(mPreviewWidth, mPreviewHeight);

        mCameraPreview.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                if (mCameraHelper != null) {
                    mCameraHelper.addSurface(holder.getSurface(), false);
                }
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                // Do nothing
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                if (mCameraHelper != null) {
                    mCameraHelper.removeSurface(holder.getSurface());
                }
            }
        });

        // 初始化控制按钮和输入框
        mOpenCameraButton = findViewById(R.id.open_camera_button);
        mCloseCameraButton = findViewById(R.id.close_camera_button);
        mExposureTimeInput = findViewById(R.id.exposure_time_input);
        mGainInput = findViewById(R.id.gain_input);
        mFormatSpinner = findViewById(R.id.format_spinner);
        mResolutionSpinner = findViewById(R.id.resolution_spinner);
        mFrameRateSpinner = findViewById(R.id.framerate_spinner);
        mTestButton1 = findViewById(R.id.test_button_1);
        mTestButton2 = findViewById(R.id.test_button_2);
        mTestButtonMain = findViewById(R.id.test_button_main);
        mTriggerPeriodInput = findViewById(R.id.trigger_period_input);
        mSerialCommandInput1 = findViewById(R.id.serial_command_input1);
        mSerialCommandInput2 = findViewById(R.id.serial_command_input2);

        // 设置点击监听器
        mOpenCameraButton.setOnClickListener(this);
        mCloseCameraButton.setOnClickListener(this);
        mTestButton1.setOnClickListener(this);
        mTestButton2.setOnClickListener(this);
        mTestButtonMain.setOnClickListener(this);
        mExposureTimeInput.setOnFocusChangeListener(this);
        mGainInput.setOnFocusChangeListener(this);
        mTriggerPeriodInput.setOnFocusChangeListener(this);

        // 初始化格式下拉框
        mFormatAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        mFormatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mFormatSpinner.setAdapter(mFormatAdapter);
        mFormatSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (mCameraHelper != null && mCameraHelper.isCameraOpened()) {
                    int selectedType = mTypeList.get(position);
                    if (mCurrentSize.type != selectedType) {
                        mCurrentSize.type = selectedType;
                        applySelectedFormat();
                        refreshResolutionSpinner();
                        refreshFrameRateSpinner();
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        // 初始化分辨率下拉框
        mResolutionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        mResolutionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mResolutionSpinner.setAdapter(mResolutionAdapter);
        mResolutionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (mCameraHelper != null && mCameraHelper.isCameraOpened() && !mResolutionList.isEmpty()) {
                    String[] resolutions = mResolutionList.get(position).split(RESOLUTION_SEPARATOR);
                    int width = Integer.parseInt(resolutions[0]);
                    int height = Integer.parseInt(resolutions[1]);
                    if (mCurrentSize.width != width || mCurrentSize.height != height) {
                        mCurrentSize.width = width;
                        mCurrentSize.height = height;
                        applySelectedFormat();
                        refreshFrameRateSpinner();
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        // 初始化帧率下拉框
        mFrameRateAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        mFrameRateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mFrameRateSpinner.setAdapter(mFrameRateAdapter);
        mFrameRateSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (mCameraHelper != null && mCameraHelper.isCameraOpened() && !mFrameRateList.isEmpty()) {
                    int fps = mFrameRateList.get(position);
                    if (mCurrentSize.fps != fps) {
                        mCurrentSize.fps = fps;
                        applySelectedFormat();
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        // 禁用控制项，直到相机打开
        setControlsEnabled(false);
    }

    private void setControlsEnabled(boolean enabled) {
        mCloseCameraButton.setEnabled(enabled);
        mExposureTimeInput.setEnabled(enabled);
        mGainInput.setEnabled(enabled);
        mFormatSpinner.setEnabled(enabled);
        mResolutionSpinner.setEnabled(enabled);
        mFrameRateSpinner.setEnabled(enabled);
        mTestButton1.setEnabled(enabled);
        mTestButton2.setEnabled(enabled);
        mTestButtonMain.setEnabled(enabled);
        mTriggerPeriodInput.setEnabled(enabled);
        mSerialCommandInput1.setEnabled(enabled);
        mSerialCommandInput2.setEnabled(enabled);

        mOpenCameraButton.setEnabled(!enabled);
    }

    public void initCameraHelper() {
        if (DEBUG) Log.d(TAG, "initCameraHelper:");
        if (mCameraHelper == null) {
            mCameraHelper = new CameraHelper();
            mCameraHelper.setStateCallback(mStateListener);
        }
    }

    private void clearCameraHelper() {
        if (DEBUG) Log.d(TAG, "clearCameraHelper:");
        if (mCameraHelper != null) {
            mCameraHelper.release();
            mCameraHelper = null;
        }
    }

    private void selectDevice(final UsbDevice device) {
        if (DEBUG) Log.v(TAG, "selectDevice:device=" + device.getDeviceName());
        mCameraHelper.selectDevice(device);
    }

    private final ICameraHelper.StateCallback mStateListener = new ICameraHelper.StateCallback() {
        @Override
        public void onAttach(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onAttach:");
            selectDevice(device);
        }

        @Override
        public void onDeviceOpen(UsbDevice device, boolean isFirstOpen) {
            if (DEBUG) Log.v(TAG, "onDeviceOpen:");
            mCameraHelper.openCamera();
        }

        @Override
        public void onCameraOpen(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onCameraOpen:");

            mCurrentVendorId = device.getVendorId();
            mCurrentProductId = device.getProductId();

            // 获取当前的预览尺寸
            Size size = mCameraHelper.getPreviewSize();
            if (size != null) {
                mCurrentSize.width = size.width;
                mCurrentSize.height = size.height;
                mCurrentSize.fps = size.fps;
                mCurrentSize.type = size.type;

                loadSavedConfig();

                // 更新预览尺寸到SurfaceView
                resizePreviewView(size);
            }

            // 更新相机参数
            updateCameraParameters();

            // 填充格式下拉框
            fetchVideoFormatData();
            showAllSpinners();

            // 启动预览
            mCameraHelper.startPreview();

            // 添加预览Surface
            mCameraHelper.addSurface(mCameraPreview.getHolder().getSurface(), false);

            // 更新菜单
            invalidateOptionsMenu();

            // 启用控制项
            runOnUiThread(() -> setControlsEnabled(true));
        }

        @Override
        public void onCameraClose(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onCameraClose:");

            if (mCameraHelper != null) {
                mCameraHelper.removeSurface(mCameraPreview.getHolder().getSurface());
            }

            saveCurrentConfig();

            // 更新菜单
            invalidateOptionsMenu();


            // 禁用控制项
            runOnUiThread(() -> setControlsEnabled(false));
        }

        @Override
        public void onDeviceClose(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onDeviceClose:");
        }

        @Override
        public void onDetach(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onDetach:");
        }

        @Override
        public void onCancel(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onCancel:");
        }
    };

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.open_camera_button) {
            // 选择USB相机设备
            if (mCameraHelper != null) {
                final List<UsbDevice> list = mCameraHelper.getDeviceList();
                if (list != null && list.size() > 0) {
                    mCameraHelper.selectDevice(list.get(0));
                } else {
                    Toast.makeText(this, "没有找到相机设备", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (id == R.id.close_camera_button) {
            // 关闭相机
            if (mCameraHelper != null) {
                mCameraHelper.closeCamera();
            }
        } else if (id == R.id.test_button_1) {
            if (mCameraHelper != null && mCameraHelper.isCameraOpened()) {
                // 应用当前选择的格式、分辨率和帧率
                applySelectedFormat();
            }
        } else if (id == R.id.test_button_2) {
            Toast.makeText(this, "测试按钮 2 点击", Toast.LENGTH_SHORT).show();
            // 在这里添加测试按钮2的逻辑
        } else if (id == R.id.test_button_main) {
            Toast.makeText(this, "主测试按钮 点击", Toast.LENGTH_SHORT).show();
            // 在这里添加主测试按钮的逻辑
        }
    }

    public void onFocusChange(View v, boolean hasFocus) {
        if (v.getId() == R.id.exposure_time_input && !hasFocus) {
            // Exposure time EditText lost focus, apply the setting
            applyExposureSetting();
        } else if (v.getId() == R.id.gain_input && !hasFocus) {
            // Gain EditText lost focus, apply the setting
            applyGainSetting();
        }else if (v.getId() == R.id.trigger_period_input && !hasFocus){
            applyTriggerPeriodSetting();
        }
    }

    private void applyExposureSetting() {
        if (mCameraHelper != null && mCameraHelper.isCameraOpened()) {
            UVCControl control = mCameraHelper.getUVCControl();
            if (control == null || !control.isExposureTimeAbsoluteEnable()) {
                Toast.makeText(this, "曝光时间调整不支持", Toast.LENGTH_SHORT).show();
                return;
            }

            String exposureText = mExposureTimeInput.getText().toString();
            if (!TextUtils.isEmpty(exposureText)) {
                try {
                    int exposureTime = Integer.parseInt(exposureText);
                    int[] limits = control.updateExposureTimeAbsoluteLimit();

                    // 验证范围
                    if (exposureTime < limits[0] || exposureTime > limits[1]) {
                        Toast.makeText(this,
                                String.format("曝光时间需在%d-%d之间", limits[0], limits[1]),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    control.setExposureTimeAbsolute(exposureTime);
                    Toast.makeText(this, "曝光时间已设置为: " + exposureTime + " us", Toast.LENGTH_SHORT).show();

                } catch (NumberFormatException e) {
                    Toast.makeText(this, "请输入有效的曝光时间数值", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Toast.makeText(this, "请先打开相机", Toast.LENGTH_SHORT).show();
        }
        saveCurrentConfig();
    }

    private void applyGainSetting() {
        if (mCameraHelper != null && mCameraHelper.isCameraOpened()) {
            UVCControl control = mCameraHelper.getUVCControl();
            if (control == null || !control.isGainEnable()) {
                Toast.makeText(this, "增益调整不支持", Toast.LENGTH_SHORT).show();
                return;
            }

            String gainText = mGainInput.getText().toString();
            if (!TextUtils.isEmpty(gainText)) {
                try {
                    int gain = Integer.parseInt(gainText);
                    int[] limits = control.updateGainLimit();

                    // 验证范围
                    if (gain < limits[0] || gain > limits[1]) {
                        Toast.makeText(this,
                                String.format("增益值需在%d-%d之间", limits[0], limits[1]),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    control.setGain(gain);
                    Toast.makeText(this, "增益值已设置为: " + gain, Toast.LENGTH_SHORT).show();

                } catch (NumberFormatException e) {
                    Toast.makeText(this, "请输入有效的增益值数值", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Toast.makeText(this, "请先打开相机", Toast.LENGTH_SHORT).show();
        }
        saveCurrentConfig();
    }

    private void applyTriggerPeriodSetting() {
        if (mCameraHelper != null && mCameraHelper.isCameraOpened()) {
            try {
                int triggerPeriod = Integer.parseInt(mTriggerPeriodInput.getText().toString());
                // 这里添加实际控制相机的代码
                Toast.makeText(this, "触发周期已设置为: " + triggerPeriod + " ms",
                        Toast.LENGTH_SHORT).show();
                saveCurrentConfig(); // 保存设置
            } catch (NumberFormatException e) {
                Toast.makeText(this, "请输入有效的触发周期数值", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "请先打开相机", Toast.LENGTH_SHORT).show();
        }
    }

    private void resizePreviewView(Size size) {
        // 更新预览尺寸
        mPreviewWidth = size.width;
        mPreviewHeight = size.height;
        // 设置SurfaceView的宽高比，匹配相机的宽高比
        mCameraPreview.setAspectRatio(mPreviewWidth, mPreviewHeight);
    }

    private void updateCameraParameters() {
        if (mCameraHelper == null || !mCameraHelper.isCameraOpened()) {
            return;
        }

        UVCControl control = mCameraHelper.getUVCControl();
        if (control == null) {
            return;
        }

        runOnUiThread(() -> {
            try {
                // 曝光时间
                if (control.isExposureTimeAbsoluteEnable()) {
                    int[] exposureLimits = control.updateExposureTimeAbsoluteLimit();
                    int exposureValue = control.getExposureTimeAbsolute();
                    mExposureTimeInput.setText(String.valueOf(exposureValue));
                    mExposureTimeInput.setHint(String.format("范围: %d-%d",
                            exposureLimits[0], exposureLimits[1]));
                    mExposureTimeInput.setEnabled(true);
                } else {
                    mExposureTimeInput.setEnabled(false);
                    mExposureTimeInput.setHint("不支持曝光调整");
                }

                // 增益值
                if (control.isGainEnable()) {
                    int[] gainLimits = control.updateGainLimit();
                    int gainValue = control.getGain();
                    mGainInput.setText(String.valueOf(gainValue));
                    mGainInput.setHint(String.format("范围: %d-%d",
                            gainLimits[0], gainLimits[1]));
                    mGainInput.setEnabled(true);
                } else {
                    mGainInput.setEnabled(false);
                    mGainInput.setHint("不支持增益调整");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating camera parameters", e);
            }
        });
        saveCurrentConfig();

    }

    private void fetchVideoFormatData() {
        if (mCameraHelper == null || !mCameraHelper.isCameraOpened()) {
            return;
        }

        // 清除旧数据
        mTypeAndNameMap.clear();
        mTypeAndResolutionMap.clear();

        // 获取支持的格式列表
        mFormatList = mCameraHelper.getSupportedFormatList();

        // 处理格式数据
        for (Format format : mFormatList) {
            if (format.type == UVCCamera.UVC_VS_FORMAT_UNCOMPRESSED) {
                int type = UVCCamera.UVC_VS_FRAME_UNCOMPRESSED;
                mTypeAndNameMap.put(type, getString(R.string.video_format_format_yuv));
                mTypeAndResolutionMap.put(type, new LinkedHashMap<String, List<Integer>>());
            } else if (format.type == UVCCamera.UVC_VS_FORMAT_MJPEG) {
                int type = UVCCamera.UVC_VS_FRAME_MJPEG;
                mTypeAndNameMap.put(type, getString(R.string.video_format_format_mjpeg));
                mTypeAndResolutionMap.put(type, new LinkedHashMap<String, List<Integer>>());
            }

            // 处理每种格式的帧描述符
            for (Format.Descriptor descriptor : format.frameDescriptors) {
                LinkedHashMap<String, List<Integer>> resolutionAndFpsMap = mTypeAndResolutionMap.get(descriptor.type);
                if (resolutionAndFpsMap != null) {
                    String resolution = descriptor.width + RESOLUTION_SEPARATOR + descriptor.height;
                    List<Integer> fpsList = resolutionAndFpsMap.get(resolution);

                    if (fpsList == null) {
                        fpsList = new ArrayList<>();
                        resolutionAndFpsMap.put(resolution, fpsList);
                    }

                    // 添加帧率列表
                    for (Format.Interval interval : descriptor.intervals) {
                        if (!fpsList.contains(interval.fps)) {
                            fpsList.add(interval.fps);
                        }
                    }
                }
            }
        }
    }

    private void showAllSpinners() {
        // 在UI线程上更新下拉框
        runOnUiThread(() -> {
            // 更新格式下拉框
            refreshFormatSpinner();

            // 更新分辨率下拉框
            refreshResolutionSpinner();

            // 更新帧率下拉框
            refreshFrameRateSpinner();
        });
    }

    private void refreshFormatSpinner() {
        // 更新格式下拉框的数据
        mTypeList = new ArrayList<>(mTypeAndNameMap.keySet());
        List<String> formatTextList = new ArrayList<>(mTypeAndNameMap.values());

        mFormatAdapter.clear();
        mFormatAdapter.addAll(formatTextList);
        mFormatAdapter.notifyDataSetChanged();

        // 设置当前选中项
        int index = mTypeList.indexOf(mCurrentSize.type);
        if (index == -1 && !mTypeList.isEmpty()) {
            index = 0;
            mCurrentSize.type = mTypeList.get(0);
        }

        if (index >= 0) {
            mFormatSpinner.setSelection(index);
        }
    }

    private void refreshResolutionSpinner() {
        // 获取当前格式对应的分辨率映射
        mResolutionMap = mTypeAndResolutionMap.get(mCurrentSize.type);
        if (mResolutionMap == null) {
            mResolutionMap = new LinkedHashMap<>();
        }

        // 更新分辨率列表
        mResolutionList = new ArrayList<>(mResolutionMap.keySet());

        mResolutionAdapter.clear();
        mResolutionAdapter.addAll(mResolutionList);
        mResolutionAdapter.notifyDataSetChanged();

        // 设置当前选中项
        String resolution = mCurrentSize.width + RESOLUTION_SEPARATOR + mCurrentSize.height;
        int index = mResolutionList.indexOf(resolution);

        if (index == -1 && !mResolutionList.isEmpty()) {
            index = 0;
            String[] resolutions = mResolutionList.get(0).split(RESOLUTION_SEPARATOR);
            mCurrentSize.width = Integer.parseInt(resolutions[0]);
            mCurrentSize.height = Integer.parseInt(resolutions[1]);
        }

        if (index >= 0) {
            mResolutionSpinner.setSelection(index);
        }
    }

    private void refreshFrameRateSpinner() {
        // 获取当前分辨率对应的帧率列表
        String resolution = mCurrentSize.width + RESOLUTION_SEPARATOR + mCurrentSize.height;
        mFrameRateList = mResolutionMap.get(resolution);

        if (mFrameRateList == null) {
            mFrameRateList = new ArrayList<>();
        }

        mFrameRateAdapter.clear();
        for (Integer fps : mFrameRateList) {
            mFrameRateAdapter.add(fps + " fps");
        }
        mFrameRateAdapter.notifyDataSetChanged();

        // 设置当前选中项
        int index = mFrameRateList.indexOf(mCurrentSize.fps);

        if (index == -1 && !mFrameRateList.isEmpty()) {
            index = 0;
            mCurrentSize.fps = mFrameRateList.get(0);
        }

        if (index >= 0) {
            mFrameRateSpinner.setSelection(index);
        }
        saveCurrentConfig();
    }

    private void applySelectedFormat() {
        if (mCameraHelper != null && mCameraHelper.isCameraOpened()) {
            try {
                // 停止预览
                mCameraHelper.stopPreview();

                // 应用新的格式设置
                mCameraHelper.setPreviewSize(mCurrentSize);

                // 调整预览视图的大小
                resizePreviewView(mCurrentSize);

                // 重新开始预览
                mCameraHelper.startPreview();

                Toast.makeText(this, String.format("已应用格式: %s, %dx%d, %dfps",
                                mTypeAndNameMap.get(mCurrentSize.type),
                                mCurrentSize.width, mCurrentSize.height, mCurrentSize.fps),
                        Toast.LENGTH_SHORT).show();

            } catch (Exception e) {
                Log.e(TAG, "Error applying format", e);
                Toast.makeText(this, "应用格式失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();

                // 重新启动预览
                try {
                    mCameraHelper.startPreview();
                } catch (Exception ex) {
                    Log.e(TAG, "Error restarting preview", ex);
                }
            }
        } else {
            Toast.makeText(this, "请先打开相机", Toast.LENGTH_SHORT).show();
        }
        saveCurrentConfig();
    }

    private void loadSavedConfig() {
        JSONObject config = CameraConfig.loadConfig(this, mCurrentVendorId, mCurrentProductId);
        if (config != null) {
            try {
                // 加载格式、分辨率、帧率
                mCurrentSize.type = config.optInt("format", mCurrentSize.type);
                mCurrentSize.width = config.optInt("width", mCurrentSize.width);
                mCurrentSize.height = config.optInt("height", mCurrentSize.height);
                mCurrentSize.fps = config.optInt("fps", mCurrentSize.fps);

                // 应用设置
                if (mCameraHelper != null) {
                    mCameraHelper.setPreviewSize(mCurrentSize);
                }

                // 加载其他参数
                runOnUiThread(() -> {
                    mExposureTimeInput.setText(String.valueOf(config.optInt("exposure")));
                    mGainInput.setText(String.valueOf(config.optInt("gain")));
                    mTriggerPeriodInput.setText(String.valueOf(config.optInt("triggerPeriod")));
                    mSerialCommandInput1.setText(config.optString("serial1"));
                    mSerialCommandInput2.setText(config.optString("serial2"));
                });

                Log.d(TAG, "Loaded saved config for device");
            } catch (Exception e) {
                Log.e(TAG, "Error applying saved config", e);
            }
        }
    }

    // 添加保存配置的方法
    private void saveCurrentConfig() {
        if (mCurrentVendorId == -1 || mCurrentProductId == -1) return;

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

        CameraConfig.saveConfig(this, mCurrentVendorId, mCurrentProductId,
                mCurrentSize.type, mCurrentSize.width, mCurrentSize.height, mCurrentSize.fps,
                exposure, gain, triggerPeriod,
                mSerialCommandInput1.getText().toString(),
                mSerialCommandInput2.getText().toString());
    }
}