# Android Studio 中文安装指南

## 方案 1: 安装中文插件（推荐）

### 步骤说明

1. **启动 Android Studio**
   ```bash
   open /Applications/Android\ Studio.app
   ```

2. **首次启动设置**
   - 会看到欢迎界面
   - 点击 **New Project** 或 **Open**
   - 等待加载完成

3. **安装中文语言包**
   - 点击顶部菜单 **File → Settings** (Mac 上是 **Android Studio → Settings**)
   - 在左侧菜单选择 **Plugins**
   - 搜索框输入: **Chinese**
   - 找到 **Chinese (Simplified) Language Pack** 或类似的中文插件
   - 点击 **Install** 安装
   - 安装完成后点击 **Restart IDE** 重启

4. **切换到中文界面**
   - 重启后界面会自动变成中文

---

## 方案 2: 使用中文翻译对照表

### 常用操作对照

| 英文 | 中文 |
|------|------|
| File | 文件 |
| Edit | 编辑 |
| View | 视图 |
| Navigate | 导航 |
| Code | 代码 |
| Build | 构建 |
| Run | 运行 |
| Tools | 工具 |
| Help | 帮助 |
| Open... | 打开... |
| New Project | 新建项目 |
| Open an Existing Project | 打开现有项目 |
| Settings | 设置 |
| Preferences | 偏好设置 |
| Plugins | 插件 |
| Build Bundle(s) / APK(s) | 构建捆绑包 / APK(s) |
| Build APK(s) | 构建 APK |
| Run App | 运行应用 |
| Debug App | 调试应用 |
| Gradle sync | Gradle 同步 |

---

## 快速构建 APK 步骤（中英文对照）

### 第 1 步: 打开项目
```
File → Open... (文件 → 打开...)
选择: /Users/pky/WorkBuddy/20260407112620/rfid-wms/android-app
点击 Open (打开)
```

### 第 2 步: 等待同步
- 观察底部状态栏
- 等待显示 **"Gradle sync finished"** (Gradle 同步完成)
- 首次可能需要 2-5 分钟

### 第 3 步: 构建 APK
```
方法一: 菜单操作
Build → Build Bundle(s) / APK(s) → Build APK(s)
(构建 → 构建捆绑包 / APK(s) → 构建 APK(s))

方法二: 快捷键
Mac: Cmd + Shift + B
```

### 第 4 步: 查找 APK
- 构建完成会弹出通知
- 点击 **locate** (定位)
- APK 位置:
  ```
  /Users/pky/WorkBuddy/20260407112620/rfid-wms/android-app/app/build/outputs/apk/debug/app-debug.apk
  ```

---

## 安装到设备

### USB 安装
```bash
adb install /Users/pky/WorkBuddy/20260407112620/rfid-wms/android-app/app/build/outputs/apk/debug/app-debug.apk
```

### SD 卡安装
1. 复制 APK 到 SD 卡
2. 在设备文件管理器中打开并安装

---

## 常见问题

### Q: 找不到中文插件？
A: 在 Plugins 搜索框输入 "language" 或 "translation"

### Q: 插件安装失败？
A: 检查网络连接，或使用 VPN

### Q: 不想安装插件怎么办？
A: 直接使用上面的中英文对照表操作即可

---

## 下一步

完成中文设置后，按照上面的步骤打开项目并构建 APK。

如果需要帮助，随时告诉我你看到什么界面！
