namespace FlowOps.Agent.Core;

public static class ToastActivationArguments
{
    public static string? Destination(string? arguments)
    {
        if (string.IsNullOrWhiteSpace(arguments)) return null;
        foreach (var pair in arguments.Split('&', StringSplitOptions.RemoveEmptyEntries))
        {
            var separator = pair.IndexOf('=');
            var key = Uri.UnescapeDataString(separator < 0 ? pair : pair[..separator]);
            if (!key.Equals("destination", StringComparison.OrdinalIgnoreCase)) continue;
            var value = separator < 0 ? "" : Uri.UnescapeDataString(pair[(separator + 1)..]);
            return string.IsNullOrWhiteSpace(value) ? null : value;
        }
        return null;
    }
}
