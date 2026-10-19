import type { Row } from "./types";
export const deletableTypes = [
  "tenants",
  "tenancies",
  "payments",
  "rent_periods",
  "deposit_transactions",
  "expenses",
  "recurring_expenses",
  "maintenance",
  "tenant_documents",
] as const;
export type Deletion = { entity: string; row: Row; label: string };
export type DeletePreview = { token: string; counts: Record<string, string>; paths: string[] };
export const recordLabels: Record<string, string> = {
  tenants: "Tenants",
  tenancies: "Tenancies",
  payments: "Payments",
  rent_periods: "Rent charges",
  deposit_transactions: "Deposit transactions",
  expenses: "Expenses",
  recurring_expenses: "Recurring schedules",
  maintenance: "Maintenance",
  tenant_documents: "Documents",
  payment_allocations: "Rent allocations",
};
