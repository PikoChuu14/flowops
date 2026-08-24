using System.Net;
using System.Net.Http.Json;

namespace FlowOps.Agent.Core;

public sealed record AgentNotification(long Id, string Type, string Title, string Message, DateTime CreatedAt, string? Destination);
public sealed record ExchangeResponse(string DeviceToken, long NotificationCursor, long UserId, string UserName);
public sealed class DeviceAuthenticationException : Exception { public DeviceAuthenticationException() : base("Device authentication is required.") {} }

public sealed class AgentApiClient
{
    private readonly HttpClient _http;
    public AgentApiClient(HttpClient http) => _http = http;

    public async Task<ExchangeResponse> ExchangeAsync(Uri server, string code, string deviceName, CancellationToken cancellationToken)
    {
        using var response = await _http.PostAsJsonAsync(new Uri(server, "/api/devices/exchange"), new { code, deviceName }, cancellationToken);
        if (!response.IsSuccessStatusCode) throw new InvalidOperationException(response.StatusCode == HttpStatusCode.Unauthorized ? "The registration code is invalid, expired, or already used." : $"Registration failed ({(int)response.StatusCode}).");
        return (await response.Content.ReadFromJsonAsync<ExchangeResponse>(cancellationToken: cancellationToken))!;
    }

    public async Task<IReadOnlyList<AgentNotification>> PollAsync(Uri server, string credential, long after, CancellationToken cancellationToken)
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, new Uri(server, $"/api/agent/notifications?after={Math.Max(0, after)}&limit=100"));
        request.Headers.TryAddWithoutValidation("Authorization", "Device " + credential);
        using var response = await _http.SendAsync(request, cancellationToken);
        if (response.StatusCode is HttpStatusCode.Unauthorized or HttpStatusCode.Forbidden) throw new DeviceAuthenticationException();
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<List<AgentNotification>>(cancellationToken: cancellationToken) ?? [];
    }

    public async Task RevokeAsync(Uri server, string credential, CancellationToken cancellationToken)
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, new Uri(server, "/api/agent/revoke"));
        request.Headers.TryAddWithoutValidation("Authorization", "Device " + credential);
        using var response = await _http.SendAsync(request, cancellationToken);
        if (response.StatusCode is not (HttpStatusCode.Unauthorized or HttpStatusCode.Forbidden)) response.EnsureSuccessStatusCode();
    }
}
