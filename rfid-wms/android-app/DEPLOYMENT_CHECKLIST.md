# 部署检查清单

## ✅ 构建前检查

### 项目文件
- [x] `build.gradle` (根目录)
- [x] `settings.gradle`
- [x] `app/build.gradle`
- [x] `ModuleAPI/build.gradle`
- [x] `AndroidManifest.xml`
- [x] Java 源文件完整
- [x] 资源文件 (layout, colors, strings, styles)

### SDK 集成
- [x] ModuleAPI 模块已包含
- [x] CMakeLists.txt 存在
- [x] SO 库存在 (armeabi-v7a, arm64-v8a)
- [x] 依赖库已配置 (OkHttp, Gson, Material)

## 🔨 构建步骤

### 在 Android Studio 中
1. [ ] 打开项目 `/Users/pky/WorkBuddy/20260407112620/rfid-wms/android-app/`
2. [ ] 等待 Gradle 同步完成
3. [ ] Build → Build Bundle(s) / APK(s) → Build APK(s)
4. [ ] 构建成功后,点击 "locate" 查看 APK 位置

### 预期 APK 位置
```
/Users/pky/WorkBuddy/20260407112620/rfid-wms/android-app/app/build/outputs/apk/debug/app-debug.apk
```

## 📱 安装到设备

### ES-UH8600 设备准备
1. [ ] 连接 WiFi (与电脑同一网络)
2. [ ] 启用开发者选项
3. [ ] 启用 USB 调试

### 安装方式
**方式一: USB 安装**
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

**方式二: SD 卡安装**
1. [ ] 复制 APK 到 SD 卡
2. [ ] 在设备文件管理器中打开
3. [ ] 点击安装

## 🧪 测试步骤

### 1. 设备连接
- [ ] 打开 APP
- [ ] 点击 "连接设备" 按钮
- [ ] 确认显示 "✓ 已连接"

### 2. 扫描测试
- [ ] 点击 "启动扫描" 按钮
- [ ] 扫描 RFID 标签
- [ ] 确认显示 EPC、TID、RSSI
- [ ] 点击 "停止扫描"

### 3. 数据上传
- [ ] 扫描标签
- [ ] 检查 Toast 提示
- [ ] 打开 http://192.168.1.55:3000/index.html
- [ ] 确认数据显示在 Web 前端

### 4. 功能测试
- [ ] 点击 "清空数据" 按钮确认清空
- [ ] 物理扫描键触发扫描 (设备支持)
- [ ] 多次扫描测试去重逻辑

## 🔧 常见问题

### 构建失败
| 问题 | 解决方案 |
|------|----------|
| Gradle 同步失败 | 检查网络,重试同步 |
| SDK 版本错误 | 安装 SDK 34 |
| CMake 错误 | 检查 CMakeLists.txt 路径 |
| 签名错误 | 已移除签名配置 |

### 运行时错误
| 问题 | 解决方案 |
|------|----------|
| 连接失败 | 检查设备权限,重启 APP |
| 扫描无响应 | 检查 SDK 初始化,重启设备 |
| 上传失败 | 检查后端服务,确认 IP 地址 |
| 崩溃 | 查看 Logcat 日志 |

### 网络问题
| 问题 | 解决方案 |
|------|----------|
| 无法连接服务器 | 检查 WiFi 连接,确认 IP |
| 上传超时 | 检查后端日志,确认服务运行 |
| 清除文本流量 | 已配置 `usesCleartextTraffic="true"` |

## 📊 性能指标

- 首次启动时间: < 3 秒
- 设备连接时间: < 2 秒
- 扫描响应时间: < 100ms
- 上传成功时间: < 500ms

## 📝 日志查看

### Android Logcat
```bash
adb logcat | grep "RFID"
```

### 后端日志
后端控制台输出,包含所有 API 请求

### Web 前端
浏览器 F12 → Console 查看 JavaScript 日志

## ✅ 验收标准

- [x] 项目可以在 Android Studio 中打开
- [ ] 构建成功生成 APK
- [ ] APP 可以安装到 ES-UH8600
- [ ] 设备可以连接
- [ ] 扫描功能正常
- [ ] 数据可以上传到服务器
- [ ] Web 前端可以实时显示数据

## 🎯 下一步

完成所有检查项后,系统即可投入使用!

---

创建时间: 2026-04-08
版本: 1.0
