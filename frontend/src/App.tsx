import { Navigate, Route, Routes } from "react-router-dom";
import { AccountDetailPage, AccountsPage } from "./pages/AccountsPage";
import { AuditPage, SettingsPage, StaffPage } from "./pages/AdminPages";
import { CustomerDetailPage, CustomerFormPage, CustomersPage } from "./pages/CustomersPage";
import { DashboardPage } from "./pages/DashboardPage";
import { LoginPage } from "./pages/LoginPage";
import { ApprovalsPage, TransactionsPage } from "./pages/TransactionsPage";
import { RequireAuth, Shell, can } from "./ui";

const CustomersGuard = can("CUSTOMER_READ");
const AccountsGuard = can("ACCOUNT_READ");
const TransactionsGuard = can("TRANSACTION_READ");
const ApprovalsGuard = can("TRANSACTION_APPROVE");
const AuditGuard = can("AUDIT_READ");
const StaffGuard = can("USER_READ");
const SettingsGuard = can("SETTINGS_MANAGE");

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<Shell />}>
          <Route index element={<DashboardPage />} />
          <Route element={<CustomersGuard />}>
            <Route path="customers" element={<CustomersPage />} />
            <Route path="customers/new" element={<CustomerFormPage />} />
            <Route path="customers/:id" element={<CustomerDetailPage />} />
            <Route path="customers/:id/edit" element={<CustomerFormPage />} />
          </Route>
          <Route element={<AccountsGuard />}>
            <Route path="accounts" element={<AccountsPage />} />
            <Route path="accounts/:id" element={<AccountDetailPage />} />
          </Route>
          <Route element={<TransactionsGuard />}>
            <Route path="transactions" element={<TransactionsPage />} />
          </Route>
          <Route element={<ApprovalsGuard />}>
            <Route path="approvals" element={<ApprovalsPage />} />
          </Route>
          <Route element={<AuditGuard />}>
            <Route path="audit" element={<AuditPage />} />
          </Route>
          <Route element={<StaffGuard />}>
            <Route path="staff" element={<StaffPage />} />
          </Route>
          <Route element={<SettingsGuard />}>
            <Route path="settings" element={<SettingsPage />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
