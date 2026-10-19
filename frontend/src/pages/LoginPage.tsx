import { FormEvent, useState } from "react";
import { Navigate } from "react-router-dom";
import { fieldMessage } from "../api";
import { useSession } from "../auth";
import { ErrorText, Field } from "../ui";

const DEMOS = [
  ["Admin", "admin", "ChangeMe-Admin-1"],
  ["Manager", "manager", "ChangeMe-Manager-1"],
  ["Teller", "teller", "ChangeMe-Teller-1"],
  ["Auditor", "auditor", "ChangeMe-Auditor-1"]
] as const;

export function LoginPage() {
  const { session, login } = useSession();
  const [username, setUsername] = useState("teller");
  const [password, setPassword] = useState("ChangeMe-Teller-1");
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  if (session) {
    return <Navigate to="/" replace />;
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(username, password);
    } catch (caught) {
      setError(caught);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-screen">
      <section className="login-story">
        <div className="brand-lockup">
          <div className="mark">SC</div>
          <div>
            <strong>SC Peace SK Bank</strong>
            <span>Simulated bank</span>
          </div>
        </div>
        <div>
          <p className="eyebrow">Portfolio operations console</p>
          <h1>Ledger work, without real money.</h1>
          <p>Employees sign in, permissions are checked on the server, and every simulated transfer is recorded. The people and balances in this system are invented.</p>
        </div>
        <p>Exampleland · demonstration only</p>
      </section>
      <section className="login-panel">
        <form className="login-card stack" onSubmit={(event) => void submit(event)}>
          <div>
            <p className="eyebrow">Employee sign in</p>
            <h2>Welcome back</h2>
            <p className="note">Use a demo profile to fill the form, then sign in. The password is still checked by the API.</p>
          </div>
          <div className="demo-grid">
            {DEMOS.map(([label, user, pass]) => (
              <button key={user} type="button" onClick={() => { setUsername(user); setPassword(pass); }}>
                {label}
                <small>{user}</small>
              </button>
            ))}
          </div>
          <ErrorText error={error} />
          <Field label="Username" error={fieldMessage(error, "username")}>
            <input value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" required />
          </Field>
          <Field label="Password" error={fieldMessage(error, "password")}>
            <input value={password} onChange={(event) => setPassword(event.target.value)} type="password" autoComplete="current-password" required />
          </Field>
          <button className="btn primary" type="submit" disabled={busy}>{busy ? "Signing in…" : "Sign in"}</button>
        </form>
      </section>
    </div>
  );
}
