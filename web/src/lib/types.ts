// PostgreSQL integers travel as decimal strings to preserve all 64 bits in browsers.
export type Row = { id: string; revision?: string; [key: string]: unknown };
export interface Flat extends Row {
  floor: string;
  flat_number: string;
  default_rent_paisa: string;
}
export interface Tenant extends Row {
  full_name: string;
  phone?: string;
  profile_image_path?: string;
}
export interface Tenancy extends Row {
  tenant_id: string;
  flat_id: string;
  monthly_rent_paisa: string;
  status: string;
  move_in_date: string;
  ledger_start_date: string;
  move_out_date?: string;
  agreement_end_date?: string;
}
export interface Charge extends Row {
  tenancy_id: string;
  period_date: string;
  amount_paisa: string;
  kind: string;
}
export interface Payment extends Row {
  tenancy_id: string;
  amount_paisa: string;
  payment_date: string;
  through_month: string;
  kind: string;
  voided: boolean;
  receipt_number: string;
  receipt_snapshot: Record<string, unknown>;
  attachment_path?: string;
}
export interface Allocation {
  payment_id: string;
  rent_period_id: string;
  amount_paisa: string;
}
export interface Expense extends Row {
  amount_paisa: string;
  expense_date: string;
  category: string;
  description: string;
  flat_id?: string;
  voided: boolean;
  receipt_path?: string;
}
export interface Maintenance extends Row {
  title: string;
  maintenance_date: string;
  amount_paisa: string;
  flat_id?: string;
  expense_id?: string;
  reminder_date?: string;
  voided: boolean;
  receipt_path?: string;
}
export interface Deposit extends Row {
  tenancy_id: string;
  type: string;
  amount_paisa: string;
  transaction_date: string;
  description?: string;
}
export interface Recurring extends Row {
  description: string;
  category: string;
  amount_paisa: string;
  next_date: string;
  frequency: string;
  active: boolean;
  flat_id?: string;
}
export interface Document extends Row {
  tenant_id: string;
  document_type: string;
  storage_path: string;
  file_name: string;
  mime_type: string;
  size_bytes: string;
  created_at: string;
}
export interface Audit extends Row {
  action: string;
  entity_type: string;
  entity_id: string;
  created_at: string;
  old_data?: unknown;
  new_data?: unknown;
}
export interface Book {
  flats: Flat[];
  tenants: Tenant[];
  tenancies: Tenancy[];
  rent_periods: Charge[];
  payments: Payment[];
  payment_allocations: Allocation[];
  expenses: Expense[];
  maintenance: Maintenance[];
  deposit_transactions: Deposit[];
  recurring_expenses: Recurring[];
  tenant_documents: Document[];
  audit_logs: Audit[];
  building: Row & { name: string; address: string; owner_name: string; owner_phone: string };
}
export interface Command {
  action: string;
  request_id: string;
  data: Record<string, unknown>;
}
export type Section =
  | "overview"
  | "flats"
  | "tenants"
  | "payments"
  | "expenses"
  | "recurring"
  | "maintenance"
  | "documents"
  | "reports"
  | "audit"
  | "reminders"
  | "building";
