# RFID WMS Android APP - 部署指南

## 📱 项目说明

这是 ES-UH8600 RFID 手持终端的 APP，用于扫描标签并上传到 WMS 系统后端服务器。

## 🏗️ 构建步骤

### 1. 安装 Android Studio
- 下载并安装最新版 Android Studio
- 确保已安装 Android SDK（API 21+）

### 2. 打开项目
```bash
cd /Users/pky/WorkBuddy/20260407112620/rfid-wms/android-app
```

在 Android Studio 中打开这个目录。

### 3. 配置服务器地址

打开 `app/src/main/java/com/rfidwms/ApiService.java`，修改服务器地址：

```java
private static final String BASE_URL = "http://你的电脑IP:3000/api";
```

示例：`http://192.168.1.55:3000/api`

### 4. 依赖项

项目使用以下依赖（已在 `build.gradle` 中配置）：
- OkHttp 4.12.0 - HTTP 网络请求
- Gson - JSON 解析

### 5. 构建 APK

1. 在 Android Studio 中点击 `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
2. 等待构建完成
3. APK 文件位置：`app/build/outputs/apk/debug/app-debug.apk`

## 📲 安装到 ES-UH8600

### 方法 1：通过 USB 安装
```bash
adb install app-debug.apk
```

### 方法 2：通过 SD 卡安装
1. 将 APK 文件复制到 ES-UH8600 的 SD 卡
2. 在设备文件管理器中打开 APK
3. 点击安装

### 方法 3：通过 WiFi 传输
1. 在电脑上搭建 HTTP 服务器（如 `python3 -m http.server 8000`）
2. ES-UH8600 浏览器访问 `http://你的电脑IP:8000/app-debug.apk`
3. 下载并安装

## 🚀 使用方法

### 1. 启动 APP
打开 "RFID WMS" 应用

### 2. 连接设备
- 点击 "连接" 按钮
- 等待设备连接成功（显示 "✓ 已连接"）

### 3. 扫描标签
- **方法 1**：点击 "📡 启动扫描" 按钮
- **方法 2**：按设备侧面的物理扫描键

扫描到的标签会自动上传到服务器。

### 4. 查看数据
打开电脑浏览器访问 `http://你的电脑IP:3000/index.html` 查看 Web 前端

## 📋 API 对接说明

### 扫描上传标签
```http
POST http://192.168.1.55:3000/api/tags/scan
Content-Type: application/json

{
  "epc": "E200 3412 7C0A 8F21",
  "tid": "1234567890ABCDEF",
  "rssi": -45
}
```

### 响应
```json
{
  "success": true,
  "tag": {
    "epc": "E200 3412 7C0A 8F21",
    "name": "未命名标签",
    "category": "未分类",
    "qty": 1,
    "location": "未知位置"
  }
}
```

## 🔧 故障排查

### 设备连接失败
1. 检查 ES-UH8600 是否已开机
2. 检查设备驱动是否正常
3. 查看 Logcat 日志

### 上传失败
1. 检查服务器是否运行在端口 3000
2. 检查设备与电脑是否在同一局域网
3. 检查防火墙设置
4. 在电脑浏览器访问 API 地址测试

### 权限问题
APP 需要以下权限：
- `WRITE_EXTERNAL_STORAGE` - 写入日志
- `READ_EXTERNAL_STORAGE` - 读取配置

首次启动时会自动请求授权。

## 📊 SDK 集成

### RFIDManager 核心方法
```java
// 获取单例实例
RFIDManager manager = RFIDManager.getInstance();

// 连接设备
boolean connected = manager.connect();

// 启动扫描
manager.startScan(new OnScanListener() {
    @Override
    public void onTagScanned(String epc, String tid, int rssi, int count) {
        // 处理扫描数据
    }
});

// 停止扫描
manager.stopScan();

// 断开连接
manager.disconnect();
```

## 🎯 系统架构

```
ES-UH8600 设备
    ↓
Android APP (RFIDManager + ApiService)
    ↓
HTTP API (localhost:3000)
    ↓
Node.js 后端服务器
    ↓
Web 前端展示 (index.html)
```

## 📞 技术支持

- SDK 文档：`/Users/pky/Desktop/HCUHF/`
- 官方 Demo：`/Users/pky/Desktop/HCUHF/uhfDemo/`
- Web 前端：`http://192.168.1.55:3000/index.html`
