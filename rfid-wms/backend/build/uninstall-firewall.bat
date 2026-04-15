@echo off
chcp 65001 >nul 2>&1
for /f "usebackq tokens=1,* delims==" %%a in ("%~dp0config.ini") do (
    if /i "%%a"=="port" set PORT=%%b
)
if not defined PORT set PORT=3000
netsh advfirewall firewall delete rule name="RFID资产盘点 %PORT%" >nul 2>&1
echo 防火墙规则已移除（端口 %PORT%）
pause
