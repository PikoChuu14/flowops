using FlowOps.Agent.Core;

namespace FlowOps.NotificationAgent;

internal sealed class RegistrationForm : Form
{
    private readonly AgentPaths _paths; private readonly AgentApiClient _api; private readonly DpapiCredentialStore _credentials;
    private readonly AgentStateStore _state; private readonly AgentLog _log;
    private readonly TextBox _code = new() { Width = 250, CharacterCasing = CharacterCasing.Upper };
    private readonly Label _message = new() { AutoSize = true, MaximumSize = new Size(410, 0) };
    private readonly Button _register = new() { Text = "Register this PC", AutoSize = true };

    public RegistrationForm(AgentPaths paths, AgentApiClient api, DpapiCredentialStore credentials, AgentStateStore state, AgentLog log)
    {
        _paths = paths; _api = api; _credentials = credentials; _state = state; _log = log;
        Text = "FlowOps Desktop Notifications"; Width = 470; Height = 260; StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.FixedDialog; MaximizeBox = false; MinimizeBox = false;
        var open = new Button { Text = "Open FlowOps", AutoSize = true };
        open.Click += (_, _) => { try { System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo(new ServerConfiguration(_paths).Load().ToString()) { UseShellExecute = true }); } catch (Exception ex) { _message.Text = ex.Message; } };
        _register.Click += async (_, _) => await RegisterAsync();
        var layout = new FlowLayoutPanel { Dock = DockStyle.Fill, FlowDirection = FlowDirection.TopDown, WrapContents = false, Padding = new Padding(20), AutoScroll = true };
        layout.Controls.Add(new Label { Text = "Generate a device code in FlowOps under Desktop Notifications, then enter it here.", AutoSize = true, MaximumSize = new Size(410, 0) });
        layout.Controls.Add(_code); layout.Controls.Add(_register); layout.Controls.Add(open); layout.Controls.Add(_message); Controls.Add(layout);
    }

    private async Task RegisterAsync()
    {
        _register.Enabled = false; _message.Text = "Registering…";
        try
        {
            var server = new ServerConfiguration(_paths).Load();
            var result = await _api.ExchangeAsync(server, _code.Text, Environment.MachineName + " / " + Environment.UserName, CancellationToken.None);
            string? previous = null;
            try { previous = _credentials.Load(); } catch { }
            if (!string.IsNullOrWhiteSpace(previous) && previous != result.DeviceToken)
            {
                try { await _api.RevokeAsync(server, previous, CancellationToken.None); }
                catch (Exception ex) { _log.Error($"Previous device cleanup failed: {ex.GetType().Name}"); }
            }
            _credentials.Save(result.DeviceToken); _state.Save(new AgentState(result.NotificationCursor));
            _message.Text = $"Connected as {result.UserName}."; _log.Info("Device registration completed."); DialogResult = DialogResult.OK; Close();
        }
        catch (Exception ex) { _message.Text = ex.Message; _log.Error($"Registration failed: {ex.GetType().Name}"); }
        finally { _register.Enabled = true; }
    }
}
