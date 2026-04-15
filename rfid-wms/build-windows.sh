#!/bin/bash
# build-windows.sh — 在 macOS 上交叉编译 Windows exe 安装包（完整版）
# 功能：
#   1. 安装 npm 依赖（含 better-sqlite3 Windows 预编译）
#   2. pkg 打包为 Windows exe
#   3. 清理旧数据（数据库/日志/扫描文件）
#   4. 复制所有分发文件
#   5. 生成带安装向导的 Inno Setup 脚本
#   6. 自动调用 makensis 编译（若已安装）
#
# 前置要求：
#   npm install -g pkg
#   brew install makensis          # 可选，无则生成 .iss 供 Windows 上编译

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR/backend"
BUILD_DIR="$BACKEND_DIR/build"
DIST_DIR="$BACKEND_DIR/dist"
APP_NAME="RFID-WMS"
VERSION="1.0.0"

echo "=========================================="
echo "   RFID WMS Windows 安装包构建"
echo "=========================================="
echo ""

# ============================================================
# 1. 清理旧构建
# ============================================================
echo "[1/7] 清理旧构建..."
rm -rf "$BUILD_DIR" "$DIST_DIR"
mkdir -p "$BUILD_DIR" "$DIST_DIR"
echo "  ✅ 清理完成"

# ============================================================
# 2. 安装依赖（强制安装 better-sqlite3 含 Windows 预编译）
# ============================================================
echo "[2/7] 安装 npm 依赖..."
cd "$BACKEND_DIR"
npm install --production 2>&1 | tail -5

# 确认 better-sqlite3 可用
if [ ! -d "node_modules/better-sqlite3" ]; then
    echo "  ⚠️ better-sqlite3 未安装，强制安装..."
    npm install better-sqlite3 --force 2>&1 | tail -5
fi
echo "  ✅ 依赖安装完成"

# ============================================================
# 3. 打包为 Windows exe
# ============================================================
echo "[3/7] pkg 打包 Windows exe（node18-x64）..."
cd "$BACKEND_DIR"
npx pkg server.js \
  --target node18-win-x64 \
  --output "$BUILD_DIR/$APP_NAME.exe" \
  --compress GZip \
  --public

if [ ! -f "$BUILD_DIR/$APP_NAME.exe" ]; then
    echo "  ❌ pkg 打包失败，请检查 node/npm 版本"
    exit 1
fi
echo "  ✅ exe 大小: $(du -h "$BUILD_DIR/$APP_NAME.exe" | cut -f1)"
echo "  ✅ 路径: $BUILD_DIR/$APP_NAME.exe"

# ============================================================
# 4. 复制分发文件并清理旧数据
# ============================================================
echo "[4/7] 复制分发文件 & 清理旧数据..."

# --- 前端文件 ---
mkdir -p "$BUILD_DIR/web"
cp "$BACKEND_DIR/../index.html" "$BUILD_DIR/web/"

# --- 数据库：保留结构，清空数据 ---
mkdir -p "$BUILD_DIR/data"
SQLITE_DB="$BACKEND_DIR/data/rfid-wms.db"

if [ -f "$SQLITE_DB" ]; then
    # 用 sql.js 或 node 脚本重建空数据库
    node -e "
