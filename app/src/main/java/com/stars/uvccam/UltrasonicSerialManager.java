package com.stars.uvccam;

import android.serialport.SerialPort;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 超声距离传感器串口管理类
 * 用于读取超声距离数据，解析协议帧
 */
public class UltrasonicSerialManager {
    private static final String TAG = "UltrasonicSerial";

    // 协议常量
    private static final byte FRAME_HEADER_1 = (byte) 0x5A;
    private static final byte FRAME_HEADER_2 = (byte) 0x5A;
    private static final byte DATA_TYPE_DISTANCE = (byte) 0x45;
    private static final byte DATA_LENGTH = (byte) 0x02;
    private static final int FRAME_LENGTH = 7;

    // 无效距离值
    private static final int INVALID_DISTANCE_1 = 20;
    private static final int INVALID_DISTANCE_2 = 720;

    private SerialPort mSerialPort;
    private InputStream mInputStream;
    private ExecutorService mExecutorService;
    private ReadThread mReadThread;
    private boolean isRunning = false;

    private String mDevicePath;
    private int mBaudRate;

    // 数据缓冲区
    private byte[] mBuffer = new byte[1024];
    private int mBufferPos = 0;

    // 回调接口
    private OnDistanceDataListener mDataListener;

    public interface OnDistanceDataListener {
        void onDistanceReceived(int distance);
        void onInvalidFrame(String frameHex, String error);
        void onError(String error);
    }

    /**
     * 构造函数
     * @param devicePath 设备路径，如 "/dev/ttyUSB0"
     * @param baudRate 波特率，默认9600
     */
    public UltrasonicSerialManager(String devicePath, int baudRate) {
        this.mDevicePath = devicePath;
        this.mBaudRate = baudRate;
        this.mExecutorService = Executors.newSingleThreadExecutor();
    }

    /**
     * 设置距离数据监听器
     */
    public void setOnDistanceDataListener(OnDistanceDataListener listener) {
        this.mDataListener = listener;
    }

