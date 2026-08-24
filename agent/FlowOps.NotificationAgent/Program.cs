namespace FlowOps.NotificationAgent;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        using var mutex = new Mutex(true, "Local\\FlowOps.NotificationAgent", out var created);
        if (!created) return;
        ApplicationConfiguration.Initialize();
        Application.Run(new TrayContext());
    }
}
