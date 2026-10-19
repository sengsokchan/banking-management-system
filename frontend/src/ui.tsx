import { NavLink, Navigate, Outlet } from "react-router-dom";
import { useState, type ReactNode } from "react";
import { ApiError } from "./api";
import { useSession } from "./auth";
import { title } from "./format";

export function RequireAuth() {
  const { session } = useSession();
  if (!session) {
    return <Navigate to="/login" replace />;
  }
  return <Outlet />;
}

export function can(permission: string) {
  return function Guard() {
    const { session } = useSession();
    if (!session?.user.permissions.includes(permission)) {
      return <Navigate to="/" replace />;
    }
    return <Outlet />;
  };
}

const NAV = [
  { to: "/", label: "Overview", permission: "" },
  { to: "/customers", label: "Customers", permission: "CUSTOMER_READ" },
  { to: "/accounts", label: "Accounts", permission: "ACCOUNT_READ" },
  { to: "/transactions", label: "Transactions", permission: "TRANSACTION_READ" },
  { to: "/approvals", label: "Approvals", permission: "TRANSACTION_APPROVE" },
  { to: "/audit", label: "Audit log", permission: "AUDIT_READ" },
  { to: "/staff", label: "Staff", permission: "USER_READ" },
  { to: "/settings", label: "Settings", permission: "SETTINGS_MANAGE" }
];

export function Shell() {
  const { session, logout } = useSession();
  const [open, setOpen] = useState(false);
  const items = NAV.filter((item) => !item.permission || session?.user.permissions.includes(item.permission));
  return (
    <div className="shell">
      <aside className={open ? "sidebar open" : "sidebar"}>
        <div className="brand-lockup">
          <div className="mark">M</div>
          <div>
            <strong>Meridian Trust</strong>
            <span>Operations</span>
          </div>
        </div>
        <nav>
          {items.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.to === "/"} onClick={() => setOpen(false)} className={({ isActive }) => (isActive ? "active" : "")}>
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <button className="menu-button" type="button" onClick={() => setOpen((value) => !value)}>Menu</button>
          <div className="who">
            <strong>{session?.user.fullName}</strong>
            <span className="note">{session?.user.roles.map(title).join(", ")}</span>
          </div>
          <button className="btn" type="button" onClick={() => void logout()}>Sign out</button>
        </header>
        <main className="page">
          <div className="banner">Simulated portfolio data. No real customer or financial information is stored.</div>
          <Outlet />
        </main>
      </div>
    </div>
  );
}

export function Badge({ value }: { value: string }) {
  const tone = value === "ACTIVE" || value === "COMPLETED" || value === "APPROVED" ? "good" : value === "FROZEN" || value === "PENDING_APPROVAL" || value === "PENDING" ? "warn" : value === "CLOSED" || value === "INACTIVE" || value === "REJECTED" ? "bad" : "";
  return <span className={`badge ${tone}`}>{title(value)}</span>;
}

export function ErrorText({ error }: { error: unknown }) {
  if (!error) {
    return null;
  }
  return <div className="alert" role="alert">{error instanceof ApiError ? error.message : "The request could not be completed."}</div>;
}

export function Field({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
  return (
    <label>
      {label}
      {children}
      {error ? <span className="field-error">{error}</span> : null}
    </label>
  );
}
