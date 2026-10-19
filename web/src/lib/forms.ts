import type { Book, Row } from "./types";
import { parseMoney, rawMoney, today } from "./money";
export type Field = {
  key: string;
  label: string;
  type?: "date" | "money" | "textarea" | "select" | "number" | "checkbox" | "tel";
  required?: boolean;
  options?: string[];
};
const text = (key: string, label: string, required = false): Field => ({ key, label, required });
const amount = (key = "amount_paisa", label = "Amount (৳)"): Field => ({ key, label, type: "money", required: true });
const date = (key: string, label: string, required = true): Field => ({ key, label, type: "date", required });
const choice = (key: string, label: string, options: string[]): Field => ({
  key,
  label,
  type: "select",
  options,
  required: true,
});
const notes: Field = { key: "notes", label: "Notes", type: "textarea" };
const scope: Field = { key: "flat_id", label: "Flat / building", type: "select" };
const methods = ["Cash", "bKash", "Nagad", "Bank Transfer", "Other"];
export const categories = [
  "Electricity",
  "Water",
  "Cleaning",
  "Security",
  "Plumbing",
  "Electrical",
  "Lift",
  "Generator",
  "Painting",
  "Repair",
  "Internet / CCTV",
  "Maintenance",
  "Other",
];
const expenseFields = [
  amount(),
  date("expense_date", "Expense date"),
  choice("category", "Category", categories),
  text("description", "Description", true),
  scope,
  text("vendor", "Vendor"),
  choice("payment_method", "Payment method", methods),
  notes,
];
export const forms: Record<string, { title: string; fields: Field[] }> = {
  tenancy_save: {
    title: "Tenant & tenancy",
    fields: [
      text("full_name", "Full name", true),
      text("phone", "Phone"),
      scope,
      amount("monthly_rent_paisa", "Monthly rent (৳)"),
      date("move_in_date", "Move-in date"),
      date("ledger_start_date", "Ledger starts"),
      date("agreement_start_date", "Agreement starts", false),
      date("agreement_end_date", "Agreement ends", false),
      amount("opening_due_paisa", "Opening due (৳)"),
      amount("opening_credit_paisa", "Opening credit (৳)"),
      text("emergency_phone", "Emergency phone"),
      text("occupation", "Occupation"),
      { key: "permanent_address", label: "Permanent address", type: "textarea" },
      notes,
    ],
  },
  payment_save: {
    title: "Payment",
    fields: [
      { key: "tenancy_id", label: "Tenant / tenancy", type: "select", required: true },
      amount(),
      date("payment_date", "Payment date"),
      date("through_month", "Apply to dues through"),
      choice("kind", "Entry type", ["cash", "opening_credit", "credit_refund"]),
      choice("payment_method", "Payment method", methods),
      text("reference", "Reference"),
      notes,
    ],
  },
  rent_charge: {
    title: "Rent charge",
    fields: [
      { key: "tenancy_id", label: "Tenant / tenancy", type: "select", required: true },
      amount(),
      date("period_date", "Charge date"),
      choice("kind", "Charge type", ["manual", "monthly", "opening_due", "settlement"]),
      notes,
    ],
  },
  deposit_save: {
    title: "Deposit transaction",
    fields: [
      { key: "tenancy_id", label: "Tenant / tenancy", type: "select", required: true },
      choice("type", "Transaction type", ["received", "deduction", "refund", "adjustment"]),
      amount(),
      date("transaction_date", "Transaction date"),
      text("description", "Description"),
    ],
  },
  move_out: {
    title: "Move-out settlement",
    fields: [
      date("move_out_date", "Move-out date"),
      amount("damage_paisa", "Damage deduction (৳)"),
      amount("adjustment_paisa", "Other adjustment (৳, may be negative)"),
      notes,
    ],
  },
  expense_save: { title: "Expense", fields: expenseFields },
  maintenance_save: {
    title: "Maintenance",
    fields: [
      text("title", "Title", true),
      date("maintenance_date", "Maintenance date"),
      amount(),
      scope,
      text("description", "Description"),
      text("vendor", "Vendor"),
      date("reminder_date", "Reminder date", false),
      notes,
    ],
  },
  recurring_save: {
    title: "Recurring expense",
    fields: [
      text("description", "Description", true),
      amount(),
      choice("category", "Category", categories),
      scope,
      date("next_date", "Next due date"),
      choice("frequency", "Frequency", ["monthly", "yearly", "weekly", "custom"]),
      { key: "interval_days", label: "Interval days (custom schedule)", type: "number", required: true },
      { key: "active", label: "Schedule active", type: "checkbox" },
    ],
  },
  building_save: {
    title: "Building details",
    fields: [
      text("name", "Building name", true),
      text("address", "Address"),
      text("owner_name", "Owner name"),
      text("owner_phone", "Owner phone"),
    ],
  },
  payment_void: { title: "Void payment", fields: [] },
  expense_void: { title: "Void expense", fields: [] },
  maintenance_void: { title: "Void maintenance", fields: [] },
  document_delete: { title: "Delete document", fields: [] },
};
export function initialValues(action: string, book: Book, row: Row = {} as Row): Record<string, string | boolean> {
  const source = action === "tenancy_save" ? { ...book.tenants.find((t) => t.id === row.tenant_id), ...row } : row;
  const initial: Record<string, string | boolean> = {};
  for (const f of forms[action].fields) {
    const value = source[f.key];
    initial[f.key] =
      f.type === "checkbox"
        ? ((value ?? true) as boolean)
        : f.type === "money"
          ? rawMoney(value ?? "0")
          : String(value ?? (f.type === "date" && f.required ? today() : (f.options?.[0] ?? "")));
  }
  if (action === "recurring_save") initial.interval_days = String(row.interval_days ?? "30");
  return initial;
}
export function payload(action: string, values: Record<string, string | boolean>, meta: Record<string, unknown>) {
  const result: Record<string, unknown> = { ...meta };
  for (const field of forms[action].fields) {
    const value = values[field.key];
    result[field.key] =
      field.type === "money"
        ? parseMoney(
            String(value),
            field.key === "adjustment_paisa" || (action === "deposit_save" && values.type === "adjustment"),
          )
        : typeof value === "boolean"
          ? value
          : value?.trim() || (action === "building_save" ? "" : null);
  }
  return result;
}
