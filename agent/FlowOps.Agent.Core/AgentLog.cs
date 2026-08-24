namespace FlowOps.Agent.Core;

public sealed class AgentLog
{
    private readonly AgentPaths _paths;
    public AgentLog(AgentPaths paths) => _paths = paths;
    public void Info(string message) => Write("INFO", message);
    public void Error(string message) => Write("ERROR", message);
    private void Write(string level, string message)
    {
        try
        {
            Directory.CreateDirectory(_paths.LogDirectory);
            var path = Path.Combine(_paths.LogDirectory, "agent.log");
            if (File.Exists(path) && new FileInfo(path).Length > 1_000_000)
            {
                var old = path + ".1"; if (File.Exists(old)) File.Delete(old); File.Move(path, old);
            }
            File.AppendAllText(path, $"{DateTimeOffset.Now:O} {level} {Sanitize(message)}{Environment.NewLine}");
        }
        catch { }
    }
    private static string Sanitize(string value) { var clean = value.Replace("\r", " ").Replace("\n", " "); return clean.Length > 500 ? clean[..500] : clean; }
}
