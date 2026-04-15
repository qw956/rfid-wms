# 手动构建指南

## 🚀 快速开始

### 当前状态

✅ 项目代码完整
⏳ 需要在 Android Studio 中构建 APK

### 第 1 步: 安装 Android Studio

如果没有安装,请下载并安装:

**Mac:**
```
下载: https://dl.google.com/dl/android/studio/install/2024.2.1.12/android-studio-2024.2.1.12-mac.dmg
```

安装后打开,按提示安装 SDK 34

### 第 2 步: 打开项目

1. 启动 Android Studio
2. 选择 "Open an Existing Project"
3. 导航到:
   ```
   /Users/pky/WorkBuddy/20260407112620/rfid-wms/android-app
   ```
4. 点击 "Open"

### 第 3 步: 等待同步

首次打开会自动:
- 下载 Gradle
- 下载 Android SDK
- 下载依赖库

**预计时间: 2-5 分钟**

等待状态栏显示 "Gradle sync finished"

### 第 4 步: 构建 APK

方式一: 菜单操作
```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```

方式二: 快捷键
```
Mac: Cmd + Shift + B
然后选择 "Build APK(s)"
```

### 第 5 步: 查找 APK

构建完成后:
1. 点击右下角通知 "Build APK(s) finished"
2. 点击 "locate"
3. APK 位置:
   ```
   /Users/pky/WorkBuddy/20260407112620/rfid-wms/android-app/app/build/outputs/apk/debug/app-debug.apk
   ```

## 📱 安装到设备

### 方式一: USB 安装

1. **连接设备**
   ```bash
   # 检查设备连接
   adb devices
   ```

2. **安装 APK**
   ```bash
   cd /Users/pky/WorkBuddy/20260407112620/rfid-wms/android-app
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### 方式二: SD 卡安装

1. 将 APK 复制到 SD 卡
2. 在 ES-UH8600 设备上打开文件管理器
3. 找到 APK 文件并点击安装

## 🧪 测试

### 1. 启动 APP
- 在设备上打开 "RFID 标签管理" APP

### 2. 连接设备
- 点击 "连接设备" 按钮
- 确认显示 "✓ 已连接"

### 3. 扫描标签
- 点击 "启动扫描"
- 扫描 RFID 标签
- 确认显示 EPC、TID、RSSI

### 4. 验证上传
- 在电脑浏览器打开: http://192.168.1.55:3000/index.html
- 确认扫描的数据显示在 Web 前端

## ⚠️ 常见问题

### 问题: Gradle 同步失败
**解决:**
- File → Invalidate Caches → Invalidate and Restart
- 检查网络连接
- 重新打开项目

### 问题: SDK 版本错误
**解决:**
- Tools → SDK Manager
- 安装 SDK Platform 34
- 点击 "Apply"

### 问题: 构建失败
**解决:**
- Build → Clean Project
- Build → Rebuild Project
- 查看 Build 窗口的错误信息

### 问题: 设备连接失败
**解决:**
- 确认设备与电脑在同一 WiFi
- 检查后端服务运行: `lsof -ti:3000`
- 修改 ApiService.java 中的 BASE_URL

## 📞 需要帮助?

如果遇到问题,请提供:
1. 错误截图
2. Build 窗口的完整日志
3. Logcat 日志 (如果 APP 崩溃)

---

创建时间: 2026-04-08
版本: 1.0
