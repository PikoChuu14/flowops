#define AppName "FlowOps"
#ifndef AppVersion
  #error AppVersion must be supplied by scripts\build-installer.ps1
#endif
#ifndef AppVersionNumeric
  #error AppVersionNumeric must be supplied by scripts\build-installer.ps1
#endif
#define AppPublisher "FlowOps Contributors"
; Permanent product identity. Keep this value unchanged for every release.
#define AppId "{8B58D1C2-7FD1-4CF7-9B49-0B2AE24C1A4E}"

[Setup]
AppId={{#AppId}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
DefaultDirName={autopf}\FlowOps
DefaultGroupName=FlowOps
SetupIconFile=FlowOps.ico
UninstallDisplayIcon={app}\FlowOps.ico
UsePreviousAppDir=no
DisableDirPage=yes
DisableProgramGroupPage=yes
OutputDir=..\dist\installer
OutputBaseFilename=FlowOps-Setup-{#AppVersion}
UninstallDisplayName=FlowOps
PrivilegesRequired=admin
ArchitecturesInstallIn64BitMode=x64compatible
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
VersionInfoVersion={#AppVersionNumeric}
VersionInfoDescription=FlowOps installer

[Files]
Source: "payload\app\*"; DestDir: "{app}\app"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "payload\runtime\*"; DestDir: "{app}\runtime"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "payload\FlowOps.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "payload\FlowOps.xml"; DestDir: "{app}"; Flags: ignoreversion
Source: "FlowOps.ico"; DestDir: "{app}"; Flags: ignoreversion
Source: "payload\tools\*"; DestDir: "{app}\tools"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "payload\prerequisites\postgresql-installer.exe"; DestDir: "{tmp}"; Flags: deleteafterinstall
Source: "scripts\detect-postgresql.ps1"; DestDir: "{tmp}"; Flags: dontcopy
Source: "scripts\inspect-flowops.ps1"; DestDir: "{tmp}"; Flags: dontcopy
Source: "..\START_HERE.txt"; DestDir: "{app}\docs"; Flags: ignoreversion
Source: "..\DEPLOYMENT.md"; DestDir: "{app}\docs"; Flags: ignoreversion
Source: "..\BACKUP_RESTORE.md"; DestDir: "{app}\docs"; Flags: ignoreversion
Source: "..\TROUBLESHOOTING.md"; DestDir: "{app}\docs"; Flags: ignoreversion
Source: "..\ADMIN_GUIDE.md"; DestDir: "{app}\docs"; Flags: ignoreversion
Source: "..\VERSION.txt"; DestDir: "{app}\docs"; Flags: ignoreversion

[Dirs]
Name: "{commonappdata}\FlowOps\config"
Name: "{commonappdata}\FlowOps\logs"
Name: "{commonappdata}\FlowOps\backups"
Name: "{commonappdata}\FlowOps\runtime"

[Icons]
Name: "{group}\Open FlowOps Server"; Filename: "{sys}\cmd.exe"; Parameters: "/c start http://localhost:{code:GetPort}"; IconFilename: "{app}\FlowOps.ico"
Name: "{group}\Backup FlowOps"; Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\tools\backup-installed.ps1"""
Name: "{group}\Restore FlowOps"; Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\tools\restore-installed.ps1"""
Name: "{group}\FlowOps Documentation"; Filename: "{app}\docs"
Name: "{commondesktop}\FlowOps Server"; Filename: "{sys}\cmd.exe"; Parameters: "/c start http://localhost:{code:GetPort}"; IconFilename: "{app}\FlowOps.ico"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"

[InstallDelete]
; Remove shortcuts created by the pre-rebrand installer. Both names launch the
; same FlowOps service, so retaining the legacy shortcut looks like a duplicate app.
Type: files; Name: "{commondesktop}\Kovax FlowOps.lnk"
Type: files; Name: "{userdesktop}\Kovax FlowOps.lnk"
Type: files; Name: "{commondesktop}\FlowOps.lnk"
; Do not delete {userdesktop}\FlowOps.lnk here. That name belongs to the
; independently installed per-user FlowOps Client on machines hosting both.

[UninstallRun]
Filename: "{app}\FlowOps.exe"; Parameters: "stop"; Flags: runhidden waituntilterminated
Filename: "{app}\FlowOps.exe"; Parameters: "uninstall"; Flags: runhidden waituntilterminated
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""FlowOps"""; Flags: runhidden waituntilterminated
; Legacy pre-rebrand firewall rule cleanup.
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""Kovax FlowOps"""; Flags: runhidden waituntilterminated

[Code]
var
  DbChoicePage: TWizardPage;
  DbIntroLabel: TNewStaticText;
  DbInstalledRadio: TNewRadioButton;
  DbAutomaticRadio: TNewRadioButton;
  DbAdminPage: TInputQueryWizardPage;
  AdminPage: TInputQueryWizardPage;
  DbStatusLabel: TNewStaticText;
  FlowDbPage: TWizardPage;
  FlowDbIntroLabel: TNewStaticText;
  FlowDbExistingRadio: TNewRadioButton;
  FlowDbNewRadio: TNewRadioButton;
  FlowDbConfirmLabel: TNewStaticText;
  PostgreSQLDetected: Boolean;
  InstallPostgres: Boolean;
  PgBinDir: String;
  PgServiceName: String;
  PgServiceStatus: String;
  PgMajorVersion: String;
  PgDetectionMessage: String;
  PgDetectionSummary: String;
  PgDiagnosticSummary: String;
  AppPort: String;
  DataRoot: String;
  LegacyDataRoot: String;
  StartupTitleLabel: TNewStaticText;
  StartupSpinner: TNewStaticText;
  StartupStatusLabel: TNewStaticText;
  StartupExplanationLabel: TNewStaticText;
  StartupOpenButton: TNewButton;
  StartupRetryButton: TNewButton;
  StartupLogsButton: TNewButton;
  StartupTimerID: LongWord;
  StartupSpinnerFrame: Integer;
  StartupTimerTicks: Integer;
  StartupAttempt: Integer;
  StartupStateFile: String;
  StartupReady: Boolean;
  StartupTimedOut: Boolean;
  AppInstalled: Boolean;
  ServiceDetected: Boolean;
  ProgramDataDetected: Boolean;
  FlowOpsDatabaseDetected: Boolean;
  FlowOpsDataDetected: Boolean;
  ExistingAdminDetected: Boolean;
  InstallMode: String;
  InstalledVersion: String;
  UseExistingDatabase: Boolean;
  NewDatabaseConfirmed: Boolean;

function SetTimer(hWnd, nIDEvent, uElapse, lpTimerFunc: LongWord): LongWord;
  external 'SetTimer@user32.dll stdcall';
function KillTimer(hWnd, nIDEvent: LongWord): Boolean;
  external 'KillTimer@user32.dll stdcall';

function PsPath: String;
begin Result := ExpandConstant('{sys}\WindowsPowerShell\v1.0\powershell.exe'); end;

function JsonEscape(S: String): String;
begin
  StringChangeEx(S, '\', '\\', True); StringChangeEx(S, '"', '\"', True);
  StringChangeEx(S, #8, '\b', True); StringChangeEx(S, #9, '\t', True);
  StringChangeEx(S, #12, '\f', True);
  StringChangeEx(S, #13, '\r', True); StringChangeEx(S, #10, '\n', True); Result := S;
end;

function GetPort(Param: String): String;
begin Result := AppPort; end;

function BooleanText(Value: Boolean): String;
begin
  if Value then Result := 'True' else Result := 'False';
end;

function DatabaseModeText(Value: Boolean): String;
begin
  if Value then Result := 'existing' else Result := 'new';
end;

function JsonBoolean(Value: Boolean): String;
begin
  if Value then Result := 'true' else Result := 'false';
end;

function VersionPart(const Value: String; PartIndex: Integer): Integer;
var I, StartAt, CurrentPart: Integer; Token: String;
begin
  StartAt := 1; CurrentPart := 0;
  for I := 1 to Length(Value) + 1 do
    if (I > Length(Value)) or (Value[I] = '.') then
    begin
      if CurrentPart = PartIndex then
      begin
        Token := Copy(Value, StartAt, I - StartAt);
        Result := StrToIntDef(Token, -1);
        if Result < 0 then Result := 1;
        Exit;
      end;
      CurrentPart := CurrentPart + 1;
      StartAt := I + 1;
    end;
  if (PartIndex = 3) and (StartAt <= Length(Value)) then Result := 1
  else Result := 0;
end;

function CompareVersionText(const LeftValue, RightValue: String): Integer;
var I, LeftPart, RightPart: Integer;
begin
  for I := 0 to 3 do
  begin
    LeftPart := VersionPart(LeftValue, I);
    RightPart := VersionPart(RightValue, I);
    if LeftPart < RightPart then begin Result := -1; Exit; end;
    if LeftPart > RightPart then begin Result := 1; Exit; end;
  end;
  Result := 0;
end;

function InstallModeMessage: String;
begin
  if InstallMode = 'upgrade' then Result := 'FlowOps ' + InstalledVersion + ' is installed. This setup will upgrade it to {#AppVersion}.'
  else if InstallMode = 'repair' then Result := 'FlowOps ' + InstalledVersion + ' is already installed. This setup can repair it.'
  else if FlowOpsDataDetected then Result := 'Existing FlowOps data was found. This setup will reinstall the application with your choice of database.'
  else Result := 'Fresh installation: no existing FlowOps application or database was detected.';
end;

procedure LayoutQueryPage(Page: TInputQueryWizardPage);
var
  I, Margin, LabelWidth, EditLeft, RowTop, RowHeight: Integer;
begin
  Margin := ScaleX(16);
  LabelWidth := ScaleX(150);
  EditLeft := Margin + LabelWidth + ScaleX(10);
  RowHeight := ScaleY(30);

  Page.SubCaptionLabel.Left := Margin;
  Page.SubCaptionLabel.Width := Page.SurfaceWidth - (Margin * 2);
  Page.SubCaptionLabel.AutoSize := False;
  Page.SubCaptionLabel.WordWrap := True;
  Page.SubCaptionLabel.Height := ScaleY(38);
  RowTop := Page.SubCaptionLabel.Top + Page.SubCaptionLabel.Height + ScaleY(10);

  for I := 0 to 3 do
  begin
    Page.PromptLabels[I].Left := Margin;
    Page.PromptLabels[I].Top := RowTop + (I * RowHeight);
    Page.PromptLabels[I].Width := LabelWidth;
    Page.PromptLabels[I].Height := RowHeight - ScaleY(4);
    Page.PromptLabels[I].AutoSize := False;
    Page.PromptLabels[I].WordWrap := True;

    Page.Edits[I].Left := EditLeft;
    Page.Edits[I].Top := RowTop + (I * RowHeight);
    Page.Edits[I].Width := Page.SurfaceWidth - EditLeft - Margin;
    Page.Edits[I].Height := ScaleY(23);
  end;
end;

procedure LayoutDatabasePage;
var
  Margin, RadioLeft, RadioTop, RadioHeight, StatusTop, StatusHeight: Integer;
begin
  Margin := ScaleX(16);
  RadioLeft := ScaleX(28);
  RadioHeight := ScaleY(28);

  DbIntroLabel.Left := Margin;
  DbIntroLabel.Top := ScaleY(4);
  DbIntroLabel.Width := DbChoicePage.SurfaceWidth - (Margin * 2);
  DbIntroLabel.AutoSize := False;
  DbIntroLabel.WordWrap := True;
  DbIntroLabel.Height := ScaleY(38);

  RadioTop := DbIntroLabel.Top + DbIntroLabel.Height + ScaleY(10);
  DbInstalledRadio.Left := RadioLeft;
  DbInstalledRadio.Top := RadioTop;
  DbInstalledRadio.Width := DbChoicePage.SurfaceWidth - RadioLeft - ScaleX(24);
  DbInstalledRadio.Height := RadioHeight;

  DbAutomaticRadio.Left := RadioLeft;
  DbAutomaticRadio.Top := RadioTop + RadioHeight + ScaleY(12);
  DbAutomaticRadio.Width := DbChoicePage.SurfaceWidth - RadioLeft - ScaleX(24);
  DbAutomaticRadio.Height := RadioHeight;

  DbStatusLabel.Left := Margin;
  StatusTop := DbAutomaticRadio.Top + DbAutomaticRadio.Height + ScaleY(12);
  DbStatusLabel.Top := StatusTop;
  DbStatusLabel.Width := DbChoicePage.SurfaceWidth - (Margin * 2);
  StatusHeight := DbChoicePage.SurfaceHeight - StatusTop - Margin;
  if StatusHeight < ScaleY(30) then StatusHeight := ScaleY(30);
  DbStatusLabel.Height := StatusHeight;
  DbStatusLabel.AutoSize := False;
  DbStatusLabel.WordWrap := True;
end;

procedure UpdateStartupSpinner;
begin
  case StartupSpinnerFrame mod 4 of
    0: StartupSpinner.Caption := #9680;
    1: StartupSpinner.Caption := #9683;
    2: StartupSpinner.Caption := #9681;
    3: StartupSpinner.Caption := #9682;
  end;
end;

procedure StopStartupTimer;
begin
  if StartupTimerID <> 0 then
  begin
    KillTimer(0, StartupTimerID);
    StartupTimerID := 0;
  end;
end;

procedure OpenFlowOps(Sender: TObject);
var
  ErrorCode: Integer;
begin
  ShellExec('', 'http://localhost:' + AppPort, '', '', SW_SHOWNORMAL, ewNoWait, ErrorCode);
end;

procedure OpenFlowOpsLogs(Sender: TObject);
var
  ErrorCode: Integer;
begin
  ShellExec('', DataRoot + '\logs', '', '', SW_SHOWNORMAL, ewNoWait, ErrorCode);
end;

procedure StartupTimerTick(HWnd, Msg, TimerID, SysTime: LongWord); forward;

procedure StartReadinessCheck;
var
  Code: Integer;
  Parameters: String;
begin
  StartupAttempt := StartupAttempt + 1;
  StartupStateFile := ExpandConstant('{tmp}\flowops-ready-') + IntToStr(StartupAttempt) + '.txt';
  DeleteFile(StartupStateFile);
  StartupReady := False;
  StartupTimedOut := False;
  StartupTimerTicks := 0;
  StartupSpinnerFrame := 0;
  StartupTitleLabel.Caption := 'Starting FlowOps';
  StartupStatusLabel.Caption := 'Starting Windows service...';
  StartupExplanationLabel.Caption := 'This may take up to 90 seconds on first launch.';
  StartupOpenButton.Visible := False;
  StartupRetryButton.Visible := False;
  StartupLogsButton.Visible := False;
  WizardForm.NextButton.Enabled := False;
  StartupSpinner.Visible := True;
  UpdateStartupSpinner;
  StopStartupTimer;
  StartupTimerID := SetTimer(0, 0, 100, CreateCallback(@StartupTimerTick));

  Parameters := '-NoProfile -ExecutionPolicy Bypass -File "' +
    ExpandConstant('{app}\tools\wait-for-ready.ps1') + '" -Url "http://localhost:' +
    AppPort + '/api/health" -StateFile "' + StartupStateFile +
    '" -TimeoutSeconds 90 -IntervalMilliseconds 1500';
  if not Exec(PsPath, Parameters, '', SW_HIDE, ewNoWait, Code) then
  begin
    StopStartupTimer;
    StartupSpinner.Visible := False;
    StartupTimedOut := True;
    StartupTitleLabel.Caption := 'FlowOps is taking longer than expected to start.';
    StartupStatusLabel.Caption := 'The readiness check could not be started.';
    StartupExplanationLabel.Caption := 'The service may still be starting or may require troubleshooting.';
    StartupRetryButton.Visible := True;
    StartupLogsButton.Visible := True;
    WizardForm.NextButton.Enabled := True;
  end;
end;

procedure RetryReadinessCheck(Sender: TObject);
begin
  StartReadinessCheck;
end;

procedure StartupTimerTick(HWnd, Msg, TimerID, SysTime: LongWord);
var
  StateData: AnsiString;
  StateText: String;
begin
  StartupTimerTicks := StartupTimerTicks + 1;
  StartupSpinnerFrame := (StartupSpinnerFrame + 1) mod 8;
  UpdateStartupSpinner;

  if StartupTimerTicks < 20 then
    StartupStatusLabel.Caption := 'Starting Windows service...'
  else if StartupTimerTicks < 55 then
    StartupStatusLabel.Caption := 'Connecting to database...'
  else if StartupTimerTicks < 100 then
    StartupStatusLabel.Caption := 'Preparing application...'
  else
    StartupStatusLabel.Caption := 'Waiting for FlowOps to become ready...';

  { Read the worker result once per second; this never performs network I/O on the UI thread. }
  if ((StartupTimerTicks mod 10) <> 0) or
    (not LoadStringFromFile(StartupStateFile, StateData)) then Exit;
  StateText := StateData;

  if Pos('STATE=READY', StateText) > 0 then
  begin
    StartupReady := True;
    StopStartupTimer;
    StartupSpinner.Visible := False;
    StartupTitleLabel.Caption := #10003 + ' FlowOps is ready';
    StartupStatusLabel.Caption := 'The application has started successfully.';
    StartupExplanationLabel.Caption := 'You can open FlowOps now, or finish setup and open it later from the shortcut.';
    StartupOpenButton.Visible := True;
    WizardForm.NextButton.Enabled := True;
  end
  else if Pos('STATE=TIMEOUT', StateText) > 0 then
  begin
    StartupTimedOut := True;
    StopStartupTimer;
    StartupSpinner.Visible := False;
    StartupTitleLabel.Caption := 'FlowOps is taking longer than expected to start.';
    StartupStatusLabel.Caption := 'The service may still be starting or may require troubleshooting.';
    StartupExplanationLabel.Caption := 'You can retry the readiness check or open the logs for more information.';
    StartupRetryButton.Visible := True;
    StartupLogsButton.Visible := True;
    WizardForm.NextButton.Enabled := True;
  end;
end;

procedure CreateStartupControls;
var
  PageWidth, CenterX, ButtonTop: Integer;
begin
  PageWidth := WizardForm.FinishedPage.ClientWidth;
  CenterX := PageWidth div 2;

  StartupTitleLabel := TNewStaticText.Create(WizardForm.FinishedPage);
  StartupTitleLabel.Parent := WizardForm.FinishedPage;
  StartupTitleLabel.Left := ScaleX(24);
  StartupTitleLabel.Top := ScaleY(24);
  StartupTitleLabel.Width := PageWidth - ScaleX(48);
  StartupTitleLabel.Height := ScaleY(36);
  StartupTitleLabel.AutoSize := False;
  StartupTitleLabel.Alignment := taCenter;
  StartupTitleLabel.Font.Size := 14;
  StartupTitleLabel.Font.Style := [fsBold];

  StartupSpinner := TNewStaticText.Create(WizardForm.FinishedPage);
  StartupSpinner.Parent := WizardForm.FinishedPage;
  StartupSpinner.Width := ScaleX(58);
  StartupSpinner.Height := ScaleY(58);
  StartupSpinner.Left := CenterX - (StartupSpinner.Width div 2);
  StartupSpinner.Top := ScaleY(76);
  StartupSpinner.AutoSize := False;
  StartupSpinner.Alignment := taCenter;
  StartupSpinner.Font.Name := 'Segoe UI Symbol';
  StartupSpinner.Font.Size := 28;
  StartupSpinner.Font.Color := clHighlight;

  StartupStatusLabel := TNewStaticText.Create(WizardForm.FinishedPage);
  StartupStatusLabel.Parent := WizardForm.FinishedPage;
  StartupStatusLabel.Left := ScaleX(24);
  StartupStatusLabel.Top := ScaleY(148);
  StartupStatusLabel.Width := PageWidth - ScaleX(48);
  StartupStatusLabel.Height := ScaleY(34);
  StartupStatusLabel.AutoSize := False;
  StartupStatusLabel.Alignment := taCenter;
  StartupStatusLabel.Font.Size := 10;

  StartupExplanationLabel := TNewStaticText.Create(WizardForm.FinishedPage);
  StartupExplanationLabel.Parent := WizardForm.FinishedPage;
  StartupExplanationLabel.Left := ScaleX(40);
  StartupExplanationLabel.Top := ScaleY(190);
  StartupExplanationLabel.Width := PageWidth - ScaleX(80);
  StartupExplanationLabel.Height := ScaleY(48);
  StartupExplanationLabel.AutoSize := False;
  StartupExplanationLabel.WordWrap := True;
  StartupExplanationLabel.Alignment := taCenter;

  ButtonTop := ScaleY(252);
  StartupOpenButton := TNewButton.Create(WizardForm.FinishedPage);
  StartupOpenButton.Parent := WizardForm.FinishedPage;
  StartupOpenButton.Caption := 'Open FlowOps';
  StartupOpenButton.Width := ScaleX(150);
  StartupOpenButton.Height := ScaleY(28);
  StartupOpenButton.Left := CenterX - (StartupOpenButton.Width div 2);
  StartupOpenButton.Top := ButtonTop;
  StartupOpenButton.OnClick := @OpenFlowOps;

  StartupRetryButton := TNewButton.Create(WizardForm.FinishedPage);
  StartupRetryButton.Parent := WizardForm.FinishedPage;
  StartupRetryButton.Caption := 'Retry';
  StartupRetryButton.Width := ScaleX(100);
  StartupRetryButton.Height := ScaleY(28);
  StartupRetryButton.Left := CenterX - ScaleX(108);
  StartupRetryButton.Top := ButtonTop;
  StartupRetryButton.OnClick := @RetryReadinessCheck;

  StartupLogsButton := TNewButton.Create(WizardForm.FinishedPage);
  StartupLogsButton.Parent := WizardForm.FinishedPage;
  StartupLogsButton.Caption := 'Open Logs';
  StartupLogsButton.Width := ScaleX(100);
  StartupLogsButton.Height := ScaleY(28);
  StartupLogsButton.Left := CenterX + ScaleX(8);
  StartupLogsButton.Top := ButtonTop;
  StartupLogsButton.OnClick := @OpenFlowOpsLogs;

  StartupOpenButton.Visible := False;
  StartupRetryButton.Visible := False;
  StartupLogsButton.Visible := False;

end;

function DetectionValue(const TextData, Key: String): String;
var
  Marker: String;
  StartAt, EndAt, Remaining: Integer;
begin
  Marker := Key + '=';
  StartAt := Pos(Marker, TextData);
  if StartAt = 0 then begin Result := ''; Exit; end;
  StartAt := StartAt + Length(Marker);
  Remaining := Length(TextData) - StartAt + 1;
  EndAt := Pos(#10, Copy(TextData, StartAt, Remaining));
  if EndAt > 0 then Result := Copy(TextData, StartAt, EndAt - 1) else Result := Copy(TextData, StartAt, Remaining);
  StringChangeEx(Result, #13, '', True);
end;

function ServiceExistsNamed(const ServiceName: String): Boolean;
var Code: Integer;
begin
  Result := Exec(ExpandConstant('{sys}\sc.exe'), 'query ' + ServiceName, '', SW_HIDE, ewWaitUntilTerminated, Code) and (Code = 0);
end;

function DetectFlowOpsService: Boolean;
begin
  { KovaxFlowOps is the legacy pre-rebrand service identifier. }
  Result := ServiceExistsNamed('FlowOps') or ServiceExistsNamed('KovaxFlowOps');
end;

procedure MigrateLegacyData;
var Code: Integer;
begin
  { Preserve configuration, JWT secrets, backups, metadata and restore state from test installs. }
  if DirExists(LegacyDataRoot) and not DirExists(DataRoot) then
  begin
    Log('Legacy ProgramData detected; copying it to the FlowOps data directory');
    ForceDirectories(DataRoot);
    if (not Exec(ExpandConstant('{sys}\robocopy.exe'), '"' + LegacyDataRoot + '" "' + DataRoot + '" /E /COPY:DAT /DCOPY:DAT /R:2 /W:1', '', SW_HIDE, ewWaitUntilTerminated, Code)) or (Code >= 8) then
    begin
      MsgBox('Existing FlowOps data could not be migrated to ' + DataRoot + '. Setup stopped without changing the database.', mbError, MB_OK);
      Abort;
    end;
    Log('Legacy ProgramData copied successfully; the source is retained as a safety copy');
  end;
end;

function DetectInstalledFlowOps: Boolean;
var VersionText: String; Key: String;
begin
  Result := False; InstalledVersion := '';
  Key := 'SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\{#AppId}_is1';
  Log('Checking FlowOps uninstall key: ' + Key);
  if IsWin64 then
  begin
    if RegQueryStringValue(HKLM64, Key, 'DisplayVersion', VersionText) then
    begin
      Result := True;
      InstalledVersion := VersionText;
      Log('Found 64-bit FlowOps uninstall entry');
    end
    else if RegKeyExists(HKLM64, Key) then
    begin
      Result := True;
      Log('Found 64-bit FlowOps uninstall entry without DisplayVersion');
    end;
  end;
  if RegQueryStringValue(HKLM32, Key, 'DisplayVersion', VersionText) then
  begin
    Result := True;
    if InstalledVersion = '' then InstalledVersion := VersionText;
    Log('Found 32-bit FlowOps uninstall entry');
  end
  else if RegKeyExists(HKLM32, Key) then
  begin
    Result := True;
    Log('Found 32-bit FlowOps uninstall entry without DisplayVersion');
  end;
  if FileExists(ExpandConstant('{autopf}\FlowOps\FlowOps.exe')) then Result := True;
  { Legacy pre-rebrand application wrapper detection. }
  if FileExists(ExpandConstant('{autopf}\Kovax FlowOps\KovaxFlowOps.exe')) then Result := True;
  ServiceDetected := DetectFlowOpsService;
  if ServiceDetected then Result := True;
  ProgramDataDetected := DirExists(DataRoot);
  AppInstalled := Result;
  Log('Existing app detected: ' + BooleanText(Result));
  Log('Installed version: ' + InstalledVersion);
  Log('Service detected: ' + BooleanText(ServiceDetected));
  Log('ProgramData detected: ' + BooleanText(ProgramDataDetected));
end;

procedure DetectFlowOpsDatabase;
var OutFile, Script, OutputText: String; Output: AnsiString; Code: Integer;
begin
  ExtractTemporaryFile('inspect-flowops.ps1');
  Script := ExpandConstant('{tmp}\inspect-flowops.ps1'); OutFile := ExpandConstant('{tmp}\flowops-database.txt'); DeleteFile(OutFile);
  Exec(PsPath, '-NoProfile -ExecutionPolicy Bypass -File "' + Script + '" -DataRoot "' + DataRoot + '" -OutputFile "' + OutFile + '" -PsqlPath "' + PgBinDir + '\psql.exe"', '', SW_HIDE, ewWaitUntilTerminated, Code);
  FlowOpsDatabaseDetected := False; ExistingAdminDetected := False;
  if LoadStringFromFile(OutFile, Output) then begin
    OutputText := Output;
    FlowOpsDatabaseDetected := DetectionValue(OutputText, 'DB_DETECTED') = '1';
    FlowOpsDataDetected := DetectionValue(OutputText, 'DATA_DETECTED') = '1';
    ExistingAdminDetected := DetectionValue(OutputText, 'ADMIN_DETECTED') = '1';
    Log('Existing ADMIN count: ' + DetectionValue(OutputText, 'ADMIN_COUNT'));
  end;
  Log('FlowOps DB detected: ' + BooleanText(FlowOpsDatabaseDetected));
  Log('FlowOps data/config detected: ' + BooleanText(FlowOpsDataDetected));
  Log('Existing ADMIN detected: ' + BooleanText(ExistingAdminDetected));
end;

procedure LayoutFlowDbPage;
var Margin, Top: Integer;
begin
  Margin := ScaleX(16); Top := ScaleY(8);
  FlowDbIntroLabel.Left := Margin; FlowDbIntroLabel.Top := Top; FlowDbIntroLabel.Width := FlowDbPage.SurfaceWidth - Margin * 2; FlowDbIntroLabel.Height := ScaleY(60); FlowDbIntroLabel.AutoSize := False; FlowDbIntroLabel.WordWrap := True;
  Top := Top + ScaleY(70);
  FlowDbExistingRadio.Left := ScaleX(28); FlowDbExistingRadio.Top := Top; FlowDbExistingRadio.Width := FlowDbPage.SurfaceWidth - ScaleX(48); FlowDbExistingRadio.Height := ScaleY(36);
  FlowDbNewRadio.Left := ScaleX(28); FlowDbNewRadio.Top := Top + ScaleY(52); FlowDbNewRadio.Width := FlowDbPage.SurfaceWidth - ScaleX(48); FlowDbNewRadio.Height := ScaleY(36);
  FlowDbConfirmLabel.Left := Margin; FlowDbConfirmLabel.Top := Top + ScaleY(105); FlowDbConfirmLabel.Width := FlowDbPage.SurfaceWidth - Margin * 2; FlowDbConfirmLabel.Height := ScaleY(65); FlowDbConfirmLabel.AutoSize := False; FlowDbConfirmLabel.WordWrap := True;
end;

function DetectPostgres: Boolean;
  var OutFile, DetectionScript, OutputText, ExecParameters: String;
      Output: AnsiString; Code: Integer; ExecStarted: Boolean;
begin
  Log('PostgreSQL detection started');
  Log('Installer architecture: IsWin64=' + BooleanText(IsWin64) +
    '; Is64BitInstallMode=' + BooleanText(Is64BitInstallMode));
  Log('Installer elevation: IsAdmin=' + BooleanText(IsAdmin) +
    '; IsAdminInstallMode=' + BooleanText(IsAdminInstallMode));
  ExtractTemporaryFile('detect-postgresql.ps1');
  DetectionScript := ExpandConstant('{tmp}\detect-postgresql.ps1');
  OutFile := ExpandConstant('{tmp}\flowops-postgres.txt');
  DeleteFile(OutFile);
  ExecParameters := '-NoProfile -ExecutionPolicy Bypass -File "' + DetectionScript +
    '" -OutputFile "' + OutFile + '"';
  ExecStarted := Exec(PsPath, ExecParameters, '', SW_HIDE, ewWaitUntilTerminated, Code);
  Log('Detection helper started: ' + BooleanText(ExecStarted));
  Log('Detection helper exit code: ' + IntToStr(Code));
  Log('Detection output file exists: ' + BooleanText(FileExists(OutFile)));
  PgBinDir := ''; PgServiceName := ''; PgServiceStatus := ''; PgMajorVersion := '';
  PgDetectionMessage := 'PostgreSQL was not detected. The bundled PostgreSQL package will be installed.';
  PgDetectionSummary := PgDetectionMessage;
  PgDiagnosticSummary := 'Detection: Registry=No | Service=No | Filesystem=No | PATH=No';
  if (not ExecStarted) or (Code <> 0) or not LoadStringFromFile(OutFile, Output) then
  begin
    Log('PostgreSQL detection output could not be loaded');
    Log('PostgreSQLDetected=False');
    Result := False;
    Exit;
  end;
  OutputText := Output;
  PgBinDir := DetectionValue(OutputText, 'BIN_DIR');
  PgServiceName := DetectionValue(OutputText, 'SERVICE_NAME');
  PgServiceStatus := DetectionValue(OutputText, 'SERVICE_STATUS');
  PgMajorVersion := DetectionValue(OutputText, 'MAJOR_VERSION');
  PgDetectionMessage := DetectionValue(OutputText, 'MESSAGE');
  PgDetectionSummary := PgDetectionMessage;
  StringChangeEx(PgDetectionSummary, '|', #13#10, True);
  PgDiagnosticSummary := 'Detection: Registry=' +
    BooleanText(DetectionValue(OutputText, 'REGISTRY_DETECTED') = '1') +
    ' | Service=' + BooleanText(DetectionValue(OutputText, 'SERVICE_DETECTED') = '1') +
    ' | Filesystem=' + BooleanText(DetectionValue(OutputText, 'FILESYSTEM_DETECTED') = '1') +
    ' | PATH=' + BooleanText(DetectionValue(OutputText, 'PATH_DETECTED') = '1');
  StringChangeEx(PgDiagnosticSummary, 'True', 'Yes', True);
  StringChangeEx(PgDiagnosticSummary, 'False', 'No', True);
  Log('PATH lookup result: ' + DetectionValue(OutputText, 'PATH_PSQL'));
  Log('Registry lookup result: detected=' + DetectionValue(OutputText, 'REGISTRY_DETECTED') +
    '; view=' + DetectionValue(OutputText, 'REGISTRY_VIEW') +
    '; key=' + DetectionValue(OutputText, 'REGISTRY_KEY') +
    '; base dir=' + DetectionValue(OutputText, 'REGISTRY_BASE_DIR'));
  Log('Service lookup result: detected=' + DetectionValue(OutputText, 'SERVICE_DETECTED') +
    '; name=' + PgServiceName + '; status=' + PgServiceStatus);
  Log('Filesystem lookup result: detected=' + DetectionValue(OutputText, 'FILESYSTEM_DETECTED'));
  Log('Detected PostgreSQL version: ' + DetectionValue(OutputText, 'VERSION') +
    '; major=' + PgMajorVersion);
  Log('Detected PostgreSQL psql: ' + DetectionValue(OutputText, 'PSQL_PATH'));
  Log('Detected PostgreSQL bin directory: ' + PgBinDir);
  Result := DetectionValue(OutputText, 'DETECTED') = '1';
  Log('PostgreSQLDetected=' + BooleanText(Result));
end;

procedure InitializeWizard;
begin
  AppPort := '8080';
  DataRoot := ExpandConstant('{commonappdata}\FlowOps');
  LegacyDataRoot := ExpandConstant('{commonappdata}\Kovax FlowOps');
  MigrateLegacyData;
  DetectInstalledFlowOps;
  if not AppInstalled then InstallMode := 'fresh'
  else if InstalledVersion = '' then InstallMode := 'repair'
  else if CompareVersionText(InstalledVersion, '{#AppVersion}') < 0 then InstallMode := 'upgrade'
  else InstallMode := 'repair';
  DbChoicePage := CreateCustomPage(wpSelectDir, 'Database setup', 'Choose how to provide PostgreSQL');
  DbIntroLabel := TNewStaticText.Create(DbChoicePage.Surface); DbIntroLabel.Parent := DbChoicePage.Surface; DbIntroLabel.Caption := 'PostgreSQL is used to store FlowOps data.' + #13#10 + InstallModeMessage;
  DbInstalledRadio := TNewRadioButton.Create(DbChoicePage.Surface); DbInstalledRadio.Parent := DbChoicePage.Surface; DbInstalledRadio.Caption := 'Use the PostgreSQL installation already on this computer';
  DbAutomaticRadio := TNewRadioButton.Create(DbChoicePage.Surface); DbAutomaticRadio.Parent := DbChoicePage.Surface; DbAutomaticRadio.Caption := 'Install PostgreSQL automatically';
  PostgreSQLDetected := DetectPostgres;
  DetectFlowOpsDatabase;
  if (not AppInstalled) and FlowOpsDataDetected then InstallMode := 'reinstall';
  DbStatusLabel := TNewStaticText.Create(DbChoicePage.Surface); DbStatusLabel.Parent := DbChoicePage.Surface;
  { Temporary development-build diagnostics: remove this appended line when no longer needed. }
  DbStatusLabel.Caption := PgDetectionSummary + #13#10#13#10 + PgDiagnosticSummary;
  if PostgreSQLDetected then DbInstalledRadio.Checked := True else DbAutomaticRadio.Checked := True;
  LayoutDatabasePage;
  FlowDbPage := CreateCustomPage(DbChoicePage.ID, 'Database setup', 'Choose the FlowOps database');
  FlowDbIntroLabel := TNewStaticText.Create(FlowDbPage.Surface); FlowDbIntroLabel.Parent := FlowDbPage.Surface; FlowDbIntroLabel.Caption := 'Existing FlowOps data was found.';
  FlowDbExistingRadio := TNewRadioButton.Create(FlowDbPage.Surface); FlowDbExistingRadio.Parent := FlowDbPage.Surface; FlowDbExistingRadio.Caption := 'Use existing database - keep users, tasks, projects, reports and settings.';
  FlowDbNewRadio := TNewRadioButton.Create(FlowDbPage.Surface); FlowDbNewRadio.Parent := FlowDbPage.Surface; FlowDbNewRadio.Caption := 'Start with a new database - create a clean FlowOps database.';
  FlowDbConfirmLabel := TNewStaticText.Create(FlowDbPage.Surface); FlowDbConfirmLabel.Parent := FlowDbPage.Surface; FlowDbConfirmLabel.Caption := 'Starting new requires explicit confirmation. A backup will be created and the old database will be archived before a fresh database is created.';
  FlowDbExistingRadio.Checked := True; LayoutFlowDbPage;
  UseExistingDatabase := FlowOpsDatabaseDetected or FlowOpsDataDetected;
  DbAdminPage := CreateInputQueryPage(FlowDbPage.ID, 'PostgreSQL administrator', 'Connect to PostgreSQL', 'These credentials are used only to create or archive a database and are discarded.');
  DbAdminPage.Add('Host:', False); DbAdminPage.Add('Port:', False); DbAdminPage.Add('Administrator username:', False); DbAdminPage.Add('Administrator password:', True);
  DbAdminPage.Values[0] := 'localhost'; DbAdminPage.Values[1] := '5432'; DbAdminPage.Values[2] := 'postgres';
  LayoutQueryPage(DbAdminPage);
  AdminPage := CreateInputQueryPage(DbAdminPage.ID, 'Create FlowOps Administrator', 'Set up the first administrator', 'This account will be the first ADMIN account.');
  AdminPage.Add('Full name:', False); AdminPage.Add('Email:', False); AdminPage.Add('Password:', True); AdminPage.Add('Confirm password:', True);
  LayoutQueryPage(AdminPage);
  CreateStartupControls;
end;

function NextButtonClick(CurPageID: Integer): Boolean;
begin
  Result := True;
  if CurPageID = DbChoicePage.ID then begin
    InstallPostgres := DbAutomaticRadio.Checked; Log('PostgreSQL detected: ' + BooleanText(PostgreSQLDetected));
    if PostgreSQLDetected and InstallPostgres then begin
      MsgBox('PostgreSQL ' + PgMajorVersion + ' is already installed on this computer. Select "Use the PostgreSQL installation already on this computer" to avoid a port and service conflict.', mbError, MB_OK);
      Result := False; Exit;
    end;
    if AppInstalled and (InstallMode = 'repair') then
      if MsgBox('FlowOps ' + InstalledVersion + ' is already installed.' + #13#10#13#10 + 'Continue with a repair installation?', mbConfirmation, MB_YESNO) <> IDYES then begin Result := False; Exit; end;
  end;
  if CurPageID = FlowDbPage.ID then begin
    UseExistingDatabase := FlowDbExistingRadio.Checked;
    Log('Selected database mode: ' + DatabaseModeText(UseExistingDatabase));
    if not UseExistingDatabase then begin
      if MsgBox('Start with a new FlowOps database?' + #13#10#13#10 + 'Existing data will no longer be used by this installation. A backup will be created before continuing.', mbConfirmation, MB_YESNO) <> IDYES then begin Result := False; Exit; end;
      NewDatabaseConfirmed := True;
    end;
  end;
  if CurPageID = AdminPage.ID then begin
    if Trim(AdminPage.Values[0]) = '' then begin MsgBox('Enter the administrator name.', mbError, MB_OK); Result := False; end;
    if Pos('@', AdminPage.Values[1]) < 2 then begin MsgBox('Enter a valid administrator email.', mbError, MB_OK); Result := False; end;
    if Length(AdminPage.Values[2]) < 8 then begin MsgBox('Use a password of at least 8 characters.', mbError, MB_OK); Result := False; end;
    if AdminPage.Values[2] <> AdminPage.Values[3] then begin MsgBox('The passwords do not match.', mbError, MB_OK); Result := False; end;
  end;
end;

function ShouldSkipPage(PageID: Integer): Boolean;
begin
  Result := ((PageID = DbAdminPage.ID) and (InstallPostgres or ((FlowOpsDatabaseDetected or FlowOpsDataDetected) and FlowDbExistingRadio.Checked))) or
    ((PageID = FlowDbPage.ID) and (not (FlowOpsDatabaseDetected or FlowOpsDataDetected))) or
    ((PageID = AdminPage.ID) and (FlowOpsDatabaseDetected or FlowOpsDataDetected) and FlowDbExistingRadio.Checked);
end;

function ConfigureDatabase: Boolean;
var Json, ErrorPath, ErrorText: String; ErrorOutput: AnsiString; Code: Integer; InputPath, LaunchLog: String; ExecStarted: Boolean;
begin
  Result := False;
  InputPath := ExpandConstant('{tmp}\flowops-setup.json');
  LaunchLog := ExpandConstant('{tmp}\flowops-setup-launch.log');
  ErrorPath := DataRoot + '\runtime\installer-database-error.txt';
  DeleteFile(LaunchLog);
  DeleteFile(ErrorPath);
  ForceDirectories(DataRoot);
  if FlowOpsDatabaseDetected and UseExistingDatabase then Log('Existing FlowOps database selected; preserved without destructive schema operations') else Log('New FlowOps database selected; backup/archive will be performed when an old database exists');
  Json := '{"host":"' + JsonEscape(DbAdminPage.Values[0]) + '","port":"' + JsonEscape(DbAdminPage.Values[1]) + '","adminUser":"' + JsonEscape(DbAdminPage.Values[2]) + '","postgresAdminPassword":"' + JsonEscape(DbAdminPage.Values[3]) + '","postgresBin":"' + JsonEscape(PgBinDir) + '","postgresService":"' + JsonEscape(PgServiceName) + '","postgresStatus":"' + JsonEscape(PgServiceStatus) + '","postgresVersion":"' + JsonEscape(PgMajorVersion) + '","postgresDetection":"' + JsonEscape(PgDetectionMessage) + '","appUser":"flowops_user","database":"flowops","appPort":"' + JsonEscape(AppPort) + '","databaseMode":"' + DatabaseModeText(UseExistingDatabase) + '","flowopsDatabaseDetected":' + JsonBoolean(FlowOpsDatabaseDetected) + ',"adminName":"' + JsonEscape(AdminPage.Values[0]) + '","adminEmail":"' + JsonEscape(AdminPage.Values[1]) + '","adminPassword":"' + JsonEscape(AdminPage.Values[2]) + '"}';
  SaveStringToFile(InputPath, Json, False);
  ExecStarted := Exec(PsPath, '-NoProfile -ExecutionPolicy Bypass -File "' + ExpandConstant('{app}\tools\setup-database.ps1') + '" -InputFile "' + InputPath + '" -DataRoot "' + DataRoot + '"', '', SW_HIDE, ewWaitUntilTerminated, Code);
  SaveStringToFile(LaunchLog, 'PowerShell started=' + BooleanText(ExecStarted) + #13#10 + 'Exit code=' + IntToStr(Code) + #13#10 + 'Input=' + InputPath + #13#10 + 'DataRoot=' + DataRoot, False);
  DeleteFile(InputPath);
  if (not ExecStarted) or (Code <> 0) then begin
    ErrorText := '';
    if LoadStringFromFile(ErrorPath, ErrorOutput) then ErrorText := Trim(ErrorOutput);
    if ErrorText = '' then ErrorText := 'The database helper did not return a detailed error.';
    MsgBox(ErrorText + #13#10#13#10 + 'Details: ' + DataRoot + '\logs\installer-database.log' + #13#10 + 'Launcher log: ' + LaunchLog, mbError, MB_OK);
    Exit;
  end;
  Result := True;
end;

procedure RemoveServiceByName(const ServiceName, WrapperPath: String);
var Code, Attempt: Integer; WorkingDirectory: String;
begin
  if not ServiceExistsNamed(ServiceName) then Exit;
  Log('Existing service found; removing: ' + ServiceName);
  WorkingDirectory := ExtractFileDir(WrapperPath);
  if FileExists(WrapperPath) then
  begin
    Exec(WrapperPath, 'stop', WorkingDirectory, SW_HIDE, ewWaitUntilTerminated, Code);
    Exec(WrapperPath, 'uninstall', WorkingDirectory, SW_HIDE, ewWaitUntilTerminated, Code);
  end;
  if ServiceExistsNamed(ServiceName) then
  begin
    Exec(ExpandConstant('{sys}\sc.exe'), 'stop ' + ServiceName, '', SW_HIDE, ewWaitUntilTerminated, Code);
    Exec(ExpandConstant('{sys}\sc.exe'), 'delete ' + ServiceName, '', SW_HIDE, ewWaitUntilTerminated, Code);
  end;
  for Attempt := 1 to 20 do
  begin
    if not ServiceExistsNamed(ServiceName) then Exit;
    Sleep(250);
  end;
  MsgBox('The existing FlowOps service could not be removed. Close Windows Services and retry the installer.', mbError, MB_OK);
  Abort;
end;

procedure RemoveExistingServices;
var LegacyAppRoot: String;
begin
  RemoveServiceByName('FlowOps', ExpandConstant('{autopf}\FlowOps\FlowOps.exe'));
  { KovaxFlowOps and its wrapper path are retained only for pre-rebrand test migration. }
  LegacyAppRoot := ExpandConstant('{autopf}\Kovax FlowOps');
  RemoveServiceByName('KovaxFlowOps', LegacyAppRoot + '\KovaxFlowOps.exe');
  if DirExists(LegacyAppRoot) then
  begin
    Log('Removing legacy application binaries after persistent data migration');
    if not DelTree(LegacyAppRoot, True, True, True) then
    begin
      MsgBox('Legacy FlowOps application files could not be removed. Close any programs using ' + LegacyAppRoot + ' and retry.', mbError, MB_OK);
      Abort;
    end;
  end;
end;

procedure ConfigureServiceDependency;
var Code: Integer;
begin
  if PgServiceName = '' then
  begin
    Log('PostgreSQL service dependency not configured: no service name was detected');
    Exit;
  end;

  Log('Configuring FlowOps dependency: ' + PgServiceName);
  if (not Exec(ExpandConstant('{sys}\sc.exe'),
    'config FlowOps depend= ' + PgServiceName, '', SW_HIDE,
    ewWaitUntilTerminated, Code)) or (Code <> 0) then
  begin
    Log('Service dependency configuration failed; exit code: ' + IntToStr(Code));
    MsgBox('FlowOps could not be configured to depend on ' + PgServiceName + '.', mbError, MB_OK);
    Abort;
  end;
  Log('Service dependency configured successfully: ' + PgServiceName);
end;

procedure InstallPostgresIfNeeded;
  var Code: Integer; PasswordFile: String; Password: AnsiString; PasswordText: String;
begin
  if not InstallPostgres then Exit;
  if not FileExists(ExpandConstant('{tmp}\postgresql-installer.exe')) then begin MsgBox('The PostgreSQL prerequisite is not included in this build.', mbError, MB_OK); Abort; end;
  PasswordFile := ExpandConstant('{tmp}\flowops-postgres-password.txt');
  Exec(PsPath, '-NoProfile -Command "[guid]::NewGuid().ToString(''N'') + [guid]::NewGuid().ToString(''N'')" > "' + PasswordFile + '"', '', SW_HIDE, ewWaitUntilTerminated, Code);
  if not LoadStringFromFile(PasswordFile, Password) then Abort;
  PasswordText := Password;
  StringChangeEx(PasswordText, #13, '', True); StringChangeEx(PasswordText, #10, '', True);
  DbAdminPage.Values[0] := 'localhost'; DbAdminPage.Values[1] := '5432'; DbAdminPage.Values[2] := 'postgres'; DbAdminPage.Values[3] := PasswordText;
  Exec(ExpandConstant('{tmp}\postgresql-installer.exe'), '--mode unattended --superpassword="' + PasswordText + '" --serverport=5432', '', SW_HIDE, ewWaitUntilTerminated, Code);
  DeleteFile(PasswordFile);
  if Code <> 0 then begin MsgBox('PostgreSQL installation failed. The installer will stop without starting FlowOps.', mbError, MB_OK); Abort; end;
  PostgreSQLDetected := DetectPostgres;
  if not PostgreSQLDetected then begin MsgBox('PostgreSQL was installed but its Windows service could not be detected.', mbError, MB_OK); Abort; end;
end;

procedure InstallServiceAndFirewall;
var Code: Integer;
begin
  if (not Exec(ExpandConstant('{app}\FlowOps.exe'), 'install', ExpandConstant('{app}'), SW_HIDE, ewWaitUntilTerminated, Code)) or (Code <> 0) then
  begin
    MsgBox('The FlowOps Windows service could not be installed.', mbError, MB_OK);
    Abort;
  end;
  ConfigureServiceDependency;
  if (not Exec(ExpandConstant('{sys}\sc.exe'), 'config FlowOps start= auto', '', SW_HIDE, ewWaitUntilTerminated, Code)) or (Code <> 0) then
  begin
    MsgBox('The FlowOps Windows service startup mode could not be configured.', mbError, MB_OK);
    Abort;
  end;
  Exec(ExpandConstant('{sys}\netsh.exe'), 'advfirewall firewall delete rule name="Kovax FlowOps"', '', SW_HIDE, ewWaitUntilTerminated, Code);
  Exec(ExpandConstant('{sys}\netsh.exe'), 'advfirewall firewall delete rule name="FlowOps"', '', SW_HIDE, ewWaitUntilTerminated, Code);
  if (not Exec(ExpandConstant('{sys}\netsh.exe'), 'advfirewall firewall add rule name="FlowOps" dir=in action=allow protocol=TCP localport=' + AppPort + ' profile=private,domain', '', SW_HIDE, ewWaitUntilTerminated, Code)) or (Code <> 0) then
  begin
    MsgBox('The FlowOps firewall rule could not be created. Allow inbound TCP port ' + AppPort + ' on Private and Domain networks before connecting client devices.', mbError, MB_OK);
    Abort;
  end;
  if (not Exec(ExpandConstant('{app}\FlowOps.exe'), 'start', ExpandConstant('{app}'), SW_HIDE, ewWaitUntilTerminated, Code)) or (Code <> 0) then
  begin
    MsgBox('The FlowOps service could not be started. See ' + DataRoot + '\logs\FlowOps.wrapper.log.', mbError, MB_OK);
    Abort;
  end;
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssInstall then RemoveExistingServices;
  if CurStep = ssPostInstall then begin
    InstallPostgresIfNeeded;
    if not ConfigureDatabase then Abort;
    InstallServiceAndFirewall;
  end;
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  if CurPageID = wpFinished then
  begin
    WizardForm.WizardBitmapImage2.Visible := False;
    WizardForm.FinishedHeadingLabel.Visible := False;
    WizardForm.FinishedLabel.Visible := False;
    WizardForm.RunList.Visible := False;
    StartReadinessCheck;
  end;
end;
