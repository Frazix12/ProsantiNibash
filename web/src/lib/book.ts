import type { Book, Flat, Tenancy } from "./types";
import { integer as n, sum, today, previousMonth } from "./money";
export const tenant = (book: Book, tenancy: Tenancy) => book.tenants.find((t) => t.id === tenancy.tenant_id);
export const flat = (book: Book, tenancy: Tenancy) => book.flats.find((f) => f.id === tenancy.flat_id);
export const tenancyLabel = (book: Book, id: string) => {
  const t = book.tenancies.find((t) => t.id === id);
  return t
    ? `${flat(book, t)?.flat_number ?? "—"} · ${tenant(book, t)?.full_name ?? "Unknown tenant"}`
    : "Unknown tenancy";
};
export const paid = (book: Book, id: string) =>
  sum(
    book.payment_allocations.filter((a) => a.rent_period_id === id),
    (a) => n(a.amount_paisa),
  );
export const due = (book: Book, id: string, month?: string) =>
  sum(
    book.rent_periods.filter((r) => r.tenancy_id === id && (!month || r.period_date.slice(0, 7) <= month)),
    (r) => n(r.amount_paisa) - paid(book, r.id),
  );
export function credit(book: Book, id: string): bigint {
  const payments = book.payments.filter((p) => p.tenancy_id === id && !p.voided);
  const ids = new Set(payments.map((p) => p.id));
  return (
    sum(payments, (p) => (p.kind === "credit_refund" ? -1n : 1n) * n(p.amount_paisa)) -
    sum(
      book.payment_allocations.filter((a) => ids.has(a.payment_id)),
      (a) => n(a.amount_paisa),
    )
  );
}
export const deposit = (book: Book, id: string) =>
  sum(
    book.deposit_transactions.filter((d) => d.tenancy_id === id),
    (d) => (["deduction", "refund"].includes(d.type) ? -1n : 1n) * n(d.amount_paisa),
  );
export function summary(book: Book, month: string) {
  const periods = book.rent_periods.filter((r) => r.period_date.startsWith(month) && r.kind !== "opening_due");
  const expected = sum(periods, (r) => n(r.amount_paisa));
  const allocated = sum(periods, (r) => paid(book, r.id));
  const collected = sum(
    book.payments.filter((p) => !p.voided && p.payment_date.startsWith(month) && p.kind !== "opening_credit"),
    (p) => (p.kind === "credit_refund" ? -1n : 1n) * n(p.amount_paisa),
  );
  const expenses = sum(
    book.expenses.filter((e) => !e.voided && e.expense_date.startsWith(month)),
    (e) => n(e.amount_paisa),
  );
  const old = sum(
    book.rent_periods.filter(
      (r) => r.period_date.slice(0, 7) < month || (r.period_date.startsWith(month) && r.kind === "opening_due"),
    ),
    (r) => n(r.amount_paisa) - paid(book, r.id),
  );
  return {
    expected,
    allocated,
    collected,
    expenses,
    currentDue: expected - allocated,
    oldDue: old,
    net: collected - expenses,
  };
}
export function flatStatus(book: Book, unit: Flat, month: string): string {
  const t = book.tenancies.find((t) => t.flat_id === unit.id && t.status === "active");
  if (!t) return "Vacant";
  const rows = book.rent_periods.filter(
    (r) => r.tenancy_id === t.id && r.period_date.startsWith(month) && r.kind !== "opening_due",
  );
  const charged = sum(rows, (r) => n(r.amount_paisa)),
    allocated = sum(rows, (r) => paid(book, r.id));
  if (due(book, t.id, previousMonth(month)) > 0n || (charged > allocated && today() >= `${month}-06`)) return "Overdue";
  return charged === 0n ? "No charge" : allocated >= charged ? "Paid" : allocated > 0n ? "Partial" : "Due";
}
export function settlement(book: Book, id: string, date: string, damage: string, adjustment: string) {
  const removed = book.rent_periods.filter(
    (r) => r.tenancy_id === id && r.kind === "monthly" && r.period_date.slice(0, 7) > date.slice(0, 7),
  );
  const debt = due(book, id) - sum(removed, (r) => n(r.amount_paisa) - paid(book, r.id));
  const available = credit(book, id) + sum(removed, (r) => paid(book, r.id));
  const held = deposit(book, id);
  return { due: debt, credit: available, deposit: held, net: debt + n(damage) + n(adjustment) - available - held };
}
export function csvCell(value: string): string {
  const safe = /^[=+\-@]/.test(value.trimStart()) ? `'${value}` : value;
  return `"${safe.replaceAll('"', '""')}"`;
}
export function csv(book: Book): string {
  const rows = [["type", "id", "date", "flat", "tenant", "amount_paisa", "details", "voided"]];
  for (const row of book.payments)
    rows.push([
      "payment",
      row.id,
      row.payment_date,
      (book.tenancies.find((t) => t.id === row.tenancy_id)
        ? flat(
            book,
            book.tenancies.find((t) => t.id === row.tenancy_id)!,
          )?.flat_number
        : "") ?? "",
      (book.tenancies.find((t) => t.id === row.tenancy_id)
        ? tenant(
            book,
            book.tenancies.find((t) => t.id === row.tenancy_id)!,
          )?.full_name
        : "") ?? "",
      row.amount_paisa,
      `${row.kind}: ${row.receipt_number}`,
      String(row.voided),
    ]);
  for (const row of book.rent_periods)
    rows.push([
      "charge",
      row.id,
      row.period_date,
      (book.tenancies.find((t) => t.id === row.tenancy_id)
        ? flat(
            book,
            book.tenancies.find((t) => t.id === row.tenancy_id)!,
          )?.flat_number
        : "") ?? "",
      (book.tenancies.find((t) => t.id === row.tenancy_id)
        ? tenant(
            book,
            book.tenancies.find((t) => t.id === row.tenancy_id)!,
          )?.full_name
        : "") ?? "",
      row.amount_paisa,
      row.kind,
      "false",
    ]);
  for (const row of book.expenses)
    rows.push([
      "expense",
      row.id,
      row.expense_date,
      row.flat_id ?? "",
      "",
      row.amount_paisa,
      row.description,
      String(row.voided),
    ]);
  for (const row of book.deposit_transactions)
    rows.push([
      "deposit",
      row.id,
      row.transaction_date,
      (book.tenancies.find((t) => t.id === row.tenancy_id)
        ? flat(
            book,
            book.tenancies.find((t) => t.id === row.tenancy_id)!,
          )?.flat_number
        : "") ?? "",
      (book.tenancies.find((t) => t.id === row.tenancy_id)
        ? tenant(
            book,
            book.tenancies.find((t) => t.id === row.tenancy_id)!,
          )?.full_name
        : "") ?? "",
      row.amount_paisa,
      row.type,
      "false",
    ]);
  return rows.map((row) => row.map(csvCell).join(",")).join("\r\n");
}
