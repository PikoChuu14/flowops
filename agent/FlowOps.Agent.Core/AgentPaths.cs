namespace FlowOps.Agent.Core;

public sealed class AgentPaths
{
    public AgentPaths(string? localAppData = null)
    {
        Root = Path.Combine(localAppData ?? Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "FlowOps Client");
    }

    public string Root { get; }
    public string ServerUrlFile => Path.Combine(Root, "server-url.txt");
    public string CredentialFile => Path.Combine(Root, "device-credential.dat");
    public string StateFile => Path.Combine(Root, "agent-state.json");
    public string LogDirectory => Path.Combine(Root, "logs");
}
