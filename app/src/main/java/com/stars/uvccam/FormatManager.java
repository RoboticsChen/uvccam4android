package com.stars.uvccam;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import com.serenegiant.usb.Format;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.widget.AspectRatioSurfaceView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class FormatManager {
    private static final String TAG = "FormatManager";
    private static final boolean DEBUG = true;
    private static final String RESOLUTION_SEPARATOR = "x";

    private Context mContext;
    
    // 视频格式数据结构
    private List<Format> mFormatList = new ArrayList<>();
    private List<Integer> mTypeList = new ArrayList<>();
    private LinkedHashMap<String, List<Integer>> mResolutionMap = new LinkedHashMap<>();
    private List<String> mResolutionList = new ArrayList<>();
    private List<Integer> mFrameRateList = new ArrayList<>();
    private LinkedHashMap<Integer, String> mTypeAndNameMap = new LinkedHashMap<>();
    private LinkedHashMap<Integer, LinkedHashMap<String, List<Integer>>> mTypeAndResolutionMap = new LinkedHashMap<>();

    // 适配器
    private ArrayAdapter<String> mFormatAdapter;
    private ArrayAdapter<String> mResolutionAdapter;
    private ArrayAdapter<String> mFrameRateAdapter;
    
    // 当前选中的格式
    private Size mCurrentSize = new Size(UVCCamera.UVC_VS_FRAME_MJPEG, 
            UVCCamera.DEFAULT_PREVIEW_WIDTH, 
            UVCCamera.DEFAULT_PREVIEW_HEIGHT, 
            UVCCamera.DEFAULT_PREVIEW_FPS, 
            null);
    
    public FormatManager(Context context) {
        mContext = context;
    }

    private void applyFormatChange(CameraManager cameraManager) {
        if (cameraManager != null && cameraManager.isCameraOpened()) {
            Log.d(TAG, "应用格式变更: " + mCurrentSize.type + ", " +
                    mCurrentSize.width + "x" + mCurrentSize.height + ", " + mCurrentSize.fps + "fps");

            // 传递变更请求给CameraManager
            cameraManager.setPreviewSize(mCurrentSize);
        }
    }
    
    public void initialize(View settingView, CameraManager cameraManager) {
        // 初始化UI组件
        Spinner formatSpinner = settingView.findViewById(R.id.format_spinner);
        Spinner resolutionSpinner = settingView.findViewById(R.id.resolution_spinner);
        Spinner frameRateSpinner = settingView.findViewById(R.id.framerate_spinner);
        
        // 初始化适配器
        mFormatAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item, new ArrayList<>());
        mFormatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        formatSpinner.setAdapter(mFormatAdapter);
        
        mResolutionAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item, new ArrayList<>());
        mResolutionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        resolutionSpinner.setAdapter(mResolutionAdapter);
        
        mFrameRateAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item, new ArrayList<>());
        mFrameRateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        frameRateSpinner.setAdapter(mFrameRateAdapter);

        
        // 设置选择监听器
        formatSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (cameraManager != null && cameraManager.isCameraOpened()) {
                    if (position >= 0 && position < mTypeList.size()) {
                        int selectedType = mTypeList.get(position);
                        if (mCurrentSize.type != selectedType) {
                            mCurrentSize.type = selectedType;
                            refreshResolutionSpinner();
                            refreshFrameRateSpinner();
                            applyFormatChange(cameraManager);
                        }
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 不做处理
            }
        });
        
        resolutionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (cameraManager != null && cameraManager.isCameraOpened() && !mResolutionList.isEmpty()) {
                    if (position >= 0 && position < mResolutionList.size()) {
                        String[] resolutions = mResolutionList.get(position).split(RESOLUTION_SEPARATOR);
                        int width = Integer.parseInt(resolutions[0]);
                        int height = Integer.parseInt(resolutions[1]);
                        if (mCurrentSize.width != width || mCurrentSize.height != height) {
                            mCurrentSize.width = width;
                            mCurrentSize.height = height;
                            refreshFrameRateSpinner();
                            applyFormatChange(cameraManager);
                        }
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 不做处理
            }
        });
        
        frameRateSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (cameraManager != null && cameraManager.isCameraOpened() && !mFrameRateList.isEmpty()) {
                    if (position >= 0 && position < mFrameRateList.size()) {
                        int fps = mFrameRateList.get(position);
                        if (mCurrentSize.fps != fps) {
                            mCurrentSize.fps = fps;
                            applyFormatChange(cameraManager);
                        }
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 不做处理
            }
        });
    }
    
    // 更新当前相机支持的视频格式
    public void updateFormats(CameraManager cameraManager) {
        if (cameraManager == null || !cameraManager.isCameraOpened()) {
            return;
        }
        
        // 获取当前预览尺寸
        Size currentSize = cameraManager.getPreviewSize();
        if (currentSize != null) {
            mCurrentSize = currentSize;
        }
        
        // 清除旧数据
        mTypeAndNameMap.clear();
        mTypeAndResolutionMap.clear();
        
        // 获取支持的格式列表
        mFormatList = cameraManager.getSupportedFormatList();
        if (mFormatList == null) {
            return;
        }
        
        // 处理格式数据
        for (Format format : mFormatList) {
            if (format.type == UVCCamera.UVC_VS_FORMAT_UNCOMPRESSED) {
                int type = UVCCamera.UVC_VS_FRAME_UNCOMPRESSED;
                mTypeAndNameMap.put(type, "YUV");
                mTypeAndResolutionMap.put(type, new LinkedHashMap<>());
            } else if (format.type == UVCCamera.UVC_VS_FORMAT_MJPEG) {
                int type = UVCCamera.UVC_VS_FRAME_MJPEG;
                mTypeAndNameMap.put(type, "MJPEG");
                mTypeAndResolutionMap.put(type, new LinkedHashMap<>());
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
        
        // 更新UI
        refreshFormatSpinner();
        refreshResolutionSpinner();
        refreshFrameRateSpinner();
    }
    
    // 刷新格式下拉框
    private void refreshFormatSpinner() {
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
        
        if (index >= 0 && index < mFormatAdapter.getCount()) {
            Spinner formatSpinner = ((MainActivity)mContext).getParamSettingView().findViewById(R.id.format_spinner);
            if (formatSpinner != null) {
                formatSpinner.setSelection(index);
            }
        }
    }
    
    // 刷新分辨率下拉框
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
        
        if (index >= 0 && index < mResolutionAdapter.getCount()) {
            Spinner resolutionSpinner = ((MainActivity)mContext).getParamSettingView().findViewById(R.id.resolution_spinner);
            if (resolutionSpinner != null) {
                resolutionSpinner.setSelection(index);
            }
        }
        
        // 更新相机预览大小
        MainActivity mainActivity = (MainActivity)mContext;
        AspectRatioSurfaceView cameraPreview = mainActivity.getCameraPreview();
        if (cameraPreview != null) {
            cameraPreview.setAspectRatio(mCurrentSize.width, mCurrentSize.height);
        }
    }
    
    // 刷新帧率下拉框
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
        
        if (index >= 0 && index < mFrameRateAdapter.getCount()) {
            Spinner frameRateSpinner = ((MainActivity)mContext).getParamSettingView().findViewById(R.id.framerate_spinner);
            if (frameRateSpinner != null) {
                frameRateSpinner.setSelection(index);
            }
        }
    }
    
    // 根据下拉框选择获取Size对象
    public Size getSelectedSize(int formatPosition, int resolutionPosition, int frameratePosition) {
        if (formatPosition < 0 || formatPosition >= mTypeList.size() ||
            resolutionPosition < 0 || resolutionPosition >= mResolutionList.size() ||
            frameratePosition < 0 || frameratePosition >= mFrameRateList.size()) {
            Log.e(TAG, "Invalid selection positions");
            return null;
        }
        
        int format = mTypeList.get(formatPosition);
        String[] resolutions = mResolutionList.get(resolutionPosition).split(RESOLUTION_SEPARATOR);
        int width = Integer.parseInt(resolutions[0]);
        int height = Integer.parseInt(resolutions[1]);
        int fps = mFrameRateList.get(frameratePosition);
        
        return new Size(format, width, height, fps, null);
    }
    
    // 获取当前选中的Size
    public Size getCurrentSize() {
        return mCurrentSize;
    }

    public Size getCurrentSelectedSize() {
        // 获取当前所选值
        Spinner formatSpinner = ((MainActivity)mContext).getParamSettingView().findViewById(R.id.format_spinner);
        Spinner resolutionSpinner = ((MainActivity)mContext).getParamSettingView().findViewById(R.id.resolution_spinner);
        Spinner frameRateSpinner = ((MainActivity)mContext).getParamSettingView().findViewById(R.id.framerate_spinner);

        if (formatSpinner == null || resolutionSpinner == null || frameRateSpinner == null) {
            Log.w(TAG, "无法获取下拉菜单控件，返回当前内存中的值");
            return mCurrentSize;
        }

        int formatPosition = formatSpinner.getSelectedItemPosition();
        int resolutionPosition = resolutionSpinner.getSelectedItemPosition();
        int frameratePosition = frameRateSpinner.getSelectedItemPosition();

        if (formatPosition < 0 || formatPosition >= mTypeList.size() ||
                resolutionPosition < 0 || resolutionPosition >= mResolutionList.size() ||
                frameratePosition < 0 || frameratePosition >= mFrameRateList.size()) {
            Log.w(TAG, "下拉菜单选择位置无效，返回当前内存中的值");
            return mCurrentSize;
        }

        int format = mTypeList.get(formatPosition);
        String[] resolutions = mResolutionList.get(resolutionPosition).split(RESOLUTION_SEPARATOR);
        int width = Integer.parseInt(resolutions[0]);
        int height = Integer.parseInt(resolutions[1]);
        int fps = mFrameRateList.get(frameratePosition);

        return new Size(format, width, height, fps, null);
    }
}
