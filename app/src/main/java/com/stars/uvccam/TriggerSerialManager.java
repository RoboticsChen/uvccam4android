package com.stars.uvccam;

import android.serialport.SerialPort;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 触发字符串口管理类
 * 用于定时发送触发字符串
 */
public class TriggerSerialManager {
    private static final String TAG = "TriggerSerial";

    // 默认发送内容和间隔
    private static final String DEFAULT_TRIGGER_DATA = "8 18 13 \r\n";
    private static final long DEFAULT_INTERVAL_MS = 50; // 50ms

    private SerialPort mSerialPort;
    private OutputStream mOutputStream;
    private ScheduledExecutorService mScheduledExecutor;
    private ScheduledFuture<?> mScheduledTask;
    private boolean isRunning = false;

    private String mDevicePath;
    private int mBaudRate;
    private String mTriggerData;
    private long mIntervalMs;

    // 回调接口
    private OnTriggerSendListener mSendListener;

    public interface OnTriggerSendListener {
        void onDataSent(String data);
        void onError(String error);
    }

    /**
     * 构造函数
     * @param devicePath 设备路径，如 "/dev/ttyUSB1"
     * @param baudRate 波特率，默认115200
     */
    public TriggerSerialManager(String devicePath, int baudRate) {
        this.mDevicePath = devicePath;
        this.mBaudRate = baudRate;
        this.mTriggerData = DEFAULT_TRIGGER_DATA;
        this.mIntervalMs = DEFAULT_INTERVAL_MS;
        this.mScheduledExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * 设置触发数据
     */
    public TriggerSerialManager setTriggerData(String triggerData) {
        this.mTriggerData = triggerData;
        return this;
    }

    /**
     * 设置发送间隔（毫秒）
     */
    public TriggerSerialManager setInterval(long intervalMs) {
        this.mIntervalMs = intervalMs;
        return this;
    }

    /**
     * 设置发送监听器
     */
    public void setOnTriggerSendListener(OnTriggerSendListener listener) {
        this.mSendListener = listener;
    }

    /**
     * 启动触发器
     */
    public boolean start() {
        try {
            // 打开串口
            mSerialPort = SerialPort.newBuilder(mDevicePath, mBaudRate)
                    .dataBits(8)
                    .parity(0)
                    .stopBits(1)
                    .build();

            mOutputStream = mSerialPort.getOutputStream();

            // 启动定时发送任务
            startScheduledSending();

            isRunning = true;
            Log.i(TAG, String.format("触发器启动成功: %s @ %d，间隔: %dms",
                    mDevicePath, mBaudRate, mIntervalMs));
            return true;

        } catch (Exception e) {
            Log.e(TAG, "触发器启动失败: " + e.getMessage());
            if (mSendListener != null) {
                mSendListener.onError("触发器启动失败: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * 停止触发器
     */
    public void stop() {
        isRunning = false;

        // 停止定时任务
        if (mScheduledTask != null && !mScheduledTask.isCancelled()) {
            mScheduledTask.cancel(true);
            mScheduledTask = null;
        }

        // 关闭输出流
        try {
            if (mOutputStream != null) {
                mOutputStream.close();
                mOutputStream = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "关闭输出流失败: " + e.getMessage());
        }

        // 关闭串口
        if (mSerialPort != null) {
            mSerialPort.tryClose();
            mSerialPort = null;
        }

        Log.i(TAG, "触发器已停止");
    }

    /**
     * 启动定时发送任务
     */
    private void startScheduledSending() {
        mScheduledTask = mScheduledExecutor.scheduleWithFixedDelay(
                this::sendTriggerData,
                0,
                mIntervalMs,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * 发送触发数据
     */
    private void sendTriggerData() {
        if (!isRunning || mOutputStream == null) {
            return;
        }

        try {
            byte[] data = mTriggerData.getBytes("UTF-8");
            mOutputStream.write(data);
            mOutputStream.flush();

            Log.d(TAG, "已发送: " + mTriggerData.trim());

            if (mSendListener != null) {
                mSendListener.onDataSent(mTriggerData.trim());
            }

        } catch (IOException e) {
            Log.e(TAG, "发送数据失败: " + e.getMessage());
            if (mSendListener != null) {
                mSendListener.onError("发送数据失败: " + e.getMessage());
            }

            // 发送失败时停止触发器
            stop();
        }
    }

    /**
     * 手动发送一次数据
     */
    public boolean sendOnce(String data) {
        if (!isRunning || mOutputStream == null) {
            Log.w(TAG, "触发器未运行，无法发送数据");
            return false;
        }

        try {
            byte[] bytes = data.getBytes("UTF-8");
            mOutputStream.write(bytes);
            mOutputStream.flush();

            Log.d(TAG, "手动发送: " + data.trim());

            if (mSendListener != null) {
                mSendListener.onDataSent(data.trim());
            }

            return true;

        } catch (IOException e) {
            Log.e(TAG, "手动发送失败: " + e.getMessage());
            if (mSendListener != null) {
                mSendListener.onError("手动发送失败: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * 动态修改发送间隔
     */
    public void updateInterval(long newIntervalMs) {
        if (newIntervalMs <= 0) {
            Log.w(TAG, "无效的间隔时间: " + newIntervalMs);
            return;
        }

        this.mIntervalMs = newIntervalMs;

        if (isRunning) {
            // 重启定时任务以应用新间隔
            if (mScheduledTask != null && !mScheduledTask.isCancelled()) {
                mScheduledTask.cancel(false);
            }
            startScheduledSending();

            Log.i(TAG, "发送间隔已更新为: " + newIntervalMs + "ms");
        }
    }

    /**
     * 动态修改触发数据
     */
    public void updateTriggerData(String newTriggerData) {
        this.mTriggerData = newTriggerData;
        Log.i(TAG, "触发数据已更新为: " + newTriggerData.trim());
    }

    /**
     * 检查是否正在运行
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 获取当前触发数据
     */
    public String getTriggerData() {
        return mTriggerData;
    }

    /**
     * 获取当前发送间隔
     */
    public long getInterval() {
        return mIntervalMs;
    }

    /**
     * 获取设备路径
     */
    public String getDevicePath() {
        return mDevicePath;
    }

    /**
     * 销毁资源
     */
    public void destroy() {
        stop();
        if (mScheduledExecutor != null && !mScheduledExecutor.isShutdown()) {
            mScheduledExecutor.shutdown();
            try {
                if (!mScheduledExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    mScheduledExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                mScheduledExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}