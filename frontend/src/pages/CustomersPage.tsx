import { FormEvent, useEffect, useState } from "react";
import { Link, Navigate, useNavigate, useParams } from "react-router-dom";
import { api, fieldMessage } from "../api";
import { useSession } from "../auth";
import { dateOnly, when } from "../format";
import type { Account, Customer, Page } from "../types";
import { Badge, ErrorText, Field } from "../ui";

type CustomerForm = {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  dateOfBirth: string;
  addressLine: string;
  city: string;
  country: string;
};

const EMPTY: CustomerForm = {
  firstName: "",
  lastName: "",
  email: "",
  phone: "",
  dateOfBirth: "",
  addressLine: "",
  city: "",
  country: "Exampleland"
};

export function CustomersPage() {
  const { session } = useSession();
  const [query, setQuery] = useState("");
  const [page, setPage] = useState<Page<Customer> | null>(null);
  const [error, setError] = useState<unknown>(null);
  const canWrite = session?.user.permissions.includes("CUSTOMER_WRITE") ?? false;

  useEffect(() => {
    const handle = setTimeout(() => {
      api<Page<Customer>>(`/api/customers?query=${encodeURIComponent(query)}&size=20`)
        .then(setPage)
        .catch(setError);
    }, 200);
    return () => clearTimeout(handle);
  }, [query]);

  return (
    <>
      <div className="toolbar">
        <div>
          <p className="eyebrow">Directory</p>
          <h1>Customers</h1>
        </div>
        {canWrite ? <Link className="btn primary" to="/customers/new">New customer</Link> : null}
      </div>
      <ErrorText error={error} />
      <div className="card">
        <Field label="Search by name or customer number">
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Amara or C-10000001" />
        </Field>
        <div className="table-wrap">
          <table>
            <thead>
              <tr><th>Customer</th><th>Number</th><th>Registered</th><th>Status</th></tr>
            </thead>
            <tbody>
              {page?.content.map((customer) => (
                <tr key={customer.id}>
                  <td><Link to={`/customers/${customer.id}`}>{customer.firstName} {customer.lastName}</Link><div className="note">{customer.email}</div></td>
                  <td>{customer.customerNumber}</td>
                  <td>{when(customer.registeredAt)} UTC</td>
                  <td><Badge value={customer.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
          {page && page.content.length === 0 ? <p className="empty">No simulated customers match that search.</p> : null}
        </div>
      </div>
    </>
  );
}

export function CustomerFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { session } = useSession();
  const canWrite = session?.user.permissions.includes("CUSTOMER_WRITE") ?? false;
  const [form, setForm] = useState<CustomerForm>(EMPTY);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!id || !canWrite) {
      return;
    }
    api<{ customer: Customer }>(`/api/customers/${id}`).then((detail) => {
      const customer = detail.customer;
      setForm({
        firstName: customer.firstName,
        lastName: customer.lastName,
        email: customer.email,
        phone: customer.phone ?? "",
        dateOfBirth: customer.dateOfBirth,
        addressLine: customer.addressLine,
        city: customer.city,
        country: customer.country
      });
    }).catch(setError);
  }, [id, canWrite]);

  if (!canWrite) {
    return <Navigate to="/customers" replace />;
  }

  function update(field: keyof CustomerForm, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const saved = await api<Customer>(id ? `/api/customers/${id}` : "/api/customers", {
        method: id ? "PUT" : "POST",
        body: JSON.stringify({ ...form, phone: form.phone || null })
      });
      navigate(`/customers/${saved.id}`);
    } catch (caught) {
      setError(caught);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="card stack" onSubmit={(event) => void submit(event)}>
      <h1>{id ? "Update customer" : "New customer"}</h1>
      <ErrorText error={error} />
      <div className="split">
        <Field label="First name" error={fieldMessage(error, "firstName")}><input value={form.firstName} onChange={(event) => update("firstName", event.target.value)} required /></Field>
        <Field label="Last name" error={fieldMessage(error, "lastName")}><input value={form.lastName} onChange={(event) => update("lastName", event.target.value)} required /></Field>
      </div>
      <Field label="Email" error={fieldMessage(error, "email")}><input type="email" value={form.email} onChange={(event) => update("email", event.target.value)} required /></Field>
      <Field label="Phone" error={fieldMessage(error, "phone")}><input value={form.phone} onChange={(event) => update("phone", event.target.value)} /></Field>
      <Field label="Date of birth" error={fieldMessage(error, "dateOfBirth")}><input type="date" value={form.dateOfBirth} onChange={(event) => update("dateOfBirth", event.target.value)} required /></Field>
      <Field label="Address" error={fieldMessage(error, "addressLine")}><input value={form.addressLine} onChange={(event) => update("addressLine", event.target.value)} required /></Field>
      <div className="split">
        <Field label="City" error={fieldMessage(error, "city")}><input value={form.city} onChange={(event) => update("city", event.target.value)} required /></Field>
        <Field label="Country" error={fieldMessage(error, "country")}><input value={form.country} onChange={(event) => update("country", event.target.value)} required /></Field>
      </div>
      <div className="actions">
        <button className="btn primary" disabled={busy} type="submit">{busy ? "Saving…" : "Save customer"}</button>
        <Link className="btn" to="/customers">Cancel</Link>
      </div>
    </form>
  );
}

