@echo off
chcp 65001 >nul 2>&1
title RFID WMS

:: Read config
set PORT=3000
set HOST=0.0.0.0
for /f "usebackq tokens=1,* delims==" %%a in ("%~dp0config.ini") do (
    if /i "%%a"=="port" set "PORT=%%b"
    if /i "%%a"=="host" set "HOST=%%b"
)

:: Firewall
echo [Firewall] Opening port %PORT% ...
netsh advfirewall firewall show rule name="RFIDWMS" >nul 2>&1
if errorlevel 1 (
    netsh advfirewall firewall add rule name="RFIDWMS" dir=in action=allow protocol=TCP localport=%PORT% >nul 2>&1
    echo OK: Port %PORT% firewall rule added
) else (
    echo OK: Port %PORT% firewall rule already exists
)

:: Create dirs
for %%d in (data logs scan_files exports uploads) do (
    if not exist "%~dp0%%d" mkdir "%~dp0%%d"
)

echo.
echo Starting RFID WMS ...
echo Access: http://localhost:%PORT%
echo Press Ctrl+C to stop
echo.
start "" "%~dp0RFID-WMS.exe"
exit
