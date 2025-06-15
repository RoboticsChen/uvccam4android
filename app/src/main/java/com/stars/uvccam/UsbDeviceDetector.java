package com.stars.uvccam;

import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * USB串口设备自动检测类
 * 根据设备的Vendor ID和Product ID自动识别设备路径
 */
public class UsbDeviceDetector {
    private static final String TAG = "UsbDeviceDetector";

    // 设备识别信息
    public static class DeviceInfo {
        public final String devicePath;
        public final String vendorId;
        public final String productId;
        public final String description;

        public DeviceInfo(String devicePath, String vendorId, String productId, String description) {
            this.devicePath = devicePath;
            this.vendorId = vendorId;
            this.productId = productId;
            this.description = description;
        }

        @Override
        public String toString() {
            return String.format("Device: %s [%s:%s] - %s", devicePath, vendorId, productId, description);
        }
    }

    // 预定义的设备类型
    public static final String ULTRASONIC_VENDOR_ID = "10c4";
    public static final String ULTRASONIC_PRODUCT_ID = "ea60";
    public static final String TRIGGER_VENDOR_ID = "1a86";
    public static final String TRIGGER_PRODUCT_ID = "7523";

    /**
     * 扫描所有USB串口设备
     */
    public static List<DeviceInfo> scanUsbSerialDevices() {
        List<DeviceInfo> devices = new ArrayList<>();

        try {
            // 扫描所有ttyUSB设备
            File devDir = new File("/dev");
            File[] files = devDir.listFiles((dir, name) -> name.startsWith("ttyUSB"));

            if (files != null) {
                for (File file : files) {
                    DeviceInfo deviceInfo = getDeviceInfo(file.getAbsolutePath());
                    if (deviceInfo != null) {
                        devices.add(deviceInfo);
                        Log.i(TAG, "发现设备: " + deviceInfo.toString());
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "扫描设备失败: " + e.getMessage());
        }

        return devices;
    }

    /**
     * 获取指定设备路径的详细信息
     */
    private static DeviceInfo getDeviceInfo(String devicePath) {
        try {
            // 从设备路径提取设备编号
            String deviceName = new File(devicePath).getName();

            // 查找对应的USB设备信息
            return findUsbDeviceInfo(deviceName);

        } catch (Exception e) {
            Log.e(TAG, "获取设备信息失败: " + devicePath + " -> " + e.getMessage());
            return null;
        }
    }

    /**
     * 查找USB设备信息
     */
    private static DeviceInfo findUsbDeviceInfo(String deviceName) {
        try {
            // 读取 /sys/class/tty/ttyUSBx/device/../idVendor 和 idProduct
            String sysPath = "/sys/class/tty/" + deviceName + "/device";

            // 尝试不同的路径结构
            String[] possiblePaths = {
                    sysPath + "/../idVendor",
                    sysPath + "/../../idVendor",
                    sysPath + "/../../../idVendor"
            };

            for (String vendorPath : possiblePaths) {
                File vendorFile = new File(vendorPath);
                if (vendorFile.exists()) {
                    String productPath = vendorPath.replace("idVendor", "idProduct");
                    File productFile = new File(productPath);

                    if (productFile.exists()) {
                        String vendorId = readFileContent(vendorFile).trim();
                        String productId = readFileContent(productFile).trim();

                        String description = getDeviceDescription(vendorId, productId);

                        return new DeviceInfo("/dev/" + deviceName, vendorId, productId, description);
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "查找USB设备信息失败: " + e.getMessage());
        }

        return null;
    }

    /**
     * 读取文件内容
     */
    private static String readFileContent(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
        }
        return content.toString();
    }

    /**
     * 根据Vendor ID和Product ID获取设备描述
     */
    private static String getDeviceDescription(String vendorId, String productId) {
        String key = vendorId + ":" + productId;

        switch (key) {
            case ULTRASONIC_VENDOR_ID + ":" + ULTRASONIC_PRODUCT_ID:
                return "超声距离传感器 (CP210x)";
            case TRIGGER_VENDOR_ID + ":" + TRIGGER_PRODUCT_ID:
                return "触发器设备 (CH340)";
            default:
                return "未知设备";
        }
    }

    /**
     * 查找特定类型的设备
     */
    public static String findDeviceByType(String vendorId, String productId) {
        List<DeviceInfo> devices = scanUsbSerialDevices();

        for (DeviceInfo device : devices) {
            if (vendorId.equalsIgnoreCase(device.vendorId) &&
                    productId.equalsIgnoreCase(device.productId)) {
                Log.i(TAG, "找到匹配设备: " + device.toString());
                return device.devicePath;
            }
        }

        Log.w(TAG, String.format("未找到设备 [%s:%s]", vendorId, productId));
        return null;
    }

    /**
     * 查找超声传感器设备
     */
    public static String findUltrasonicDevice() {
        return findDeviceByType(ULTRASONIC_VENDOR_ID, ULTRASONIC_PRODUCT_ID);
    }

    /**
     * 查找触发器设备
     */
    public static String findTriggerDevice() {
        return findDeviceByType(TRIGGER_VENDOR_ID, TRIGGER_PRODUCT_ID);
    }

    /**
     * 等待设备连接（带超时）
     */
    public static String waitForDevice(String vendorId, String productId, int timeoutSeconds) {
        long startTime = System.currentTimeMillis();
        long timeout = timeoutSeconds * 1000L;

        while (System.currentTimeMillis() - startTime < timeout) {
            String devicePath = findDeviceByType(vendorId, productId);
            if (devicePath != null) {
                return devicePath;
            }

            try {
                Thread.sleep(500); // 每500ms检查一次
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return null;
    }

    /**
     * 检查设备是否仍然连接
     */
    public static boolean isDeviceConnected(String devicePath) {
        if (devicePath == null) {
            return false;
        }

        File deviceFile = new File(devicePath);
        return deviceFile.exists();
    }

    /**
     * 获取所有设备的状态报告
     */
    public static String getDeviceStatusReport() {
        List<DeviceInfo> devices = scanUsbSerialDevices();

        StringBuilder report = new StringBuilder();
        report.append("=== USB串口设备扫描报告 ===\n");
        report.append("发现设备数量: ").append(devices.size()).append("\n\n");

        for (DeviceInfo device : devices) {
            report.append(device.toString()).append("\n");
        }

        // 检查预期设备
        report.append("\n=== 预期设备状态 ===\n");
        String ultrasonicPath = findUltrasonicDevice();
        String triggerPath = findTriggerDevice();

        report.append("超声传感器: ").append(ultrasonicPath != null ? ultrasonicPath : "未找到").append("\n");
        report.append("触发器设备: ").append(triggerPath != null ? triggerPath : "未找到").append("\n");

        return report.toString();
    }
}