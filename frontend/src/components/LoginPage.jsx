import { useState } from "react";
import { useAuth } from "../context/AuthContext";
import FlowOpsLogo from "./FlowOpsLogo";
import InstallFlowOps from "./InstallFlowOps";

function MailIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <path d="M4 6.5h16v11H4z" />
      <path d="m4.5 7 7.5 6 7.5-6" />
    </svg>
  );
}

function LockIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <rect x="5" y="10" width="14" height="10" rx="2" />
      <path d="M8 10V7a4 4 0 0 1 8 0v3" />
    </svg>
  );
}

function EyeIcon({ hidden }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <path d="M3 12s3.2-5 9-5 9 5 9 5-3.2 5-9 5-9-5-9-5Z" />
      <circle cx="12" cy="12" r="2" />
      {hidden && <path d="m4 4 16 16" />}
    </svg>
  );
}

function LoginPage() {
  const { login } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");
    setLoading(true);

    try {
      const response = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      });

      if (!response.ok) {
        throw new Error(response.status === 401 || response.status === 403 ? "credentials" : "server");
      }

      login(await response.json());
    } catch (submitError) {
      console.error("Login failed:", submitError);
      setError(submitError.message === "credentials"
        ? "Incorrect username/email or password."
        : "Unable to sign in. Please try again.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="login-page">
      <div className="login-orbit login-orbit-one" aria-hidden="true" />
      <div className="login-orbit login-orbit-two" aria-hidden="true" />

      <section className="login-card" aria-labelledby="login-heading">
        <div className="login-brand">
          <FlowOpsLogo className="login-brand-mark" decorative />
          <div>
            <p className="login-brand-name">FlowOps</p>
            <p className="login-brand-tagline">Operational workflow and daily task tracking</p>
          </div>
        </div>

        <div className="login-intro">
          <p className="login-eyebrow">Secure workspace access</p>
          <h1 id="login-heading">Welcome back</h1>
          <p>Sign in to continue to FlowOps</p>
        </div>

        <form className="login-form" onSubmit={handleSubmit}>
          <div className="login-field">
            <label htmlFor="login-email">Username or email</label>
            <div className="login-input-wrap">
              <MailIcon />
              <input
                id="login-email"
                type="text"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                autoComplete="username"
                autoFocus
                required
              />
            </div>
          </div>

          <div className="login-field">
            <label htmlFor="login-password">Password</label>
            <div className="login-input-wrap">
              <LockIcon />
              <input
                id="login-password"
                type={showPassword ? "text" : "password"}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                autoComplete="current-password"
                required
              />
              <button
                className="login-password-toggle"
                type="button"
                onClick={() => setShowPassword((visible) => !visible)}
                aria-label={showPassword ? "Hide password" : "Show password"}
                aria-pressed={showPassword}
              >
                <EyeIcon hidden={!showPassword} />
              </button>
            </div>
          </div>

          {error && <p className="login-error" role="alert">{error}</p>}

          <button className="login-submit" type="submit" disabled={loading}>
            {loading ? "Signing in..." : "Sign In"}
            {!loading && <span aria-hidden="true">→</span>}
          </button>
        </form>

        <InstallFlowOps compact />

        <p className="login-help">Need help signing in? Contact your administrator.</p>
        <p className="login-footer">For authorized users only.</p>
      </section>
    </main>
  );
}

export default LoginPage;
