import { FormEvent, useEffect, useState } from "react";
import { api } from "../api";
import { useSession } from "../auth";
import { money } from "../format";
import type { Account, Page, Settings, Transaction } from "../types";
import { ErrorText, Field } from "../ui";
import { TransactionTable } from "./AccountsPage";

export function TransactionsPage() {
  const { session } = useSession();
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [history, setHistory] = useState<Transaction[]>([]);
  const [settings, setSettings] = useState<Settings | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [notice, setNotice] = useState("");
  const [form, setForm] = useState({
    kind: "deposit",
    accountId: "",
    sourceAccountId: "",
    destinationAccountId: "",
    amount: "",
    description: "",
    idempotencyKey: ""
  });
  const permissions = session?.user.permissions ?? [];
  const allowed = [
    permissions.includes("TRANSACTION_DEPOSIT") ? "deposit" : "",
    permissions.includes("TRANSACTION_WITHDRAW") ? "withdraw" : "",
    permissions.includes("TRANSACTION_TRANSFER") ? "transfer" : ""
  ].filter(Boolean);

  function load() {
    if (permissions.includes("ACCOUNT_READ")) {
      api<Page<Account>>("/api/accounts?size=100").then((result) => setAccounts(result.content)).catch(setError);
    }
    api<Page<Transaction>>("/api/transactions?size=20").then((result) => setHistory(result.content)).catch(setError);
    api<Settings>("/api/settings").then(setSettings).catch(setError);
  }

  useEffect(load, []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setNotice("");
    const headers: Record<string, string> = {};
    if (form.idempotencyKey) {
      headers["Idempotency-Key"] = form.idempotencyKey;
    }
    const amount = Number(form.amount);
    const body = form.kind === "transfer"
      ? { sourceAccountId: form.sourceAccountId, destinationAccountId: form.destinationAccountId, amount, description: form.description }
      : { accountId: form.accountId, amount, description: form.description };
    try {
      const saved = await api<Transaction>(`/api/transactions/${form.kind}`, {
        method: "POST",
        headers,
        body: JSON.stringify(body)
      });
      setNotice(saved.status === "PENDING_APPROVAL"
        ? `${saved.referenceNumber} is waiting for approval. No balance moved yet.`
        : `${saved.referenceNumber} completed.`);
      setForm({ ...form, amount: "", description: "", idempotencyKey: "" });
      load();
    } catch (caught) {
      setError(caught);
    }
  }

  return (
    <>
      <div>
        <p className="eyebrow">Movements</p>
        <h1>Transactions</h1>
      </div>
      {settings ? <p className="note">Amounts at or above {money(settings.approvalThreshold)} for a withdrawal or transfer are submitted for approval. Send the same Idempotency-Key to safely retry a request.</p> : null}
      <ErrorText error={error} />
      {notice ? <div className="banner">{notice}</div> : null}
      {allowed.length > 0 ? (
        <form className="card stack" onSubmit={(event) => void submit(event)}>
          <Field label="Operation">
            <select value={form.kind} onChange={(event) => setForm({ ...form, kind: event.target.value })}>
              {allowed.map((kind) => <option key={kind} value={kind}>{kind}</option>)}
            </select>
          </Field>
          {form.kind === "transfer" ? (
            <div className="split">
              <AccountSelect label="From" accounts={accounts} value={form.sourceAccountId} onChange={(value) => setForm({ ...form, sourceAccountId: value })} />
              <AccountSelect label="To" accounts={accounts} value={form.destinationAccountId} onChange={(value) => setForm({ ...form, destinationAccountId: value })} />
            </div>
          ) : (
            <AccountSelect label="Account" accounts={accounts} value={form.accountId} onChange={(value) => setForm({ ...form, accountId: value })} />
          )}
          <Field label="Amount"><input value={form.amount} onChange={(event) => setForm({ ...form, amount: event.target.value })} inputMode="decimal" required /></Field>
          <Field label="Description"><input value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} /></Field>
          <Field label="Idempotency key (optional)">
            <input value={form.idempotencyKey} onChange={(event) => setForm({ ...form, idempotencyKey: event.target.value })} placeholder="At least 8 characters" />
          </Field>
          <button className="btn primary" type="submit">Submit simulated operation</button>
        </form>
      ) : <p className="note">This role can review transactions and cannot create them.</p>}
      <section className="card">
        <h2>History</h2>
        <TransactionTable transactions={history} />
      </section>
    </>
  );
}

function AccountSelect({ label, accounts, value, onChange }: { label: string; accounts: Account[]; value: string; onChange: (value: string) => void }) {
  return (
    <Field label={label}>
      <select value={value} onChange={(event) => onChange(event.target.value)} required>
        <option value="">Select</option>
        {accounts.map((account) => (
          <option key={account.id} value={account.id}>
            {account.accountNumber} · {account.customerName} · {account.status} · {money(account.balance)}
          </option>
        ))}
      </select>
    </Field>
  );
}

export function ApprovalsPage() {
  const [rows, setRows] = useState<Transaction[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [comment, setComment] = useState("");

  function load() {
    api<Page<Transaction>>("/api/transactions?status=PENDING_APPROVAL&size=50").then((result) => setRows(result.content)).catch(setError);
  }

  useEffect(load, []);

  async function decide(id: string, action: "approve" | "reject") {
    setError(null);
    try {
      await api(`/api/transactions/${id}/${action}`, { method: "POST", body: JSON.stringify({ comment }) });
      setComment("");
      load();
    } catch (caught) {
      setError(caught);
    }
  }

  return (
    <>
      <div>
        <p className="eyebrow">Control</p>
        <h1>Approvals</h1>
      </div>
      <p className="note">Pending operations have not moved simulated funds. Approval checks the balance again, and you cannot approve your own request.</p>
      <ErrorText error={error} />
      <Field label="Decision note">
        <input value={comment} onChange={(event) => setComment(event.target.value)} />
      </Field>
      <section className="card">
        {rows.map((item) => (
          <article key={item.id} className="toolbar">
            <div>
              <strong>{item.referenceNumber}</strong>
              <div className="note">{item.type} · {money(item.amount)} · requested by {item.createdBy}</div>
              <div className="note">{item.sourceAccountNumber || "—"} → {item.destinationAccountNumber || "—"}</div>
            </div>
            <div className="actions">
              <button className="btn primary" type="button" onClick={() => void decide(item.id, "approve")}>Approve</button>
              <button className="btn-danger" type="button" onClick={() => void decide(item.id, "reject")}>Reject</button>
            </div>
          </article>
        ))}
        {rows.length === 0 ? <p className="empty">Nothing is waiting for approval.</p> : null}
      </section>
    </>
  );
}
