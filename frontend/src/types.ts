export type UserProfile = {
  id: string;
  username: string;
  fullName: string;
  email: string;
  roles: string[];
  permissions: string[];
};

export type Session = {
  accessToken: string;
  refreshToken: string;
  user: UserProfile;
};

export type Page<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type Customer = {
  id: string;
  customerNumber: string;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  dateOfBirth: string;
  addressLine: string;
  city: string;
  country: string;
  status: "ACTIVE" | "INACTIVE";
  registeredAt: string;
};

export type Account = {
  id: string;
  accountNumber: string;
  customerId: string;
  customerNumber: string;
  customerName: string;
  accountType: "SAVINGS" | "CHECKING";
  balance: number;
  currency: string;
  status: "ACTIVE" | "FROZEN" | "CLOSED";
  openedAt: string;
  closedAt?: string;
};

export type Transaction = {
  id: string;
  referenceNumber: string;
  type: "DEPOSIT" | "WITHDRAWAL" | "TRANSFER";
  status: "COMPLETED" | "PENDING_APPROVAL" | "REJECTED";
  amount: number;
  currency: string;
  sourceAccountId?: string;
  sourceAccountNumber?: string;
  destinationAccountId?: string;
  destinationAccountNumber?: string;
  description?: string;
  createdBy?: string;
  createdAt: string;
  decidedAt?: string;
  approvalStatus?: string;
  approvalComment?: string;
};

export type AuditLog = {
  id: string;
  actorUsername: string;
  action: string;
  entityType: string;
  entityId?: string;
  success: boolean;
  message: string;
  ipAddress?: string;
  createdAt: string;
};

export type StaffUser = {
  id: string;
  username: string;
  fullName: string;
  email: string;
  active: boolean;
  roles: string[];
  createdAt: string;
};

export type Role = {
  name: string;
  description: string;
};

export type Settings = {
  bankDisplayName: string;
  approvalThreshold: number;
  updatedAt: string;
};

export type Dashboard = {
  customerCount: number;
  activeAccounts: number;
  frozenAccounts: number;
  simulatedBalances: number;
  pendingApprovals: number;
  approvalThreshold: number;
  recentTransactions: Transaction[];
  recentAudit: AuditLog[];
};

export type FieldError = {
  field: string;
  message: string;
};
