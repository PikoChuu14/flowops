using System.Diagnostics;
using System.Drawing;
using FlowOps.Agent.Core;

namespace FlowOps.NotificationAgent;

internal enum ConnectionState { Connected, Disconnected, AuthenticationRequired }

internal sealed class TrayContext : ApplicationContext
{
    private readonly AgentPaths _paths = new();
    private readonly NotifyIcon _tray;
    private readonly ToolStripMenuItem _status;
    private readonly CancellationTokenSource _stop = new();
    private readonly DpapiCredentialStore _credentials;
    private readonly AgentStateStore _state;
    private readonly AgentLog _log;
    private readonly WindowsToastNotifier _toasts;
    private readonly AgentApiClient _api = new(new HttpClient { Timeout = TimeSpan.FromSeconds(15) });
    private readonly PollBackoff _backoff = new();
    private AgentNotification? _lastToast;
    private readonly string _launcher = Path.Combine(AppContext.BaseDirectory, "FlowOps-Client.ps1");

    public TrayContext()
    {
        _credentials = new(_paths); _state = new(_paths); _log = new(_paths);
        _toasts = new WindowsToastNotifier(_log);
        _toasts.Activated += destination => OpenFlowOps(destination);
        _toasts.Register();
        _status = new ToolStripMenuItem("Notification Status: Starting") { Enabled = false };
        var menu = new ContextMenuStrip();
        menu.Items.Add("Open FlowOps", null, (_, _) => OpenFlowOps(null));
        menu.Items.Add(_status);
        menu.Items.Add("Reconnect / Refresh", null, (_, _) => _ = PollOnceAsync(_stop.Token));
        menu.Items.Add("Settings", null, (_, _) => ShowSettings());
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add("Exit", null, (_, _) => ExitAgent());
        _tray = new NotifyIcon { Text = "FlowOps Notification Agent", Icon = LoadIcon(), ContextMenuStrip = menu, Visible = true };
        _tray.DoubleClick += (_, _) => OpenFlowOps(null);
        _tray.BalloonTipClicked += (_, _) => { var toast = _lastToast; _lastToast = null; OpenFlowOps(toast?.Destination); };
        _ = RunAsync(_stop.Token);
    }

    private static Icon LoadIcon()
    {
        var path = Path.Combine(AppContext.BaseDirectory, "FlowOps.ico");
        return File.Exists(path) ? new Icon(path) : SystemIcons.Application;
    }

    private async Task RunAsync(CancellationToken token)
    {
        while (!token.IsCancellationRequested)
        {
            var delay = await PollOnceAsync(token);
            try { await Task.Delay(delay, token); } catch (OperationCanceledException) { break; }
        }
    }

    private async Task<TimeSpan> PollOnceAsync(CancellationToken token)
    {
        var credential = SafeCredential();
        if (credential is null) { SetState(ConnectionState.AuthenticationRequired); return TimeSpan.FromSeconds(30); }
        try
        {
            var server = new ServerConfiguration(_paths).Load();
            var current = _state.Load();
            var notifications = await _api.PollAsync(server, credential, current.LastNotificationId, token);
            // NotifyIcon has one activation event for the currently displayed item.
            // Deliver one record per poll so click-through can never target the wrong notification.
            var notification = notifications.OrderBy(item => item.Id).FirstOrDefault();
            if (notification is not null && notification.Id > current.LastNotificationId)
            {
                ShowNotification(notification);
                current = new AgentState(notification.Id);
                _state.Save(current);
            }
            SetState(ConnectionState.Connected);
            return _backoff.Success();
        }
        catch (DeviceAuthenticationException)
        {
            _credentials.Clear(); SetState(ConnectionState.AuthenticationRequired); _log.Info("Device credential was rejected and removed.");
            return TimeSpan.FromSeconds(30);
        }
        catch (OperationCanceledException) when (token.IsCancellationRequested) { return TimeSpan.Zero; }
        catch (Exception ex)
        {
            SetState(ConnectionState.Disconnected); _log.Error($"Polling failed: {ex.GetType().Name}: {ex.Message}");
            return _backoff.Failure();
        }
    }

    private string? SafeCredential() { try { return _credentials.Load(); } catch (Exception ex) { _log.Error($"Credential read failed: {ex.GetType().Name}"); return null; } }
    private void ShowNotification(AgentNotification item)
    {
        if (_toasts.Show(item)) return;
        _lastToast = item;
        _tray.BalloonTipTitle = string.IsNullOrWhiteSpace(item.Title) ? "FlowOps" : item.Title;
        _tray.BalloonTipText = item.Message;
        _tray.BalloonTipIcon = ToolTipIcon.Info;
        _tray.ShowBalloonTip(10_000);
    }

    private void SetState(ConnectionState state)
    {
        if (_status.GetCurrentParent()?.InvokeRequired == true) { _status.GetCurrentParent()!.BeginInvoke(() => SetState(state)); return; }
        _status.Text = "Notification Status: " + state switch { ConnectionState.Connected => "Connected", ConnectionState.Disconnected => "Disconnected", _ => "Authentication required" };
        _tray.Text = _status.Text.Replace("Notification Status: ", "FlowOps: ");
    }

    private void ShowSettings()
    {
        using var form = new RegistrationForm(_paths, _api, _credentials, _state, _log);
        if (form.ShowDialog() == DialogResult.OK) _ = PollOnceAsync(_stop.Token);
    }

    private void OpenFlowOps(string? destination)
    {
        try
        {
            var server = new ServerConfiguration(_paths).Load();
            var url = ServerConfiguration.Compose(server, destination).ToString();
            if (File.Exists(_launcher))
                Process.Start(new ProcessStartInfo("powershell.exe", $"-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File \"{_launcher}\" -Url \"{url}\"") { UseShellExecute = false, CreateNoWindow = true });
            else Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
        }
        catch (Exception ex) { MessageBox.Show(ex.Message, "FlowOps Notification Agent", MessageBoxButtons.OK, MessageBoxIcon.Warning); }
    }

    private void ExitAgent() { _stop.Cancel(); _tray.Visible = false; _tray.Dispose(); ExitThread(); }
    protected override void Dispose(bool disposing) { if (disposing) { _stop.Cancel(); _toasts.Dispose(); _stop.Dispose(); _tray.Dispose(); } base.Dispose(disposing); }
}
