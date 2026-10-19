import { parse } from "lossless-json";
import type { Book } from "./types";
import { integer } from "./money";
// Numeric JSON tokens become strings before JavaScript can round any bigint.
export const decode = (text: string): unknown => parse(text, undefined, (value) => value);
export function validateBook(value: unknown): asserts value is Book {
  if (!value || typeof value !== "object") throw new Error("Invalid database snapshot.");
  const book = value as Book;
  for (const key of [
    "flats",
    "tenants",
    "tenancies",
    "rent_periods",
    "payments",
    "payment_allocations",
    "expenses",
    "maintenance",
    "deposit_transactions",
    "recurring_expenses",
    "tenant_documents",
    "audit_logs",
  ] as const) {
    if (!Array.isArray(book[key])) throw new Error("Incomplete database snapshot. Retry loading the records.");
  }
  if (!book.building || typeof book.building.name !== "string") throw new Error("Building details are missing.");
  if (book.flats.length) {
    const expected = new Set(Array.from({ length: 7 }, (_, i) => [`${i + 1}A`, `${i + 1}B`]).flat());
    if (
      book.flats.length !== 14 ||
      new Set(book.flats.map((f) => f.id)).size !== 14 ||
      new Set(book.flats.map((f) => f.flat_number)).size !== 14 ||
      book.flats.some(
        (f) => !expected.has(f.flat_number) || f.flat_number[0] !== f.floor || integer(f.default_rent_paisa) < 0n,
      )
    )
      throw new Error("Cloud flat data does not match the fixed 14-flat building.");
  }
  for (const rows of [
    book.rent_periods,
    book.payments,
    book.expenses,
    book.maintenance,
    book.deposit_transactions,
    book.recurring_expenses,
    book.payment_allocations,
  ]) {
    for (const row of rows) integer(row.amount_paisa);
  }
}
