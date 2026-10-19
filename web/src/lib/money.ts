const MAX = 9223372036854775807n;
export function integer(value: unknown): bigint {
  if (typeof value !== "string" || !/^-?\d+$/.test(value)) throw new Error("Invalid integer amount.");
  return BigInt(value);
}
export function parseMoney(value: string, signed = false): string {
  const match = value.trim().match(signed ? /^(-?)(\d+)(?:\.(\d{1,2}))?$/ : /^()(\d+)(?:\.(\d{1,2}))?$/);
  if (!match) throw new Error("Enter a valid amount with at most two decimal places.");
  const amount = BigInt(match[2]) * 100n + BigInt((match[3] || "").padEnd(2, "0"));
  if (amount > MAX) throw new Error("Amount exceeds the supported range.");
  return ((match[1] ? -1n : 1n) * amount).toString();
}
export function rawMoney(value: unknown): string {
  const amount = integer(value ?? "0");
  const positive = amount < 0n ? -amount : amount;
  return `${amount < 0n ? "-" : ""}${positive / 100n}.${(positive % 100n).toString().padStart(2, "0")}`;
}
export function money(value: bigint | string): string {
  const raw = rawMoney(value.toString());
  const [whole, decimal] = raw.split(".");
  return `৳${whole.replace(/\B(?=(\d{3})+(?!\d))/g, ",")}.${decimal}`;
}
export const sum = <T>(rows: T[], amount: (row: T) => bigint) => rows.reduce((total, row) => total + amount(row), 0n);
export function today(): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Dhaka",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
}
export function validDate(value: string): boolean {
  return (
    /^\d{4}-\d{2}-\d{2}$/.test(value) &&
    Number(value.slice(0, 4)) >= 1 &&
    Number.isFinite(Date.parse(`${value}T00:00:00Z`)) &&
    new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value
  );
}
export function previousMonth(month: string): string {
  const date = new Date(`${month}-01T00:00:00Z`);
  date.setUTCMonth(date.getUTCMonth() - 1);
  return date.toISOString().slice(0, 7);
}
export function dateLabel(value: unknown): string {
  if (typeof value !== "string" || !value) return "—";
  return new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" }).format(
    new Date(value.length === 10 ? `${value}T00:00:00Z` : value),
  );
}
