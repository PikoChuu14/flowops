import { useCallback, useEffect, useState } from "react";
import { apiFetch } from "../api/apiFetch";

function formatDate(value) {
  if (!value) return "Never";
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

export default function DesktopNotificationsPage() {
  const [devices, setDevices] = useState([]);
  const [code, setCode] = useState(null);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");

  const load = useCallback(async () => {
    try {
      const response = await apiFetch("/api/devices");
      if (!response.ok) throw new Error(`Device request failed (${response.status}).`);
      setDevices(await response.json());
    } catch (error) {
      setMessage(error.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  async function createCode() {
    setMessage("");
    const response = await apiFetch("/api/devices/register-code", { method: "POST" });
    if (!response.ok) { setMessage(`Could not create a device code (${response.status}).`); return; }
    setCode(await response.json());
  }

  async function revoke(id) {
    setMessage("");
    const response = await apiFetch(`/api/devices/revoke/${id}`, { method: "POST" });
    if (!response.ok) { setMessage(`Could not revoke this device (${response.status}).`); return; }
    setDevices((current) => current.map((device) => device.id === id ? { ...device, active: false, revokedAt: new Date().toISOString() } : device));
  }

  const active = devices.filter((device) => device.active);
  return (
    <section className="settings-page desktop-notifications-page">
      <div className="page-heading"><div><p className="eyebrow">Account</p><h1>Desktop Notifications</h1><p>Receive FlowOps notifications from the Windows tray agent even when your browser is closed.</p></div></div>
      <div className="settings-card">
        <h2>{active.length ? "Registered devices" : "No registered device"}</h2>
        <p>In the FlowOps tray menu, open <strong>Settings</strong>. Generate a code here, then enter it in the agent. Codes expire after 10 minutes and work once.</p>
        <button type="button" className="primary-button" onClick={createCode}>Enable on this PC</button>
        {code && <div className="device-code" role="status"><span>Device code</span><strong>{code.code}</strong><small>Expires {formatDate(code.expiresAt)}</small></div>}
        {message && <p className="form-message error">{message}</p>}
      </div>
      <div className="settings-card">
        <h2>Your Windows devices</h2>
        {loading ? <p>Loading…</p> : devices.length === 0 ? <p>No Windows notification agents have been registered.</p> : (
          <div className="device-list">{devices.map((device) => (
            <article key={device.id} className="device-row">
              <div><strong>{device.deviceName}</strong><span>Created {formatDate(device.createdAt)} · Last used {formatDate(device.lastUsedAt)}</span><small>{device.active ? "Active" : `Revoked ${formatDate(device.revokedAt)}`}</small></div>
              {device.active && <button type="button" className="secondary-button" onClick={() => revoke(device.id)}>Revoke</button>}
            </article>
          ))}</div>
        )}
      </div>
      <p className="security-note"><strong>Security note:</strong> Use trusted HTTPS for production. On an HTTP-only LAN, device authentication and notification traffic are not encrypted in transit.</p>
    </section>
  );
}
