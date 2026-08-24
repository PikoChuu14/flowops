using System.Net;
using System.Text;
using FlowOps.Agent.Core;

var tests = new (string Name, Func<Task> Run)[] {
    ("config loading and server URL change", Config), ("click-through URL composition", Url),
    ("cursor persistence and duplicate suppression state", State), ("polling and authentication header", Poll),
    ("revoked credential handling", Revoked), ("encrypted credential storage", Credential), ("toast activation destination", ToastActivation), ("server unreachable behavior", Unreachable), ("backoff", Backoff)
};
var failed = 0;
foreach (var test in tests) { try { await test.Run(); Console.WriteLine($"PASS {test.Name}"); } catch (Exception ex) { failed++; Console.Error.WriteLine($"FAIL {test.Name}: {ex.Message}"); } }
return failed;

static string Temp() { var path = Path.Combine(Path.GetTempPath(), "flowops-agent-tests", Guid.NewGuid().ToString("N")); Directory.CreateDirectory(path); return path; }
static Task Config() { var root = Temp(); var paths = new AgentPaths(root); Directory.CreateDirectory(paths.Root); File.WriteAllText(paths.ServerUrlFile, "http://one:8080/"); Equal("http://one:8080/", new ServerConfiguration(paths).Load().ToString()); File.WriteAllText(paths.ServerUrlFile, "https://two/"); Equal("https://two/", new ServerConfiguration(paths).Load().ToString()); return Task.CompletedTask; }
static Task Url() { Equal("https://flowops.test/ppc/raw-material-arrivals?arrivalId=12", ServerConfiguration.Compose(new Uri("https://flowops.test"), "/ppc/raw-material-arrivals?arrivalId=12").ToString()); Throws<InvalidOperationException>(() => ServerConfiguration.Compose(new Uri("https://flowops.test"), "https://evil.test")); return Task.CompletedTask; }
static Task State() { var store = new AgentStateStore(new AgentPaths(Temp())); store.Save(new AgentState(42)); Equal(42L, store.Load().LastNotificationId); return Task.CompletedTask; }
static async Task Poll() { var handler = new Handler(req => { Equal("Device 1.secret", req.Headers.GetValues("Authorization").Single()); return Json(HttpStatusCode.OK, "[{\"id\":2,\"type\":\"TASK_ASSIGNED\",\"title\":\"Task\",\"message\":\"Assigned\",\"createdAt\":\"2026-08-24T10:00:00\",\"destination\":\"/?taskId=2\"}]"); }); var items = await new AgentApiClient(new HttpClient(handler)).PollAsync(new Uri("https://server"), "1.secret", 1, default); Equal(2L, items.Single().Id); }
static async Task Revoked() { var api = new AgentApiClient(new HttpClient(new Handler(_ => Json(HttpStatusCode.Unauthorized, "")))); await ThrowsAsync<DeviceAuthenticationException>(() => api.PollAsync(new Uri("https://server"), "bad", 0, default)); }
static Task Credential() { if (!OperatingSystem.IsWindows()) return Task.CompletedTask; var store = new DpapiCredentialStore(new AgentPaths(Temp())); store.Save("secret-device-token"); Equal("secret-device-token", store.Load()); store.Clear(); Equal<string?>(null, store.Load()); return Task.CompletedTask; }
static Task ToastActivation() { Equal("/projects?boardId=9&taskId=4", ToastActivationArguments.Destination("destination=%2Fprojects%3FboardId%3D9%26taskId%3D4")); Equal<string?>(null, ToastActivationArguments.Destination("action=open")); return Task.CompletedTask; }
static async Task Unreachable() { var api = new AgentApiClient(new HttpClient(new Handler(_ => throw new HttpRequestException("offline")))); await ThrowsAsync<HttpRequestException>(() => api.PollAsync(new Uri("https://server"), "token", 0, default)); }
static Task Backoff() { var b = new PollBackoff(); Equal(TimeSpan.FromSeconds(30), b.Failure()); Equal(TimeSpan.FromMinutes(1), b.Failure()); Equal(TimeSpan.FromMinutes(2), b.Failure()); Equal(TimeSpan.FromMinutes(5), b.Failure()); Equal(TimeSpan.FromMinutes(5), b.Failure()); Equal(TimeSpan.FromSeconds(30), b.Success()); return Task.CompletedTask; }
static HttpResponseMessage Json(HttpStatusCode code, string json) => new(code) { Content = new StringContent(json, Encoding.UTF8, "application/json") };
static void Equal<T>(T expected, T actual) { if (!EqualityComparer<T>.Default.Equals(expected, actual)) throw new Exception($"Expected {expected}, got {actual}"); }
static void Throws<T>(Action action) where T : Exception { try { action(); } catch (T) { return; } throw new Exception($"Expected {typeof(T).Name}"); }
static async Task ThrowsAsync<T>(Func<Task> action) where T : Exception { try { await action(); } catch (T) { return; } throw new Exception($"Expected {typeof(T).Name}"); }
sealed class Handler(Func<HttpRequestMessage, HttpResponseMessage> send) : HttpMessageHandler { protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) => Task.FromResult(send(request)); }