const Database = require('better-sqlite3');
const db = new Database('$BUILD_DIR/data/rfid-wms.db');
db.exec(\`
  CREATE TABLE IF NOT EXISTS tags (
    epc TEXT PRIMARY KEY, tid TEXT DEFAULT '', rssi INTEGER DEFAULT 0,
    name TEXT DEFAULT '未命名标签', category TEXT DEFAULT '未分类',
    qty INTEGER DEFAULT 1, location TEXT DEFAULT '未知位置',
    department TEXT DEFAULT '', user_name TEXT DEFAULT '',
    purchase_date TEXT DEFAULT '', user_data TEXT DEFAULT '',
    scanCount INTEGER DEFAULT 1, created TEXT NOT NULL,
    updated TEXT NOT NULL, lastScan TEXT NOT NULL
  );
  CREATE TABLE IF NOT EXISTS operation_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT, action TEXT NOT NULL,
    detail TEXT DEFAULT '', ip TEXT DEFAULT '', created TEXT NOT NULL
  );
  CREATE TABLE IF NOT EXISTS scan_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT NOT NULL,
    epc TEXT NOT NULL, tid TEXT DEFAULT '', rssi INTEGER DEFAULT 0,
    name TEXT DEFAULT '', category TEXT DEFAULT '', qty INTEGER DEFAULT 1,
    location TEXT DEFAULT '', department TEXT DEFAULT '',
    user_name TEXT DEFAULT '', purchase_date TEXT DEFAULT '',
    user_data TEXT DEFAULT '', scanned_at TEXT NOT NULL,
    UNIQUE(session_id, epc)
  );
  CREATE TABLE IF NOT EXISTS categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE,
    sort_order INTEGER DEFAULT 0, created TEXT NOT NULL
  );
\`);
const now = new Date().toISOString();
const defaults = ['紧固件','电子元器件','润滑油脂','密封件','变频器','其他'];
defaults.forEach((name, i) => {
  try { db.prepare('INSERT INTO categories VALUES (?,?,?,?)').run(null, name, i, now); } catch(e){}
});
db.close();
console.log('空数据库创建完成');
" 2>&1
    echo "  ✅ 数据库已清空（新库结构）"
fi

# --- 清理 WAL/SHM/日志/扫描文件 ---
rm -f "$BACKEND_DIR/data/rfid-wms.db-wal" "$BACKEND_DIR/data/rfid-wms.db-shm" 2>/dev/null || true
rm -f "$BACKEND_DIR/data/rfid-wms.db" 2>/dev/null || true   # 重新生成
mkdir -p "$BUILD_DIR/data" "$BUILD_DIR/logs" "$BUILD_DIR/scan_files" "$BUILD_DIR/exports" "$BUILD_DIR/uploads"
touch "$BUILD_DIR/data/.gitkeep" "$BUILD_DIR/logs/.gitkeep" "$BUILD_DIR/scan_files/.gitkeep" "$BUILD_DIR/exports/.gitkeep" "$BUILD_DIR/uploads/.gitkeep"
echo "  ✅ 运行时目录已创建（旧文件已清理）"

# --- better-sqlite3 native 模块（pkg 模式用 sql.js，但保留 better-sqlite3 以防万一） ---
SQLITE3_DIR="$BACKEND_DIR/node_modules/better-sqlite3"
mkdir -p "$BUILD_DIR/node_modules/better-sqlite3"
cp "$SQLITE3_DIR/package.json" "$BUILD_DIR/node_modules/better-sqlite3/" 2>/dev/null || true

# sql.js（WASM 模式 pkg 必须）
SQLJS_DIR="$BACKEND_DIR/node_modules/sql.js"
if [ -d "$SQLJS_DIR" ]; then
    mkdir -p "$BUILD_DIR/node_modules/sql.js/dist"
    cp "$SQLJS_DIR/dist/sql-wasm.wasm" "$BUILD_DIR/node_modules/sql.js/dist/" 2>/dev/null || true
    cp "$SQLJS_DIR/dist/sql-wasm.js" "$BUILD_DIR/node_modules/sql.js/dist/" 2>/dev/null || true
    cp "$SQLJS_DIR/package.json" "$BUILD_DIR/node_modules/sql.js/" 2>/dev/null || true
fi

# --- 配置文件（初始端口3000） ---
cat > "$BUILD_DIR/config.ini" << 'INI_EOF'
# RFID 仓储管理系统 配置文件
# 安装后可在此修改，重启生效

port=3000
INI_EOF
echo "  ✅ 配置文件已创建（端口=3000）"

# --- APK 文件 ---
if [ -f "$BACKEND_DIR/../android-app/ModuleAPI/app/build/outputs/apk/debug/app-debug.apk" ]; then
    mkdir -p "$BUILD_DIR/public/download/apk"
    cp "$BACKEND_DIR/../android-app/ModuleAPI/app/build/outputs/apk/debug/app-debug.apk" "$BUILD_DIR/public/download/apk/" 2>/dev/null || true
fi

echo "  ✅ 分发文件准备完毕"

# ============================================================
# 5. 生成启动/停止脚本
# ============================================================
echo "[5/7] 生成启动脚本..."

# 获取本机默认 IP（辅助用户填写配置）
HOST_IP=$(hostname -I 2>/dev/null | awk '{print $1}' || echo "192.168.1.100")

cat > "$BUILD_DIR/start.bat" << 'BAT_EOF'
@echo off
chcp 65001 >nul 2>&1
title RFID 仓储管理系统

:: ========== 读取配置文件 ==========
for /f "usebackq tokens=1,* delims==" %%a in ("%~dp0config.ini") do (
    if /i "%%a"=="port" set "PORT=%%b"
    if /i "%%a"=="host" set "HOST=%%b"
)
if not defined PORT set PORT=3000
if not defined HOST set HOST=127.0.0.1

:: ========== 防火墙放行端口 ==========
echo [防火墙] 放行端口 %PORT% ...
netsh advfirewall firewall show rule name="RFID资产盘点 %PORT%" >nul 2>&1
if errorlevel 1 (
    netsh advfirewall firewall add rule name="RFID资产盘点 %PORT%" dir=in action=allow protocol=TCP localport=%PORT% >nul 2>&1
    echo [OK] 端口 %PORT% 防火墙规则已添加
) else (
    echo [OK] 端口 %PORT% 防火墙规则已存在
)

:: ========== 创建数据目录（绿色版兼容） ==========
if not exist "%~dp0data" mkdir "%~dp0data"
if not exist "%~dp0logs"  mkdir "%~dp0logs"
if not exist "%~dp0scan_files" mkdir "%~dp0scan_files"
if not exist "%~dp0exports" mkdir "%~dp0exports"
if not exist "%~dp0uploads" mkdir "%~dp0uploads"

:: ========== 启动服务器 ==========
echo.
echo 正在启动 RFID 仓储管理系统 ...
echo 服务器地址: http://localhost:%PORT%
echo 按 Ctrl+C 停止服务器
echo.
"%~dp0RFID-WMS.exe"
BAT_EOF

cat > "$BUILD_DIR/stop.bat" << 'BAT_EOF'
@echo off
chcp 65001 >nul 2>&1
echo 正在停止 RFID 仓储管理系统 ...
taskkill /f /im RFID-WMS.exe >nul 2>&1
echo 服务器已停止，按任意键退出 ...
pause >nul
BAT_EOF

cat > "$BUILD_DIR/uninstall-firewall.bat" << 'BAT_EOF'
@echo off
chcp 65001 >nul 2>&1
for /f "usebackq tokens=1,* delims==" %%a in ("%~dp0config.ini") do (
    if /i "%%a"=="port" set PORT=%%b
)
if not defined PORT set PORT=3000
netsh advfirewall firewall delete rule name="RFID WMS %PORT%" >nul 2>&1
echo 防火墙规则已移除（端口 %PORT%）
pause
BAT_EOF

cat > "$BUILD_DIR/config.ini" << 'INI_EOF'
# RFID 仓储管理系统配置文件
# 安装后修改此文件，重启服务器生效

# 服务器端口（默认3000）
port=3000

# 服务器绑定地址（默认0.0.0.0局域网可访问，改为127.0.0.1仅本机访问）
host=0.0.0.0
INI_EOF

echo "  ✅ 启动/停止脚本已创建"

# ============================================================
# 6. 生成 Inno Setup 脚本（带安装向导 IP 配置页）
# ============================================================
echo "[6/7] 生成 Inno Setup 安装向导脚本..."

cat > "$DIST_DIR/rfid-wms-setup.iss" << 'ISS_EOF'
; ============================================================
; RFID 仓储管理系统 - Windows 安装向导
; 由 build-windows.sh 自动生成
; ============================================================
; 编译方式（在 Windows 上）：
;   1. 下载 Inno Setup: https://jrsoftware.org/isinfo.php
;   2. 右键此文件 -> Compile
;   3. 或命令行: iscc rfid-wms-setup.iss
; ============================================================

#define MyAppName "RFID 仓储管理系统"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "RFID WMS"
#define MyAppExeName "RFID-WMS.exe"
#define MyAppURL "https://github.com/rfid-wms"
#define BuildDir "..\backend\build"

[Setup]
; 基础信息
AppId={{B4C5D6E7-8A9B-0C1D-2E3F-4A5B6C7D8E9F}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}

; 安装路径
DefaultDirName={autopf}\RFID WMS
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes

; 输出文件
OutputDir=..\dist
OutputBaseFilename=RFID-WMS-Setup-{#MyAppVersion}
SetupIconFile=

; 安装选项
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\{#MyAppExeName}

; 许可协议（中英双语）
LicenseFile=
InfoBeforeFile=

; 向导背景
WizardImageFile=
WizardSmallImageFile=

[Languages]
Name: "chinesesimplified"; MessagesFile: "compiler:Languages\ChineseSimplified.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[CustomMessages]
chinesesimplified.ServerPort=服务器端口:
chinesesimplified.ServerPortHint= PDA 设备连接到此端口（默认 3000）
chinesesimplified.ServerIP=本机 IP 地址:
chinesesimplified.ServerIPHint=PDA 设备需填写此 IP 地址连接服务器
chinesesimplified.AutoFirewall=自动放行防火墙端口
chinesesimplified.AutoFirewallHint=允许局域网设备访问本系统（推荐）
chinesesimplified.CreateDesktopIcon=创建桌面快捷方式
chinesesimplified.LaunchAfterInstall=安装完成后立即启动
chinesesimplified.FirewallRuleAdded=防火墙端口已放行

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "快捷方式:"; Flags: checkedonce
Name: "firewall"; Description: "{cm:AutoFirewall}"; GroupDescription: "系统配置:"; Flags: checkedonce
Name: "launch"; Description: "{cm:LaunchAfterInstall}"; GroupDescription: "启动:"; Flags: checkedonce

[Files]
; 主程序
Source: "..\backend\build\RFID-WMS.exe"; DestDir: "{app}"; Flags: ignoreversion

; 配置文件（安装时写入用户填写的 IP/端口）
Source: "..\backend\build\config.ini"; DestDir: "{app}"; Flags: ignoreversion

; 启动脚本
Source: "..\backend\build\start.bat"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\backend\build\stop.bat"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\backend\build\uninstall-firewall.bat"; DestDir: "{app}"; Flags: ignoreversion

; 前端文件
Source: "..\backend\build\web\*"; DestDir: "{app}\web"; Flags: ignoreversion recursesubdirs createallsubdirs

; 运行时目录
Source: "..\backend\build\data\*"; DestDir: "{app}\data"; Flags: ignoreversion recursesubdirs createallsubdirs; Permissions: users-modify
Source: "..\backend\build\logs\*"; DestDir: "{app}\logs"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\backend\build\scan_files\*"; DestDir: "{app}\scan_files"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\backend\build\exports\*"; DestDir: "{app}\exports"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\backend\build\uploads\*"; DestDir: "{app}\uploads"; Flags: ignoreversion recursesubdirs createallsubdirs

; Node 原生模块（better-sqlite3）
Source: "..\backend\build\node_modules\better-sqlite3\*"; DestDir: "{app}\node_modules\better-sqlite3"; Flags: ignoreversion recursesubdirs createallsubdirs

; sql.js WASM
Source: "..\backend\build\node_modules\sql.js\*"; DestDir: "{app}\node_modules\sql.js"; Flags: ignoreversion recursesubdirs createallsubdirs

; Android APK（如有）
Source: "..\backend\build\public\*"; DestDir: "{app}\public"; Flags: ignoreversion recursesubdirs createallsubdirs skipifsourcedoesntexist

[Icons]
; 开始菜单
Name: "{group}\{#MyAppName}"; Filename: "{app}\start.bat"; WorkingDir: "{app}"; IconFilename: "{app}\{#MyAppExeName}"; Comment: "启动 RFID 仓储管理系统"
Name: "{group}\打开管理界面"; Filename: "http://localhost:{code:GetPort}/"; Flags: postinstall shellexec
Name: "{group}\停止服务器"; Filename: "{app}\stop.bat"; WorkingDir: "{app}"
Name: "{group}\卸载防火墙规则"; Filename: "{app}\uninstall-firewall.bat"; WorkingDir: "{app}"
Name: "{group}\卸载 {#MyAppName}"; Filename: "{uninstallexe}"

; 桌面快捷方式（带参数：端口）
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\start.bat"; WorkingDir: "{app}"; Tasks: desktopicon; IconFilename: "{app}\{#MyAppExeName}"; Comment: "启动 RFID 仓储管理系统"

[Run]
; 安装完成后启动（可选）
Filename: "{app}\start.bat"; Description: "{cm:LaunchAfterInstall}"; Flags: shellexec skipifsilent; Tasks: launch

[UninstallRun]
; 卸载时停止服务
Filename: "taskkill"; Parameters: "/f /im RFID-WMS.exe"; Flags: runhidden; RunOnceId: "StopService"
; 卸载防火墙规则
Filename: "{app}\uninstall-firewall.bat"; Flags: runhidden; RunOnceId: "UninstallFirewall"

[UninstallDelete]
; 卸载时删除运行时数据（可选，问用户）
Type: filesandordirs; Name: "{app}\data"
Type: filesandordirs; Name: "{app}\logs"
Type: filesandordirs; Name: "{app}\scan_files"
Type: filesandordirs; Name: "{app}\exports"
Type: filesandordirs; Name: "{app}\uploads"
Type: filesandordirs; Name: "{app}\node_modules"

[Registry]
; 卸载信息（用于"添加或删除程序"）
Root: HKLM; Subkey: "Software\Microsoft\Windows\CurrentVersion\Uninstall\{#MyAppName}"; ValueType: string; ValueName: "DisplayName"; ValueData: "{#MyAppName}"; Flags: uninsdeletekey
Root: HKLM; Subkey: "Software\Microsoft\Windows\CurrentVersion\Uninstall\{#MyAppName}"; ValueType: string; ValueName: "UninstallString"; ValueData: """{uninstallexe}"""
Root: HKLM; Subkey: "Software\Microsoft\Windows\CurrentVersion\Uninstall\{#MyAppName}"; ValueType: string; ValueName: "QuietUninstallString"; ValueData: """{uninstallexe}"" /SILENT"
Root: HKLM; Subkey: "Software\Microsoft\Windows\CurrentVersion\Uninstall\{#MyAppName}"; ValueType: string; ValueName: "InstallLocation"; ValueData: "{app}"
Root: HKLM; Subkey: "Software\Microsoft\Windows\CurrentVersion\Uninstall\{#MyAppName}"; ValueType: string; ValueName: "Publisher"; ValueData: "{#MyAppPublisher}"
Root: HKLM; Subkey: "Software\Microsoft\Windows\CurrentVersion\Uninstall\{#MyAppName}"; ValueType: string; ValueName: "DisplayVersion"; ValueData: "{#MyAppVersion}"
Root: HKLM; Subkey: "Software\Microsoft\Windows\CurrentVersion\Uninstall\{#MyAppName}"; ValueType: string; ValueName: "URLInfoAbout"; ValueData: "{#MyAppURL}"
Root: HKLM; Subkey: "Software\Microsoft\Windows\CurrentVersion\Uninstall\{#MyAppName}"; ValueType: string; ValueName: "DisplayIcon"; ValueData: "{app}\{#MyAppExeName}"

[Code]
var
  PortalPage: TInputQueryWizardPage;
  PortValue, IPValue: string;

// 获取 config.ini 中的端口
function GetPort(Param: string): string;
begin
  Result := PortValue;
end;

// 安装初始化
procedure InitializeWizard();
begin
  // ====== 第1步：端口配置页（系统配置） ======
  PortalPage := CreateInputQueryPage(
    wpWelcome,
    '系统配置',
    '配置服务器端口',
    '请设置本系统的服务端口（PDA 设备需使用此端口连接）',
    []
  );
  PortalPage.Add('端口 (1-65535):', False);
  PortalPage.Values[0] := '3000';

  // ====== 第2步：IP 配置页（网络信息） ======
  IPValue := '';
  CreateCustomPage(
    wpSelectTasks,
    '网络信息',
    '局域网 IP 地址'
  );

  MsgBox('当前电脑的局域网 IP 地址将显示在下一步。', mbInformation, MB_OK);
end;

// 安装前处理
function NextButtonClick(CurPageID: Integer): Boolean;
var
  ConfigFile: String;
  PortStr, HostStr: String;
  F: TextFile;
begin
  Result := True;

  // 在选择任务页之后（CurPageID = 自定义页）处理配置
  if CurPageID = PortalPage.ID then
  begin
    PortStr := Trim(PortalPage.Values[0]);

    // 端口校验
    if (PortStr = '') or (StrToIntDef(PortStr, 0) < 1) or (StrToIntDef(PortStr, 0) > 65535) then
    begin
      MsgBox('端口号无效，请输入 1-65535 之间的数字。', mbError, MB_OK);
      Result := False;
      Exit;
    end;

    PortValue := PortStr;
  end;
end;

// 安装过程中写配置文件
procedure CurStepChanged(CurStep: TSetupStep);
var
  ConfigFile: String;
  F: TextFile;
begin
  if CurStep = ssInstall then
  begin
    ConfigFile := ExpandConstant('{app}\config.ini');

    // 写入配置文件（含用户填写的端口）
    AssignFile(F, ConfigFile);
    Rewrite(F);
    WriteLn(F, '# RFID 仓储管理系统配置文件');
    WriteLn(F, '# 安装向导生成，安装后修改此文件，重启服务器生效');
    WriteLn(F, '');
    WriteLn(F, '# 服务器端口（默认3000）');
    WriteLn(F, 'port=' + PortValue);
    WriteLn(F, '');
    WriteLn(F, '# 服务器绑定地址（0.0.0.0=局域网可访问，127.0.0.1=仅本机）');
    WriteLn(F, 'host=0.0.0.0');
    CloseFile(F);
  end;
end;

// 安装完成后：放行防火墙（仅当勾选 firewall 任务时）
procedure CurPageChanged(CurPageID: Integer);
var
  ResultCode: Integer;
begin
  // 在最终完成页执行防火墙规则
  if CurPageID = wpFinished then
  begin
    if ShouldInstallTask('firewall') then
    begin
      Exec('netsh', 'advfirewall firewall add rule name="RFID WMS ' + PortValue + '" dir=in action=allow protocol=TCP localport=' + PortValue, '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
      MsgBox('防火墙端口 ' + PortValue + ' 已放行完毕！', mbInformation, MB_OK);
    end;
  end;
end;
ISS_EOF

echo "  ✅ Inno Setup 脚本已生成: $DIST_DIR/rfid-wms-setup.iss"

# ============================================================
# 7. 自动编译（如 makensis 可用）
# ============================================================
if command -v makensis &> /dev/null; then
    echo "[7/7] 自动编译安装包（makensis）..."
    cd "$DIST_DIR"
    makensis rfid-wms-setup.iss
    echo ""
    echo "=========================================="
    echo "   ✅ 安装包构建成功！"
    echo "=========================================="
    echo "  安装包: $DIST_DIR/RFID-WMS-Setup-$VERSION.exe"
    echo ""
else
    echo "[7/7] makensis 未安装，跳过自动编译"
    echo ""
    echo "=========================================="
    echo "   ✅ 构建文件准备完毕"
    echo "=========================================="
    echo ""
    echo "  exe 程序:      $BUILD_DIR/$APP_NAME.exe"
    echo "  Inno Setup 脚本: $DIST_DIR/rfid-wms-setup.iss"
    echo ""
    echo "  在 Windows 上编译安装包："
    echo "  1. 下载 Inno Setup: https://jrsoftware.org/isinfo.php"
    echo "  2. 安装后双击 rfid-wms-setup.iss"
    echo "  3. 或命令行: iscc rfid-wms-setup.iss"
    echo ""
fi

echo "  完整分发包: $BUILD_DIR/"
echo ""