    /**
     * 打开串口并开始读取
     */
    public boolean start() {
        try {
            File deviceFile = new File(mDevicePath);
            mSerialPort = new SerialPort(deviceFile, mBaudRate);
            mInputStream = mSerialPort.getInputStream();

            isRunning = true;
            mReadThread = new ReadThread();
            mExecutorService.execute(mReadThread);

            Log.i(TAG, "超声距离串口启动成功: " + mDevicePath + " @ " + mBaudRate);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "超声距离串口启动失败: " + e.getMessage());
            if (mDataListener != null) {
                mDataListener.onError("串口启动失败: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * 停止读取并关闭串口
     */
    public void stop() {
        isRunning = false;

        if (mReadThread != null) {
            mReadThread.interrupt();
            mReadThread = null;
        }

        try {
            if (mInputStream != null) {
                mInputStream.close();
                mInputStream = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "关闭输入流失败: " + e.getMessage());
        }

        if (mSerialPort != null) {
            mSerialPort.tryClose();
            mSerialPort = null;
        }

        Log.i(TAG, "超声距离串口已停止");
    }

    /**
     * 读取数据线程
     */
    private class ReadThread extends Thread {
        @Override
        public void run() {
            byte[] readBuffer = new byte[64];

            while (isRunning && !Thread.currentThread().isInterrupted()) {
                try {
                    if (mInputStream == null) {
                        break;
                    }

                    int bytesRead = mInputStream.read(readBuffer);
                    if (bytesRead > 0) {
                        // 将读取的数据添加到缓冲区
                        for (int i = 0; i < bytesRead; i++) {
                            addByteToBuffer(readBuffer[i]);
                        }
                    }

                } catch (IOException e) {
                    if (isRunning) {
                        Log.e(TAG, "读取数据失败: " + e.getMessage());
                        if (mDataListener != null) {
                            mDataListener.onError("读取数据失败: " + e.getMessage());
                        }
                    }
                    break;
                }
            }
        }
    }

    /**
     * 添加字节到缓冲区并检查完整帧
     */
    private void addByteToBuffer(byte data) {
        // 防止缓冲区溢出
        if (mBufferPos >= mBuffer.length - 1) {
            mBufferPos = 0; // 重置缓冲区
        }

        mBuffer[mBufferPos++] = data;

        // 检查是否有完整帧
        checkCompleteFrame();
    }

    /**
     * 检查缓冲区中是否有完整的协议帧
     */
    private void checkCompleteFrame() {
        while (mBufferPos >= FRAME_LENGTH) {
            // 寻找帧头
            int frameStart = -1;
            for (int i = 0; i <= mBufferPos - FRAME_LENGTH; i++) {
                if (mBuffer[i] == FRAME_HEADER_1 && mBuffer[i + 1] == FRAME_HEADER_2) {
                    frameStart = i;
                    break;
                }
            }

            if (frameStart == -1) {
                // 没有找到帧头，移除第一个字节
                shiftBuffer(1);
                continue;
            }

            if (frameStart > 0) {
                // 移除帧头前的无效数据
                shiftBuffer(frameStart);
                continue;
            }

            // 检查是否有完整帧
            if (mBufferPos >= FRAME_LENGTH) {
                // 提取完整帧
                byte[] frame = new byte[FRAME_LENGTH];
                System.arraycopy(mBuffer, 0, frame, 0, FRAME_LENGTH);

                // 解析帧
                parseFrame(frame);

                // 移除已处理的帧
                shiftBuffer(FRAME_LENGTH);
            } else {
                break; // 数据不够完整帧
            }
        }
    }

    /**
     * 移动缓冲区数据
     */
    private void shiftBuffer(int offset) {
        if (offset >= mBufferPos) {
            mBufferPos = 0;
        } else {
            System.arraycopy(mBuffer, offset, mBuffer, 0, mBufferPos - offset);
            mBufferPos -= offset;
        }
    }

    /**
     * 解析协议帧
     */
    private void parseFrame(byte[] frame) {
        String frameHex = bytesToHex(frame);

        // 检查帧头
        if (frame[0] != FRAME_HEADER_1 || frame[1] != FRAME_HEADER_2) {
            notifyInvalidFrame(frameHex, "帧头错误");
            return;
        }

        // 检查数据类型
        if (frame[2] != DATA_TYPE_DISTANCE) {
            notifyInvalidFrame(frameHex, "非距离数据帧");
            return;
        }

        // 检查数据量
        if (frame[3] != DATA_LENGTH) {
            notifyInvalidFrame(frameHex, "数据量异常");
            return;
        }

        // 校验和验证
        int checksum = 0;
        for (int i = 0; i < 6; i++) {
            checksum += (frame[i] & 0xFF);
        }
        checksum &= 0xFF;

        if (checksum != (frame[6] & 0xFF)) {
            notifyInvalidFrame(frameHex, "校验失败");
            return;
        }

        // 解析距离：高8位在前
        int distance = ((frame[4] & 0xFF) << 8) | (frame[5] & 0xFF);

        // 检查是否为无效距离
        if (distance == INVALID_DISTANCE_1 || distance == INVALID_DISTANCE_2) {
            notifyInvalidFrame(frameHex, "超量程，无效数据");
            return;
        }

        // 有效距离数据
        Log.d(TAG, String.format("[有效帧] 原始数据: %s | 距离: %d cm", frameHex, distance));

        if (mDataListener != null) {
            mDataListener.onDistanceReceived(distance);
        }
    }

    /**
     * 通知无效帧
     */
    private void notifyInvalidFrame(String frameHex, String error) {
        Log.w(TAG, String.format("[无效帧] 原始数据: %s -> %s", frameHex, error));

        if (mDataListener != null) {
            mDataListener.onInvalidFrame(frameHex, error);
        }
    }

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b & 0xFF));
        }
        return sb.toString().trim();
    }

    /**
     * 检查是否正在运行
     */
    public boolean isRunning() {
        return isRunning;
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
        if (mExecutorService != null && !mExecutorService.isShutdown()) {
            mExecutorService.shutdown();
        }
    }
}