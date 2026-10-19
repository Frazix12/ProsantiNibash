import { deletableTypes } from "./deletion";
import { forms } from "./forms";
import { validDate, integer as n, today } from "./money";
import type { Command } from "./types";
export const uuid = (value: unknown): value is string =>
  typeof value === "string" && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
export const safePath = (value: unknown): value is string =>
  typeof value === "string" &&
  /^(tenants|payments|expenses|maintenance)\/[0-9a-f-]{36}\/[A-Za-z0-9._-]+$/.test(value) &&
  uuid(value.split("/")[1]);
export function validateLocal(request: Request, mutation = false) {
  const host = request.headers.get("host") || "";
  if (!/^(127\.0\.0\.1|localhost):\d+$/.test(host)) throw new Error("Only the local address is allowed.");
  if (request.headers.get("sec-fetch-site") === "cross-site") throw new Error("Cross-site requests are not allowed.");
  if (mutation && (request.headers.get("origin") !== `http://${host}` || request.headers.get("x-pn-local") !== "1"))
    throw new Error("Open the app at its local address before saving.");
}
function validateValue(key: string, value: unknown) {
  if (value === null || value === undefined) return;
  if (typeof value === "string" && value.length > 10000)
    throw new Error("A field is too long (maximum 10,000 characters).");
  if (key === "id" || key.endsWith("_id")) {
    if (!uuid(value)) throw new Error(`Invalid ${key.replaceAll("_", " ")}.`);
  }
  if (key.endsWith("_date") || ["through_month", "next_date", "scheduled_date"].includes(key)) {
    if (typeof value !== "string" || !validDate(value)) throw new Error("Choose a valid date.");
  }
  if (key.endsWith("_paisa") || key.includes("revision") || ["size_bytes", "interval_days"].includes(key)) {
    const amount = n(value);
    if (amount < -9223372036854775808n || amount > 9223372036854775807n) throw new Error("Integer is out of range.");
    if (key.includes("revision") && amount < 1n) throw new Error("Reload the record before editing.");
  }
  if (["storage_path", "receipt_path"].includes(key) && value !== "" && !safePath(value))
    throw new Error("Invalid file path.");
  if (!["string", "boolean"].includes(typeof value)) throw new Error("Unsupported field value.");
}
function validateAmounts(action: string, data: Record<string, unknown>) {
  for (const [key, value] of Object.entries(data)) {
    const signed = key === "adjustment_paisa" || (action === "deposit_save" && data.type === "adjustment");
    if (key.endsWith("_paisa") && !signed && n(value) < 0n) throw new Error("Amounts must not be negative.");
  }
  if (
    ["payment_save", "expense_save", "recurring_save", "deposit_save"].includes(action) &&
    n(data.amount_paisa) === 0n
  )
    throw new Error("Enter a nonzero amount.");
  if (action === "rent_charge" && data.kind !== "monthly" && n(data.amount_paisa) <= 0n)
    throw new Error("Enter a positive charge amount.");
  if (action === "tenancy_save") {
    if (n(data.monthly_rent_paisa) <= 0n) throw new Error("Monthly rent must be positive.");
    if (n(data.opening_due_paisa) > 0n && n(data.opening_credit_paisa) > 0n)
      throw new Error("Choose opening due or credit, not both.");
    if (String(data.ledger_start_date) < String(data.move_in_date))
      throw new Error("Ledger start cannot precede move-in.");
    if (!uuid(data.flat_id)) throw new Error("Choose a flat.");
    if (
      data.agreement_end_date &&
      data.agreement_start_date &&
      String(data.agreement_end_date) < String(data.agreement_start_date)
    )
      throw new Error("Agreement end must follow its start.");
  }
  if (action === "recurring_save" && (n(data.interval_days) < 1n || n(data.interval_days) > 3660n))
    throw new Error("Interval must be 1–3660 days.");
  if (action === "move_out" && String(data.move_out_date) > today())
    throw new Error("Move-out cannot be in the future.");
}
const fileFields: Record<string, string[]> = {
  document_save: ["tenant_id", "document_type", "storage_path", "file_name", "mime_type", "size_bytes"],
  attachment_save: ["entity_type", "storage_path"],
};
export function validateCommand(input: unknown): asserts input is Command {
  if (!input || typeof input !== "object" || Array.isArray(input)) throw new Error("Invalid command.");
  const { action, request_id, data } = input as Command;
  if (action === "permanent_delete") {
    if (
      !uuid(request_id) ||
      !data ||
      typeof data !== "object" ||
      Array.isArray(data) ||
      !uuid(data.id) ||
      !deletableTypes.includes(data.entity_type as (typeof deletableTypes)[number]) ||
      typeof data.token !== "string" ||
      !/^[a-f0-9]{32}$/.test(data.token) ||
      data.confirmation !== "DELETE" ||
      Object.keys(data).some((k) => !["entity_type", "id", "token", "confirmation"].includes(k))
    )
      throw new Error("Review and confirm permanent deletion first.");
    return;
  }
  if (!uuid(request_id) || typeof action !== "string" || !(action in forms || action in fileFields))
    throw new Error("Invalid action or request ID.");
  if (!data || typeof data !== "object" || Array.isArray(data)) throw new Error("Invalid form data.");
  const fields = forms[action]?.fields ?? [];
  const allowed = new Set(["id", "revision", ...fields.map((f) => f.key), ...(fileFields[action] ?? [])]);
  if (action === "maintenance_save") allowed.add("receipt_path");
  if (action === "tenancy_save") ["tenant_revision", "tenant_id"].forEach((k) => allowed.add(k));
  if (action === "expense_save")
    ["recurring_expense_id", "recurring_revision", "scheduled_date"].forEach((k) => allowed.add(k));
  for (const [key, value] of Object.entries(data)) {
    if (!allowed.has(key)) throw new Error("Unexpected form field.");
    validateValue(key, value);
  }
  for (const f of fields) {
    if (f.required && (data[f.key] === null || data[f.key] === undefined || data[f.key] === ""))
      throw new Error(`${f.label} is required.`);
    if (f.options && !f.options.includes(String(data[f.key])))
      throw new Error(`Choose a valid ${f.label.toLowerCase()}.`);
    if (f.type === "checkbox" && typeof data[f.key] !== "boolean") throw new Error("Invalid schedule status.");
  }
  if (data.id && !data.revision) throw new Error("Reload the record before editing.");
  if (
    ["move_out", "payment_void", "expense_void", "maintenance_void", "document_delete", "attachment_save"].includes(
      action,
    ) &&
    !data.id
  )
    throw new Error("Choose a record first.");
  if (
    action === "document_save" &&
    (!uuid(data.tenant_id) ||
      !safePath(data.storage_path) ||
      !data.document_type ||
      !data.file_name ||
      !Object.keys(fileTypes).includes(String(data.mime_type)) ||
      n(data.size_bytes) > 20971520n ||
      n(data.size_bytes) < 1n)
  )
    throw new Error("Invalid document metadata.");
  if (action === "attachment_save" && !["payments", "expenses", "maintenance"].includes(String(data.entity_type)))
    throw new Error("Invalid attachment type.");
  if (forms[action]) validateAmounts(action, data);
}
export const fileTypes: Record<string, string> = {
  "application/pdf": "pdf",
  "image/jpeg": "jpg",
  "image/png": "png",
  "image/webp": "webp",
  "text/plain": "txt",
};