export function CustomerDetailPage() {
  const { id } = useParams();
  const { session } = useSession();
  const [detail, setDetail] = useState<{ customer: Customer; accounts: Account[] } | null>(null);
  const [error, setError] = useState<unknown>(null);
  const canWrite = session?.user.permissions.includes("CUSTOMER_WRITE") ?? false;
  const canDeactivate = session?.user.permissions.includes("CUSTOMER_DEACTIVATE") ?? false;

  function load() {
    api<{ customer: Customer; accounts: Account[] }>(`/api/customers/${id}`).then(setDetail).catch(setError);
  }

  useEffect(load, [id]);

  async function changeStatus(action: "deactivate" | "activate") {
    setError(null);
    try {
      await api(`/api/customers/${id}/${action}`, { method: "POST" });
      load();
    } catch (caught) {
      setError(caught);
    }
  }

  if (!detail) {
    return <ErrorText error={error} />;
  }
  const customer = detail.customer;
  return (
    <>
      <div className="toolbar">
        <div>
          <p className="eyebrow">{customer.customerNumber}</p>
          <h1>{customer.firstName} {customer.lastName}</h1>
        </div>
        <Badge value={customer.status} />
      </div>
      <ErrorText error={error} />
      <section className="card detail-list">
        <span>Registered</span><strong>{when(customer.registeredAt)} UTC</strong>
        <span>Email</span><strong>{customer.email}</strong>
        <span>Phone</span><strong>{customer.phone || "—"}</strong>
        <span>Born</span><strong>{dateOnly(customer.dateOfBirth)}</strong>
        <span>Address</span><strong>{customer.addressLine}, {customer.city}, {customer.country}</strong>
      </section>
      <div className="actions">
        {canWrite ? <Link className="btn" to={`/customers/${customer.id}/edit`}>Edit</Link> : null}
        {canDeactivate && customer.status === "ACTIVE" ? <button className="btn-danger" type="button" onClick={() => void changeStatus("deactivate")}>Deactivate</button> : null}
        {canWrite && customer.status === "INACTIVE" ? <button className="btn" type="button" onClick={() => void changeStatus("activate")}>Reactivate</button> : null}
      </div>
      <section className="card">
        <h2>Accounts</h2>
        <div className="table-wrap">
          <table>
            <thead><tr><th>Number</th><th>Type</th><th>Status</th><th className="num">Balance</th></tr></thead>
            <tbody>
              {detail.accounts.map((account) => (
                <tr key={account.id}>
                  <td><Link to={`/accounts/${account.id}`}>{account.accountNumber}</Link></td>
                  <td>{account.accountType}</td>
                  <td><Badge value={account.status} /></td>
                  <td className="money">{account.balance.toFixed(2)} {account.currency}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {detail.accounts.length === 0 ? <p className="empty">No accounts yet.</p> : null}
        </div>
      </section>
    </>
  );
}
