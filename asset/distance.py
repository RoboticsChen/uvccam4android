import serial

def parse_frame(frame):
    # 检查帧头
    if frame[0] != 0x5A or frame[1] != 0x5A:
        return None, "帧头错误"
    
    # 检查数据类型
    if frame[2] != 0x45:
        return None, "非距离数据帧"
    
    # 数据量应为2
    if frame[3] != 0x02:
        return None, "数据量异常"
    
    # 校验和验证
    checksum = sum(frame[0:6]) & 0xFF
    if checksum != frame[6]:
        return None, "校验失败"
    
    # 解析距离：高8位在前
    distance = (frame[4] << 8) | frame[5]
    if distance == 20 or distance == 720:
        return None, f"超量程，无效数据"

    return distance, None

def read_distance_from_serial(port="/dev/ttyUSB0", baudrate=9600):
    try:
        ser = serial.Serial(port, baudrate, timeout=1)
        print(f"打开串口 {port} 成功，开始读取距离数据...\n")

        buffer = bytearray()
        
        while True:
            data = ser.read()
            if data:
                buffer += data

                # 检查是否有完整帧
                while len(buffer) >= 7:
                    if buffer[0] == 0x5A and buffer[1] == 0x5A:
                        frame = buffer[0:7]
                        distance, error = parse_frame(frame)

                        frame_hex = ' '.join(f"{byte:02X}" for byte in frame)

                        if error:
                            print(f"[无效帧] 原始数据: {frame_hex} -> {error}")
                        else:
                            print(f"[有效帧] 原始数据: {frame_hex} | 距离: {distance} cm")
                        
                        buffer = buffer[7:]
                    else:
                        buffer.pop(0)
    except serial.SerialException as e:
        print(f"串口错误: {e}")

if __name__ == "__main__":
    read_distance_from_serial("/dev/ttyUSB0", 9600)  # 替换为你的串口号
