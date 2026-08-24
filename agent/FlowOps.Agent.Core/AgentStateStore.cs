using System.Text.Json;

namespace FlowOps.Agent.Core;

public sealed record AgentState(long LastNotificationId = 0);

public sealed class AgentStateStore
{
    private readonly AgentPaths _paths;
    public AgentStateStore(AgentPaths paths) => _paths = paths;
    public AgentState Load()
    {
        try { return File.Exists(_paths.StateFile) ? JsonSerializer.Deserialize<AgentState>(File.ReadAllText(_paths.StateFile)) ?? new() : new(); }
        catch (JsonException) { return new(); }
    }
    public void Save(AgentState state) { Directory.CreateDirectory(_paths.Root); File.WriteAllText(_paths.StateFile, JsonSerializer.Serialize(state)); }
}
