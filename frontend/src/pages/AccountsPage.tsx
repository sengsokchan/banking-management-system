import { FormEvent, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, fieldMessage } from "../api";
import { useSession } from "../auth";
import { money, when } from "../format";
import type { Account, Customer, Page, Transaction } from "../types";
import { Badge, ErrorText, Field } from "../ui";

export function AccountsPage() {
  const { session } = useSession();
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState<Page<Account> | null>(null);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [form, setForm] = useState({ customerId: "", accountType: "CHECKING", initialDeposit: "0.00" });
  const canWrite = session?.user.permissions.includes("ACCOUNT_WRITE") ?? false;

  function load() {
    const params = new URLSearchParams({ query, size: "20" });
    if (status) {
      params.set("status", status);
    }
    api<Page<Account>>(`/api/accounts?${params}`).then(setPage).catch(setError);
  }

  useEffect(() => {
    const handle = setTimeout(load, 200);
    return () => clearTimeout(handle);
  }, [query, status]);

  useEffect(() => {
    if (canWrite) {
      api<Page<Customer>>("/api/customers?size=100").then((result) => setCustomers(result.content.filter((item) => item.status === "ACTIVE"))).catch(setError);
    }
  }, [canWrite]);

  async function create(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      await api("/api/accounts", {
        method: "POST",
        body: JSON.stringify({ ...form, initialDeposit: Number(form.initialDeposit) })
      });
      setForm({ customerId: "", accountType: "CHECKING", initialDeposit: "0.00" });
      load();
    } catch (caught) {
      setError(caught);
    }
  }

  return (
    <>
      <div>
        <p className="eyebrow">Ledger</p>
        <h1>Accounts</h1>
      </div>
      <ErrorText error={error} />
      {canWrite ? (
        <form className="card stack" onSubmit={(event) => void create(event)}>
          <h2>Open an account</h2>
          <div className="split">
            <Field label="Customer" error={fieldMessage(error, "customerId")}>
              <select value={form.customerId} onChange={(event) => setForm({ ...form, customerId: event.target.value })} required>
                <option value="">Select</option>
                {customers.map((customer) => <option key={customer.id} value={customer.id}>{customer.customerNumber} · {customer.firstName} {customer.lastName}</option>)}
              </select>
            </Field>
            <Field label="Type" error={fieldMessage(error, "accountType")}>
              <select value={form.accountType} onChange={(event) => setForm({ ...form, accountType: event.target.value })}>
                <option value="CHECKING">Checking</option>
                <option value="SAVINGS">Savings</option>
              </select>
            </Field>
          </div>
          <Field label="Opening deposit (simulated USD)" error={fieldMessage(error, "initialDeposit")}>
            <input value={form.initialDeposit} onChange={(event) => setForm({ ...form, initialDeposit: event.target.value })} inputMode="decimal" />
          </Field>
          <button className="btn primary" type="submit">Open account</button>
        </form>
      ) : null}
      <div className="card">
        <div className="toolbar">
          <div className="grow">
            <Field label="Search number or owner">
              <input value={query} onChange={(event) => setQuery(event.target.value)} />
            </Field>
          </div>
          <Field label="Status">
            <select value={status} onChange={(event) => setStatus(event.target.value)}>
              <option value="">All</option>
              <option value="ACTIVE">Active</option>
              <option value="FROZEN">Frozen</option>
              <option value="CLOSED">Closed</option>
            </select>
          </Field>
        </div>
        <AccountTable accounts={page?.content ?? []} />
      </div>
    </>
  );
}

export function AccountDetailPage() {
  const { id } = useParams();
  const { session } = useSession();
  const [account, setAccount] = useState<Account | null>(null);
  const [history, setHistory] = useState<Transaction[]>([]);
  const [error, setError] = useState<unknown>(null);
  const canStatus = session?.user.permissions.includes("ACCOUNT_STATUS") ?? false;

  function load() {
    api<Account>(`/api/accounts/${id}`).then(setAccount).catch(setError);
    api<Page<Transaction>>(`/api/transactions?accountId=${id}&size=20`).then((result) => setHistory(result.content)).catch(setError);
  }

  useEffect(load, [id]);

  async function act(action: "freeze" | "activate" | "close") {
    setError(null);
    try {
      setAccount(await api<Account>(`/api/accounts/${id}/${action}`, { method: "POST" }));
    } catch (caught) {
      setError(caught);
    }
  }

  if (!account) {
    return <ErrorText error={error} />;
  }
  return (
    <>
      <div className="toolbar">
        <div>
          <p className="eyebrow">{account.accountType} · {account.currency}</p>
          <h1>{account.accountNumber}</h1>
        </div>
        <Badge value={account.status} />
      </div>
      <ErrorText error={error} />
      <section className="stats">
        <article className="card stat"><span>Simulated balance</span><strong>{money(account.balance)}</strong></article>
        <article className="card stat"><span>Owner</span><strong><Link to={`/customers/${account.customerId}`}>{account.customerName}</Link></strong><span>{account.customerNumber}</span></article>
        <article className="card stat"><span>Opened</span><strong>{when(account.openedAt)}</strong><span>UTC</span></article>
        <article className="card stat"><span>Closed</span><strong>{account.closedAt ? when(account.closedAt) : "—"}</strong></article>
      </section>
      {canStatus ? (
        <div className="actions">
          {account.status === "ACTIVE" ? <button className="btn" type="button" onClick={() => void act("freeze")}>Freeze</button> : null}
          {account.status === "FROZEN" ? <button className="btn" type="button" onClick={() => void act("activate")}>Activate</button> : null}
          {account.status !== "CLOSED" ? <button className="btn-danger" type="button" onClick={() => void act("close")}>Close</button> : null}
        </div>
      ) : null}
      <section className="card">
        <h2>History</h2>
        <TransactionTable transactions={history} />
      </section>
    </>
  );
}

export function AccountTable({ accounts }: { accounts: Account[] }) {
  return (
    <div className="table-wrap">
      <table>
        <thead><tr><th>Account</th><th>Owner</th><th>Type</th><th>Status</th><th className="num">Balance</th></tr></thead>
        <tbody>
          {accounts.map((account) => (
            <tr key={account.id}>
              <td><Link to={`/accounts/${account.id}`}>{account.accountNumber}</Link></td>
              <td>{account.customerName}<div className="note">{account.customerNumber}</div></td>
              <td>{account.accountType}</td>
              <td><Badge value={account.status} /></td>
              <td className="money">{money(account.balance)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {accounts.length === 0 ? <p className="empty">No accounts to show.</p> : null}
    </div>
  );
}

export function TransactionTable({ transactions }: { transactions: Transaction[] }) {
  return (
    <div className="table-wrap">
      <table>
        <thead><tr><th>Reference</th><th>Type</th><th>Accounts</th><th>Status</th><th className="num">Amount</th></tr></thead>
        <tbody>
          {transactions.map((item) => (
            <tr key={item.id}>
              <td>{item.referenceNumber}<div className="note">{when(item.createdAt)} UTC · {item.createdBy}</div></td>
              <td>{item.type}</td>
              <td>{item.sourceAccountNumber || "—"} → {item.destinationAccountNumber || "—"}</td>
              <td><Badge value={item.status} /></td>
              <td className="money">{money(item.amount)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {transactions.length === 0 ? <p className="empty">No transactions to show.</p> : null}
    </div>
  );
}
