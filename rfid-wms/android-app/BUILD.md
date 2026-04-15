# Android APP 构建指南

## 前置条件

1. **安装 Android Studio**
   - 下载: https://developer.android.com/studio
   - 安装 SDK 34

2. **安装 Java JDK**
   - JDK 8 或以上

## 构建步骤

### 1. 打开项目

```bash
cd /Users/pky/WorkBuddy/20260407112620/rfid-wms/android-app
```

在 Android Studio 中打开这个目录

### 2. 等待 Gradle 同步

首次打开会自动下载依赖,需要等待几分钟

### 3. 构建 APK

**方式一: 通过 Android Studio**
- Build → Build Bundle(s) / APK(s) → Build APK(s)
- 构建完成后点击通知中的 "locate" 查找 APK 文件

**方式二: 通过命令行**
```bash
cd /Users/pky/WorkBuddy/20260407112620/rfid-wms/android-app
./gradlew assembleDebug
```
APK 位置: `app/build/outputs/apk/debug/app-debug.apk`

## 安装到设备

### 通过 USB 连接

1. **启用 USB 调试**
   - 设备设置 → 开发者选项 → USB 调试

2. **安装 APK**
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 通过 SD 卡

1. 将 APK 复制到 SD 卡
2. 在设备上打开文件管理器
3. 点击 APK 安装

## 使用说明

1. **启动 APP** → 点击 "连接设备"
2. **开始扫描** → 点击 "启动扫描" 或按物理扫描键
3. **查看数据** → 访问 http://192.168.1.55:3000/index.html

## 故障排查

### 构建失败
- 检查 SDK 版本是否为 34
- 清理缓存: Build → Clean Project
- 重新构建: Build → Rebuild Project

### 设备连接失败
- 检查 WiFi 是否连接
- 确认服务器 IP 地址正确 (ApiService.java 中的 BASE_URL)
- 后端服务是否运行: `cd backend && node server.js`

### 上传失败
- 检查后端服务是否运行
- 确认设备能访问服务器 IP
- 查看后端日志是否有错误

## 配置修改

### 修改服务器地址

编辑 `app/src/main/java/com/rfidwms/ApiService.java`:
```java
private static final String BASE_URL = "http://你的IP:3000/api";
```

### 修改包名

编辑 `app/build.gradle`:
```gradle
applicationId "com.yourcompany.rfidwms"
```

### 修改应用名称

编辑 `app/src/main/res/values/strings.xml`:
```xml
<string name="app_name">你的应用名称</string>
```
