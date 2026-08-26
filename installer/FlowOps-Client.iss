#define AppName "FlowOps Client"
#define AppPublisher "FlowOps Contributors"
#define AppId "{E19A26B7-8D54-4D17-9B90-A56A8F82E91B}"
#ifndef AppVersion
#define AppVersion "1.1.2"
#endif

[Setup]
AppId={{#AppId}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
DefaultDirName={localappdata}\Programs\FlowOps Client
DefaultGroupName=FlowOps
SetupIconFile=FlowOps.ico
UninstallDisplayIcon={app}\FlowOps.ico
DisableDirPage=yes
DisableProgramGroupPage=yes
OutputDir=..\dist\installer
OutputBaseFilename=FlowOps-Client-Setup
PrivilegesRequired=lowest
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
VersionInfoDescription=FlowOps lightweight Windows client installer
VersionInfoVersion={#AppVersion}
CloseApplications=yes
RestartApplications=no

[Files]
Source: "..\client\FlowOps-Client.ps1"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\client\FlowOps-Client.ps1"; Flags: dontcopy
Source: "FlowOps.ico"; DestDir: "{app}"; Flags: ignoreversion
Source: "native\*.dll"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\artifacts\agent\win-x64\*"; DestDir: "{app}"; Excludes: "*.pdb,FlowOps-Client.ps1,FlowOps.ico"; Flags: ignoreversion recursesubdirs createallsubdirs

[Dirs]
Name: "{localappdata}\FlowOps Client"

[Icons]
Name: "{group}\FlowOps Client"; Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{app}\FlowOps-Client.ps1"""; WorkingDir: "{app}"; IconFilename: "{app}\FlowOps.ico"; IconIndex: 0
Name: "{group}\Configure FlowOps Client"; Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\FlowOps-Client.ps1"" -Configure"; WorkingDir: "{app}"; IconFilename: "{app}\FlowOps.ico"; IconIndex: 0
Name: "{group}\FlowOps Notification Agent"; Filename: "{app}\FlowOps.NotificationAgent.exe"; WorkingDir: "{app}"; IconFilename: "{app}\FlowOps.ico"; IconIndex: 0
Name: "{userdesktop}\FlowOps Client"; Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{app}\FlowOps-Client.ps1"""; WorkingDir: "{app}"; IconFilename: "{app}\FlowOps.ico"; IconIndex: 0; Tasks: desktopicon

[InstallDelete]
Type: files; Name: "{group}\FlowOps.lnk"
Type: files; Name: "{userdesktop}\FlowOps.lnk"

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"; Flags: checkedonce
Name: "agentautostart"; Description: "Start FlowOps Notification Agent when I sign in to Windows"; GroupDescription: "Background notifications:"; Flags: checkedonce

[Registry]
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: string; ValueName: "FlowOps Notification Agent"; ValueData: """{app}\FlowOps.NotificationAgent.exe"""; Flags: uninsdeletevalue; Tasks: agentautostart
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: none; ValueName: "FlowOps Notification Agent"; Flags: deletevalue; Tasks: not agentautostart

[UninstallRun]
Filename: "{cmd}"; Parameters: "/c taskkill /IM FlowOps.NotificationAgent.exe /F"; Flags: runhidden waituntilterminated; RunOnceId: "StopNotificationAgent"

[Code]
var
  ServerPage: TInputQueryWizardPage;
  TestButton: TNewButton;
  TestStatus: TNewStaticText;

function PowerShellPath: String;
begin
  Result := ExpandConstant('{sys}\WindowsPowerShell\v1.0\powershell.exe');
end;

function IsValidServerUrl(const Value: String): Boolean;
var
  Normalized: String;
begin
  Normalized := Lowercase(Trim(Value));
  Result := (((Pos('http://', Normalized) = 1) or (Pos('https://', Normalized) = 1)) and
    (Length(Normalized) > 8) and (Pos(' ', Normalized) = 0) and
    (Pos('"', Normalized) = 0) and (Pos('@', Normalized) = 0));
end;

procedure TestConnectionClick(Sender: TObject);
var
  Code: Integer;
  Parameters: String;
begin
  if not IsValidServerUrl(ServerPage.Values[0]) then
  begin
    TestStatus.Caption := 'Enter a complete HTTP or HTTPS server address first.';
    TestStatus.Font.Color := clRed;
    Exit;
  end;
  TestStatus.Caption := 'Testing connection...';
  TestStatus.Font.Color := clGray;
  WizardForm.Refresh;
  Parameters := '-NoProfile -ExecutionPolicy Bypass -File "' +
    ExpandConstant('{tmp}\FlowOps-Client.ps1') + '" -Test -Url "' +
    Trim(ServerPage.Values[0]) + '"';
  if Exec(PowerShellPath, Parameters, '', SW_HIDE, ewWaitUntilTerminated, Code) and (Code = 0) then
  begin
    TestStatus.Caption := 'Connected to FlowOps.';
    TestStatus.Font.Color := clGreen;
  end
  else
  begin
    TestStatus.Caption := 'Unable to reach FlowOps server. You may correct the address or continue and test later.';
    TestStatus.Font.Color := clRed;
  end;
end;

procedure InitializeWizard;
begin
  ExtractTemporaryFile('FlowOps-Client.ps1');
  ServerPage := CreateInputQueryPage(wpWelcome, 'FlowOps Server Address',
    'Connect this PC to the central FlowOps server',
    'Enter the HTTP or HTTPS address supplied by your company administrator. No login is required during setup.');
  ServerPage.Add('FlowOps Server Address:', False);
  ServerPage.Values[0] := 'http://flowops-server:8080';
  TestButton := TNewButton.Create(ServerPage.Surface);
  TestButton.Parent := ServerPage.Surface;
  TestButton.Caption := 'Test Connection';
  TestButton.Left := 0;
  TestButton.Top := ServerPage.Edits[0].Top + ServerPage.Edits[0].Height + ScaleY(18);
  TestButton.Width := ScaleX(120);
  TestButton.OnClick := @TestConnectionClick;
  TestStatus := TNewStaticText.Create(ServerPage.Surface);
  TestStatus.Parent := ServerPage.Surface;
  TestStatus.Left := 0;
  TestStatus.Top := TestButton.Top + TestButton.Height + ScaleY(12);
  TestStatus.Width := ServerPage.SurfaceWidth;
  TestStatus.Height := ScaleY(46);
  TestStatus.AutoSize := False;
  TestStatus.WordWrap := True;
  TestStatus.Caption := 'The client stores only this server URL.';
end;

function NextButtonClick(CurPageID: Integer): Boolean;
begin
  Result := True;
  if (CurPageID = ServerPage.ID) and not IsValidServerUrl(ServerPage.Values[0]) then
  begin
    MsgBox('Enter a complete HTTP or HTTPS FlowOps server address, for example http://flowops-server:8080.', mbError, MB_OK);
    Result := False;
  end;
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  ConfigPath: String;
  Code: Integer;
begin
  if CurStep = ssInstall then
    Exec(ExpandConstant('{cmd}'), '/c taskkill /IM FlowOps.NotificationAgent.exe /F', '', SW_HIDE, ewWaitUntilTerminated, Code)
  else if CurStep = ssPostInstall then
  begin
    ConfigPath := ExpandConstant('{localappdata}\FlowOps Client\server-url.txt');
    if not SaveStringToFile(ConfigPath, Trim(ServerPage.Values[0]), False) then
    begin
      MsgBox('The FlowOps server address could not be saved.', mbError, MB_OK);
      Abort;
    end;
    { Always restore the tray agent after an install/repair. The autostart task
      only controls future sign-ins and must not control the current session. }
    Exec(ExpandConstant('{app}\FlowOps.NotificationAgent.exe'), '',
      ExpandConstant('{app}'), SW_HIDE, ewNoWait, Code);
  end;
end;

procedure CurPageChanged(CurPageID: Integer);
var
  ExistingConfig: String;
  ExistingValue: AnsiString;
begin
  if (CurPageID = ServerPage.ID) then
  begin
    ExistingConfig := ExpandConstant('{localappdata}\FlowOps Client\server-url.txt');
    if FileExists(ExistingConfig) then
      if LoadStringFromFile(ExistingConfig, ExistingValue) then
        ServerPage.Values[0] := String(ExistingValue);
  end;
end;
