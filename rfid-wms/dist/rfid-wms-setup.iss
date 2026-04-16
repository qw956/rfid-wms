; ============================================================
;  RFID 仓储管理系统 - Windows 安装向导
;  由 build-windows.sh 自动生成
;
;  编译方式（在 Windows 上）：
;    1. 下载 Inno Setup: https://jrsoftware.org/isinfo.php
;    2. 安装后双击此文件，或命令行执行：
;       iscc rfid-wms-setup.iss
; ============================================================

#define MyAppName      "RFID资产盘点"
#define MyAppVersion   "1.0.0"
#define MyAppPublisher "RFID WMS"
#define MyAppExeName   "RFID-WMS.exe"
#define MyAppURL       "https://github.com/rfid-wms"

[Setup]
; ---------- 基础 ----------
AppId={{B4C5D6E7-8A9B-0C1D-2E3F-4A5B6C7D8E9F}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}

; ---------- 路径 ----------
DefaultDirName={autopf}\RFID资产盘点
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes

; ---------- 输出 ----------
OutputDir=.
OutputBaseFilename=RFID资产盘点-Setup-{#MyAppVersion}

; ---------- 安装选项 ----------
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\{#MyAppExeName}
SetupLogging=yes

; ---------- 语言 ----------
[Languages]
Name: "chinesesimplified"; MessagesFile: "compiler:Languages\ChineseSimplified.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[CustomMessages]
; ---------- 中文标签 ----------
chinesesimplified.lblPort=服务器端口:
chinesesimplified.hintPort=请输入服务端口，PDA 设备连接时使用此端口（默认 3000）
chinesesimplified.lblIP=本机局域网 IP:
chinesesimplified.hintIP=PDA 设备需填写此 IP 连接服务器（本机自动检测）
chinesesimplified.lblFirewall=放行防火墙端口
chinesesimplified.hintFirewall=允许局域网内其他设备访问本系统（推荐启用）
chinesesimplified.lblDesktop=创建桌面快捷方式
chinesesimplified.lblAutoRun=开机自动启动
chinesesimplified.doneTitle=安装完成
chinesesimplified.doneText=点击"完成"将启动 RFID 仓储管理系统。\n\n访问地址: http://localhost:%1\nPDA 连接地址: http://%2:%1
chinesesimplified.firewallOK=防火墙端口 %1 已放行
chinesesimplified.firewallSkip=已跳过防火墙配置，请手动放行端口
chinesesimplified.badPort=端口号无效，请输入 1-65535 之间的数字

[Tasks]
Name: "firewall";  Description: "{cm:lblFirewall}";   GroupDescription: "系统配置:";  Flags: checkedonce
Name: "desktop";   Description: "{cm:lblDesktop}";   GroupDescription: "快捷方式:"; Flags: checkedonce
Name: "autorun";   Description: "{cm:lblAutoRun}";   GroupDescription: "快捷方式:"; Flags: unchecked

[Files]
; 主程序（单文件）
Source: "..\backend\build\RFID-WMS.exe";          DestDir: "{app}";                     Flags: ignoreversion

; 配置 & 启动脚本（安装时由 [Code] 动态写入）
Source: "..\backend\build\config.ini";           DestDir: "{app}";                     Flags: ignoreversion
Source: "..\backend\build\start.bat";            DestDir: "{app}";                     Flags: ignoreversion
Source: "..\backend\build\stop.bat";             DestDir: "{app}";                     Flags: ignoreversion
Source: "..\backend\build\uninstall-firewall.bat"; DestDir: "{app}";                  Flags: ignoreversion

; 前端页面
Source: "..\backend\build\web\*";                DestDir: "{app}\web";                  Flags: ignoreversion recursesubdirs createallsubdirs

; 运行时目录
Source: "..\backend\build\data\*";                DestDir: "{app}\data";                 Flags: ignoreversion recursesubdirs createallsubdirs; Permissions: users-modify
Source: "..\backend\build\logs\*";               DestDir: "{app}\logs";                 Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\backend\build\scan_files\*";         DestDir: "{app}\scan_files";           Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\backend\build\exports\*";           DestDir: "{app}\exports";              Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\backend\build\uploads\*";            DestDir: "{app}\uploads";              Flags: ignoreversion recursesubdirs createallsubdirs

; sql.js WASM 运行时（pkg exe 需加载）
Source: "..\backend\build\node_modules\sql.js\dist\*"; DestDir: "{app}\node_modules\sql.js\dist"; Flags: ignoreversion recursesubdirs createallsubdirs skipifsourcedoesntexist

; Android APK（如有）
Source: "..\backend\build\public\*";             DestDir: "{app}\public";               Flags: ignoreversion recursesubdirs createallsubdirs skipifsourcedoesntexist

[Icons]
; 开始菜单组
Name: "{group}\{#MyAppName}";            Filename: "{app}\start.bat";          WorkingDir: "{app}"; IconFilename: "{app}\{#MyAppExeName}"
Name: "{group}\停止服务器";              Filename: "{app}\stop.bat";            WorkingDir: "{app}"
Name: "{group}\卸载防火墙规则";          Filename: "{app}\uninstall-firewall.bat"; WorkingDir: "{app}"
Name: "{group}\卸载 {#MyAppName}";       Filename: "{uninstallexe}"

; 桌面快捷方式
Name: "{autodesktop}\{#MyAppName}";     Filename: "{app}\start.bat";          WorkingDir: "{app}"; Tasks: desktop; IconFilename: "{app}\{#MyAppExeName}"

[Run]
Filename: "{app}\start.bat";             Description: "立即启动 {#MyAppName}";  Flags: shellexec skipifsilent postinstall nowait; Tasks:

[UninstallRun]
; 停止服务
Filename: "taskkill"; Parameters: "/f /im RFID-WMS.exe"; Flags: runhidden; RunOnceId: "StopService"
; 移除防火墙规则
Filename: "{app}\uninstall-firewall.bat"; Flags: runhidden; RunOnceId: "UninstallFirewall"

[UninstallDelete]
Type: filesandordirs; Name: "{app}\data"
Type: filesandordirs; Name: "{app}\logs"
Type: filesandordirs; Name: "{app}\scan_files"
Type: filesandordirs; Name: "{app}\exports"
Type: filesandordirs; Name: "{app}\uploads"
Type: filesandordirs; Name: "{app}\node_modules"

; ========== [Registry] 段：添加或删除程序项 ==========
[Registry]
Root: HKLM; Subkey: "Software\Microsoft\Windows\CurrentVersion\Uninstall\{#MyAppName}"; ValueType: string; ValueName: "DisplayName"; ValueData: "{#MyAppName}"; Flags: uninsdeletekey
Root: HKLM; Subkey: "Software\Microsoft\Windows\CurrentVersion\Uninstall\{#MyAppName}"; ValueType: string; ValueName: "UninstallString"; ValueData: """{uninstallexe}"""
Root: HKLM; Subkey: "Software\Microsoft\Windows\CurrentVersion\Uninstall\{#MyAppName}"; ValueType: string; ValueName: "QuietUninstallString"; ValueData: """{uninstallexe}"" /SILENT"
Root: HKLM; Subkey: "Software\Microsoft\Windows\CurrentVersion\Uninstall\{#MyAppName}"; ValueType: string; ValueName: "InstallLocation"; ValueData: "{app}"
Root: HKLM; Subkey: "Software\Microsoft\Windows\CurrentVersion\Uninstall\{#MyAppName}"; ValueType: string; ValueName: "Publisher"; ValueData: "{#MyAppPublisher}"
Root: HKLM; Subkey: "Software\Microsoft\Windows\CurrentVersion\Uninstall\{#MyAppName}"; ValueType: string; ValueName: "DisplayVersion"; ValueData: "{#MyAppVersion}"
Root: HKLM; Subkey: "Software\Microsoft\Windows\CurrentVersion\Uninstall\{#MyAppName}"; ValueType: string; ValueName: "DisplayIcon"; ValueData: "{app}\{#MyAppExeName}"

; ========== [Code] 段：安装向导交互 ==========
[Code]
var
  ConfigPage: TOutputMsgWizardPage;     // 向导第2页：系统配置
  edtPort: TNewEdit;
  lblPort, lblHint: TNewStaticText;
  chkFirewall: TNewCheckbox;

  FinishPage: TOutputMsgWizardPage;    // 完成页：显示 IP+端口
  lblAccessURL: TNewStaticText;

  SavedPort: string;

procedure InitializeWizard();
begin
  // ============================================================
  // 第2页：系统配置（端口 + 防火墙）
  // ============================================================
  ConfigPage := CreateOutputMsgPage(
    wpSelectDir,
    '系统配置',
    '配置服务器端口和防火墙策略',
    '请设置本系统的运行参数，完成后点击"下一步"继续安装。' + #13#10 +
    '' + #13#10 +
    '【重要】请记录以下信息用于 PDA 设备配置：' + #13#10 +
    '  服务器地址 = 本机局域网 IP' + #13#10 +
    '  端口       = 下方的端口号（默认 3000）',
    False
  );

  // 端口标签
  lblPort := TNewStaticText.Create(WizardForm);
  lblPort.Parent := ConfigPage.Surface;
  lblPort.Caption := '服务器端口 (1-65535):';
  lblPort.AutoSize := True;
  lblPort.Left := ConfigPage.Left + 20;
  lblPort.Top := ConfigPage.Top + 100;

  // 端口输入框
  edtPort := TNewEdit.Create(WizardForm);
  edtPort.Parent := ConfigPage.Surface;
  edtPort.Text := '3000';
  edtPort.Width := 100;
  edtPort.Left := lblPort.Left;
  edtPort.Top := lblPort.Top + lblPort.Height + 6;

  // 端口提示
  lblHint := TNewStaticText.Create(WizardForm);
  lblHint.Parent := ConfigPage.Surface;
  lblHint.Caption := 'PDA 设备连接到此端口（默认 3000，无需修改）';
  lblHint.Font.Color := clNavy;
  lblHint.AutoSize := True;
  lblHint.Left := edtPort.Left;
  lblHint.Top := edtPort.Top + edtPort.Height + 4;

  // 防火墙复选框（对应 Tasks）
  chkFirewall := TNewCheckbox.Create(WizardForm);
  chkFirewall.Parent := ConfigPage.Surface;
  chkFirewall.Caption := '自动放行防火墙端口（推荐）';
  chkFirewall.Hint := '允许局域网设备访问本系统';
  chkFirewall.Checked := True;
  chkFirewall.Width := ConfigPage.SurfaceWidth - 40;
  chkFirewall.Left := edtPort.Left;
  chkFirewall.Top := lblHint.Top + lblHint.Height + 20;

  // 说明文字
  with TNewStaticText.Create(WizardForm) do
  begin
    Parent := ConfigPage.Surface;
    Caption := '注：安装完成后，配置文件 config.ini 位于安装目录下，' + #13#10 +
               '修改后重启服务器即可生效。';
    Font.Color := clGrayText;
    AutoSize := True;
    Left := edtPort.Left;
    Top := chkFirewall.Top + chkFirewall.Height + 16;
  end;

  // ============================================================
  // 第4页（最后）：安装完成
  // ============================================================
  FinishPage := CreateOutputMsgPage(
    wpFinished,
    '安装完成',
    'RFID 仓储管理系统已安装成功',
    '',
    True
  );

  // 访问地址（动态填充）
  lblAccessURL := TNewStaticText.Create(WizardForm);
  lblAccessURL.Parent := FinishPage.Surface;
  lblAccessURL.AutoSize := False;
  lblAccessURL.Width := FinishPage.SurfaceWidth;
  lblAccessURL.Height := 80;
  lblAccessURL.Left := FinishPage.Left + 20;
  lblAccessURL.Top := FinishPage.Top + 60;
  lblAccessURL.Font.Size := 10;
  lblAccessURL.Font.Color := clNavy;
  lblAccessURL.Caption := '';
end;

// 下一步按钮点击：校验端口
function NextButtonClick(CurPageID: Integer): Boolean;
var
  portStr: string;
  portNum: Integer;
begin
  Result := True;

  if CurPageID = ConfigPage.ID then
  begin
    portStr := Trim(edtPort.Text);

    // 端口校验
    if (portStr = '') or (not TryStrToInt(portStr, portNum)) or
       (portNum < 1) or (portNum > 65535) then
    begin
      MsgBox('{cm:badPort}', mbError, MB_OK);
      edtPort.SetFocus;
      Result := False;
      Exit;
    end;

    SavedPort := portStr;
    // 更新防火墙任务状态
    if chkFirewall.Checked then
      WizardForm.DoTask('firewall')
    else
      WizardForm.UncheckTask('firewall');
  end;
end;

// 安装过程中写入配置文件
procedure CurStepChanged(CurStep: TSetupStep);
var
  ConfigPath: string;
  F: TextFile;
begin
  if CurStep = ssInstall then
  begin
    ConfigPath := ExpandConstant('{app}\config.ini');

    // 写入含端口配置的文件（覆盖模板）
    AssignFile(F, ConfigPath);
    Rewrite(F);
    WriteLn(F, '# RFID 仓储管理系统配置文件');
    WriteLn(F, '# 安装向导生成，安装后修改此文件，重启服务器生效');
    WriteLn(F, '');
    WriteLn(F, '# 服务器端口（默认3000）');
    WriteLn(F, 'port=' + SavedPort);
    WriteLn(F, '');
    WriteLn(F, '# 服务器绑定地址（0.0.0.0=局域网可访问，127.0.0.1=仅本机）');
    WriteLn(F, 'host=0.0.0.0');
    CloseFile(F);
  end;
end;

// 完成页显示时：执行防火墙 + 显示访问地址
procedure CurPageChanged(CurPageID: Integer);
var
  ResultCode: Integer;
  ip: string;
  InfoPage: TWizardPage;
begin
  if CurPageID = FinishPage.ID then
  begin
    // 默认局域网 IP，安装后可手动修改
    ip := '192.168.1.100';

    // 显示访问信息
    lblAccessURL.Caption :=
      '============================================' + #13#10 +
      '  访问地址（电脑浏览器）' + #13#10 +
      '  http://localhost:' + SavedPort + #13#10 +
      '' + #13#10 +
      '  PDA 设备连接地址（手持机配置用）' + #13#10 +
      '  http://' + ip + ':' + SavedPort + #13#10 +
      '============================================' + #13#10 +
      '' + #13#10 +
      '配置文件位置: {app}\config.ini' + #13#10 +
      '修改端口后重启服务器即可生效';

    // 执行防火墙放行（如勾选）
    if chkFirewall.Checked then
    begin
      Exec('netsh',
           'advfirewall firewall add rule name="RFID资产盘点 ' + SavedPort +
           '" dir=in action=allow protocol=TCP localport=' + SavedPort,
           '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
      MsgBox('防火墙端口 ' + SavedPort + ' 已放行！', mbInformation, MB_OK);
    end;
  end;
end;
