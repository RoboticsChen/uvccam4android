import serial
import time

PORT = '/dev/ttyUSB0'  # 根据实际修改
BAUD_RATE = 115200

try:
    ser = serial.Serial(PORT, BAUD_RATE, timeout=1)
    print(f"已连接到串口 {PORT}，每50ms发送一行 '18 13 8 \\r\\n'")

    while True:
        line = "8 18 13 \r\n"
        ser.write(line.encode('utf-8'))
        print(f"已发送: {line.strip()}")
        time.sleep(0.05)  # 50ms

except serial.SerialException as e:
    print(f"串口错误: {e}")
except KeyboardInterrupt:
    print("程序中断，关闭串口。")
finally:
    if 'ser' in locals() and ser.is_open:
        ser.close()
