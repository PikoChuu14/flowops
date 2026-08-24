using FlowOps.Agent.Core;
using Microsoft.Windows.AppNotifications;
using Microsoft.Windows.AppNotifications.Builder;

namespace FlowOps.NotificationAgent;

internal sealed class WindowsToastNotifier : IDisposable
{
    private readonly AgentLog _log;
    private bool _registered;

    public WindowsToastNotifier(AgentLog log) => _log = log;

    public event Action<string?>? Activated;

    public bool Register()
    {
        try
        {
            if (!AppNotificationManager.IsSupported())
            {
                _log.Info("Windows App SDK notifications are not supported; tray balloon fallback is active.");
                return false;
            }
            AppNotificationManager.Default.NotificationInvoked += OnInvoked;
            AppNotificationManager.Default.Register();
            _registered = true;
            _log.Info("Persistent Windows app notifications registered.");
            return true;
        }
        catch (Exception ex)
        {
            _log.Error($"Windows notification registration failed: {ex.GetType().Name}: {ex.Message}");
            return false;
        }
    }

    public bool Show(AgentNotification item)
    {
        if (!_registered) return false;
        try
        {
            var builder = new AppNotificationBuilder()
                .AddArgument("destination", item.Destination ?? "")
                .AddText("FlowOps")
                .AddText(string.IsNullOrWhiteSpace(item.Title) ? "New notification" : item.Title)
                .AddText(item.Message);
            var notification = builder.BuildNotification();
            notification.ExpiresOnReboot = false;
            AppNotificationManager.Default.Show(notification);
            return true;
        }
        catch (Exception ex)
        {
            _log.Error($"Windows notification display failed: {ex.GetType().Name}: {ex.Message}");
        }
        return false;
    }

    private void OnInvoked(AppNotificationManager sender, AppNotificationActivatedEventArgs args)
        => Activated?.Invoke(ToastActivationArguments.Destination(args.Argument));

    public void Dispose()
    {
        if (!_registered) return;
        try
        {
            AppNotificationManager.Default.NotificationInvoked -= OnInvoked;
            AppNotificationManager.Default.Unregister();
        }
        catch (Exception ex) { _log.Error($"Windows notification cleanup failed: {ex.GetType().Name}"); }
        _registered = false;
    }
}
