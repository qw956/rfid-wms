#!/bin/bash

echo "========================================="
echo "RFID WMS Android APP 构建脚本"
echo "========================================="
echo ""

# 检查是否安装了 Android Studio
if [ ! -d "/Applications/Android Studio.app" ]; then
    echo "❌ 未检测到 Android Studio"
    echo "请先安装 Android Studio: https://developer.android.com/studio"
    exit 1
fi

echo "✅ 检测到 Android Studio"
echo ""

# 显示构建步骤
echo "请按照以下步骤操作："
echo ""
echo "1. 打开 Android Studio"
echo "2. File → Open → 选择目录:"
echo "   /Users/pky/WorkBuddy/20260407112620/rfid-wms/android-app"
echo ""
echo "3. 等待 Gradle 同步完成（首次需要 2-5 分钟）"
echo ""
echo "4. 点击菜单:"
echo "   Build → Build Bundle(s) / APK(s) → Build APK(s)"
echo ""
echo "5. 构建完成后,点击通知中的 'locate' 查看 APK 位置"
echo ""
echo "========================================="
echo "预期 APK 位置:"
echo "========================================="
echo ""
echo "/Users/pky/WorkBuddy/20260407112620/rfid-wms/android-app/app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "========================================="
echo "安装到设备命令:"
echo "========================================="
echo ""
echo "cd /Users/pky/WorkBuddy/20260407112620/rfid-wms/android-app"
echo "adb install app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "========================================="
echo ""

# 尝试用 Android Studio 打开项目
echo "正在尝试打开 Android Studio..."
open -a "Android Studio" /Users/pky/WorkBuddy/20260407112620/rfid-wms/android-app

echo "如果 Android Studio 没有自动打开,请手动打开上述目录"
echo ""
