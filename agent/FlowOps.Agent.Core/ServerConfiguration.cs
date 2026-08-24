namespace FlowOps.Agent.Core;

public sealed class ServerConfiguration
{
    private readonly AgentPaths _paths;
    public ServerConfiguration(AgentPaths paths) => _paths = paths;

    public Uri Load()
    {
        if (!File.Exists(_paths.ServerUrlFile)) throw new InvalidOperationException("FlowOps server address is not configured.");
        var value = File.ReadAllText(_paths.ServerUrlFile).Trim().TrimEnd('/');
        if (!Uri.TryCreate(value, UriKind.Absolute, out var uri) ||
            (uri.Scheme != Uri.UriSchemeHttp && uri.Scheme != Uri.UriSchemeHttps) ||
            !string.IsNullOrEmpty(uri.UserInfo))
            throw new InvalidOperationException("FlowOps server address must be a valid HTTP or HTTPS URL without credentials.");
        return uri;
    }

    public static Uri Compose(Uri server, string? destination)
    {
        if (string.IsNullOrWhiteSpace(destination)) return server;
        if (Uri.TryCreate(destination, UriKind.Absolute, out _)) throw new InvalidOperationException("Notification destination must be relative.");
        var clean = destination.StartsWith('/') ? destination[1..] : destination;
        return new Uri(server.ToString().TrimEnd('/') + "/" + clean, UriKind.Absolute);
    }
}
