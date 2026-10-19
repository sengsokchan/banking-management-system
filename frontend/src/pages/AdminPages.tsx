import { FormEvent, useEffect, useState } from "react";
import { api, fieldMessage } from "../api";
import { useSession } from "../auth";
import { money, when } from "../format";
import type { AuditLog, Page, Role, Settings, StaffUser } from "../types";
import { Badge, ErrorText, Field } from "../ui";

export function AuditPage() {
  const [query, setQuery] = useState("");
  const [success, setSuccess] = useState("");
  const [page, setPage] = useState<Page<AuditLog> | null>(null);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    const params = new URLSearchParams({ query, size: "30" });
    if (success) {
      params.set("success", success);
    }
    const handle = setTimeout(() => {
      api<Page<AuditLog>>(`/api/audit-logs?${params}`).then(setPage).catch(setError);
    }, 200);
    return () => clearTimeout(handle);
  }, [query, success]);

  return (
    <>
      <div>
        <p className="eyebrow">Accountability</p>
        <h1>Audit log</h1>
      </div>
      <ErrorText error={error} />
      <div className="card">
        <div className="toolbar">
          <div className="grow"><Field label="Search actor, action, or message"><input value={query} onChange={(event) => setQuery(event.target.value)} /></Field></div>
          <Field label="Result">
            <select value={success} onChange={(event) => setSuccess(event.target.value)}>
              <option value="">All</option>
              <option value="true">Succeeded</option>
              <option value="false">Failed</option>
            </select>
          </Field>
        </div>
        <div className="table-wrap">
          <table>
            <thead><tr><th>When</th><th>Who</th><th>Action</th><th>Record</th><th>Result</th></tr></thead>
            <tbody>
              {page?.content.map((item) => (
                <tr key={item.id}>
                  <td>{when(item.createdAt)} UTC</td>
                  <td>{item.actorUsername}</td>
                  <td>{item.action}<div className="note">{item.message}</div></td>
                  <td>{item.entityType} {item.entityId || ""}</td>
                  <td><Badge value={item.success ? "COMPLETED" : "REJECTED"} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </>
  );
}

export function StaffPage() {
  const { session } = useSession();
  const [page, setPage] = useState<Page<StaffUser> | null>(null);
  const [roles, setRoles] = useState<Role[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [form, setForm] = useState({ username: "", password: "", fullName: "", email: "", roles: ["TELLER"] });
  const canWrite = session?.user.permissions.includes("USER_WRITE") ?? false;

  function load() {
    api<Page<StaffUser>>("/api/users?size=50").then(setPage).catch(setError);
    api<Role[]>("/api/roles").then(setRoles).catch(setError);
  }

  useEffect(load, []);

  async function create(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      await api("/api/users", { method: "POST", body: JSON.stringify(form) });
      setForm({ username: "", password: "", fullName: "", email: "", roles: ["TELLER"] });
      load();
    } catch (caught) {
      setError(caught);
    }
  }

  async function toggle(user: StaffUser) {
    setError(null);
    try {
      await api(`/api/users/${user.id}`, {
        method: "PUT",
        body: JSON.stringify({ fullName: user.fullName, email: user.email, active: !user.active, roles: user.roles })
      });
      load();
    } catch (caught) {
      setError(caught);
    }
  }

  function toggleRole(role: string) {
    setForm((current) => ({
      ...current,
      roles: current.roles.includes(role) ? current.roles.filter((item) => item !== role) : [...current.roles, role]
    }));
  }

  return (
    <>
      <div>
        <p className="eyebrow">Access</p>
        <h1>Staff</h1>
      </div>
      <ErrorText error={error} />
      {canWrite ? (
        <form className="card stack" onSubmit={(event) => void create(event)}>
          <h2>New employee</h2>
          <div className="split">
            <Field label="Username" error={fieldMessage(error, "username")}><input value={form.username} onChange={(event) => setForm({ ...form, username: event.target.value })} required /></Field>
            <Field label="Temporary password" error={fieldMessage(error, "password")}><input type="password" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} required /></Field>
          </div>
          <Field label="Full name" error={fieldMessage(error, "fullName")}><input value={form.fullName} onChange={(event) => setForm({ ...form, fullName: event.target.value })} required /></Field>
          <Field label="Email" error={fieldMessage(error, "email")}><input type="email" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} required /></Field>
          <div className="actions">
            {roles.map((role) => (
              <label key={role.name}><input type="checkbox" checked={form.roles.includes(role.name)} onChange={() => toggleRole(role.name)} /> {role.name}</label>
            ))}
          </div>
          <button className="btn primary" type="submit">Create employee</button>
        </form>
      ) : null}
      <section className="card table-wrap">
        <table>
          <thead><tr><th>Employee</th><th>Roles</th><th>Status</th><th></th></tr></thead>
          <tbody>
            {page?.content.map((user) => (
              <tr key={user.id}>
                <td>{user.fullName}<div className="note">{user.username} · {user.email}</div></td>
                <td>{user.roles.join(", ")}</td>
                <td><Badge value={user.active ? "ACTIVE" : "INACTIVE"} /></td>
                <td>{canWrite ? <button className="btn" type="button" onClick={() => void toggle(user)}>{user.active ? "Deactivate" : "Activate"}</button> : null}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </>
  );
}

export function SettingsPage() {
  const [settings, setSettings] = useState<Settings | null>(null);
  const [name, setName] = useState("");
  const [threshold, setThreshold] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    api<Settings>("/api/settings").then((value) => {
      setSettings(value);
      setName(value.bankDisplayName);
      setThreshold(String(value.approvalThreshold));
    }).catch(setError);
  }, []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaved(false);
    setError(null);
    try {
      const updated = await api<Settings>("/api/settings", {
        method: "PUT",
        body: JSON.stringify({ bankDisplayName: name, approvalThreshold: Number(threshold) })
      });
      setSettings(updated);
      setSaved(true);
    } catch (caught) {
      setError(caught);
    }
  }

  return (
    <form className="card stack" onSubmit={(event) => void submit(event)}>
      <div>
        <p className="eyebrow">System</p>
        <h1>Settings</h1>
      </div>
      <ErrorText error={error} />
      {saved ? <div className="banner">Settings saved.</div> : null}
      <Field label="Display name" error={fieldMessage(error, "bankDisplayName")}>
        <input value={name} onChange={(event) => setName(event.target.value)} required />
      </Field>
      <Field label="Approval threshold (simulated USD)" error={fieldMessage(error, "approvalThreshold")}>
        <input value={threshold} onChange={(event) => setThreshold(event.target.value)} inputMode="decimal" required />
      </Field>
      <p className="note">Last updated {settings ? when(settings.updatedAt) : "—"} UTC. Current threshold {settings ? money(settings.approvalThreshold) : "—"}.</p>
      <button className="btn primary" type="submit">Save settings</button>
    </form>
  );
}
