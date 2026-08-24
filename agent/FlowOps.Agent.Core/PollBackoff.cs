namespace FlowOps.Agent.Core;

public sealed class PollBackoff
{
    private static readonly TimeSpan[] Failures = [TimeSpan.FromSeconds(30), TimeSpan.FromMinutes(1), TimeSpan.FromMinutes(2), TimeSpan.FromMinutes(5)];
    private int _failureCount;
    public TimeSpan Success() { _failureCount = 0; return TimeSpan.FromSeconds(30); }
    public TimeSpan Failure() { var result = Failures[Math.Min(_failureCount, Failures.Length - 1)]; _failureCount++; return result; }
    public int FailureCount => _failureCount;
}
