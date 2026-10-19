import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";
import { money, when } from "../format";
import type { Dashboard } from "../types";
import { Badge, ErrorText } from "../ui";

export function DashboardPage() {
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    api<Dashboard>("/api/dashboard").then(setData).catch(setError);
  }, []);

  return (
    <>
      <div>
        <p className="eyebrow">Today</p>
        <h1>Operations overview</h1>
      </div>
      <ErrorText error={error} />
      {data ? (
        <>
          <section className="stats">
            <article className="card stat"><span>Customers</span><strong>{data.customerCount}</strong></article>
            <article className="card stat"><span>Active accounts</span><strong>{data.activeAccounts}</strong></article>
            <article className="card stat"><span>Simulated balances</span><strong>{money(data.simulatedBalances)}</strong></article>
            <article className="card stat"><span>Waiting for approval</span><strong>{data.pendingApprovals}</strong></article>
          </section>
          <p className="note">Withdrawals and transfers at or above {money(data.approvalThreshold)} wait for a different employee to approve them. Frozen accounts: {data.frozenAccounts}.</p>
          <section className="split">
            <article className="card">
              <h2>Recent activity</h2>
              {data.recentTransactions.length === 0 ? <p className="empty">No transactions yet.</p> : (
                <div className="table-wrap">
                  <table>
                    <tbody>
                      {data.recentTransactions.map((item) => (
                        <tr key={item.id}>
                          <td><Link to="/transactions">{item.referenceNumber}</Link><div className="note">{when(item.createdAt)} UTC</div></td>
                          <td><Badge value={item.status} /></td>
                          <td className="money">{money(item.amount)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </article>
            <article className="card">
              <h2>Audit trail</h2>
              {data.recentAudit.length === 0 ? <p className="empty">Your role does not include audit review, or nothing has been recorded yet.</p> : (
                <div className="table-wrap">
                  <table>
                    <tbody>
                      {data.recentAudit.map((item) => (
                        <tr key={item.id}>
                          <td>{item.actorUsername}<div className="note">{when(item.createdAt)} UTC</div></td>
                          <td>{item.action}</td>
                          <td><Badge value={item.success ? "COMPLETED" : "REJECTED"} /></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </article>
          </section>
        </>
      ) : null}
    </>
  );
}
