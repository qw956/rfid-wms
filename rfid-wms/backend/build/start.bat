@echo off
chcp 65001 >nul 2>&1
title RFID资产盘点

:: 读取配置
for /f "usebackq tokens=1,* delims==" %%a in ("%~dp0config.ini") do (
    if /i "%%a"=="port" set "PORT=%%b"
    if /i "%%a"=="host" set "HOST=%%b"
)
if not defined PORT set PORT=3000
if not defined HOST set HOST=0.0.0.0

:: 防火墙放行
echo [防火墙] 放行端口 %PORT% ...
netsh advfirewall firewall show rule name="RFID资产盘点 %PORT%" >nul 2>&1
if errorlevel 1 (
    netsh advfirewall firewall add rule name="RFID资产盘点 %PORT%" dir=in action=allow protocol=TCP localport=%PORT% >nul 2>&1
    echo [OK] 端口 %PORT% 防火墙规则已添加
) else (
    echo [OK] 端口 %PORT% 防火墙规则已存在
)

:: 确保目录存在
for %%d in (data logs scan_files exports uploads) do (
    if not exist "%~dp0%%d" mkdir "%~dp0%%d"
)

echo.
echo 启动 RFID 仓储管理系统 ...
echo 访问地址: http://localhost:%PORT%
echo 按 Ctrl+C 停止服务器
echo.
"%~dp0RFID-WMS.exe"
