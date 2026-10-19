import test from "node:test";
import assert from "node:assert/strict";
import { decode, validateBook } from "../src/lib/wire";
import { parseMoney, rawMoney, validDate, money, previousMonth } from "../src/lib/money";
import { credit, deposit, due, paid, settlement, summary, csvCell } from "../src/lib/book";
import { initialValues, payload } from "../src/lib/forms";
import { inspectFile, referencedPaths } from "../src/lib/files";
import { validateCommand, validateLocal, safePath } from "../src/lib/validation";
import { fixture, id } from "./fixtures";
test("64-bit money survives JSON, form input, and display without rounding", () => {
  assert.deepEqual(decode('{"amount_paisa":9223372036854775807,"revision":1}'), {
    amount_paisa: "9223372036854775807",
    revision: "1",
  });
  assert.equal(parseMoney("92233720368547758.07"), "9223372036854775807");
  assert.equal(rawMoney("9223372036854775807"), "92233720368547758.07");
  assert.equal(money("-12345"), "৳-123.45");
  for (const amount of ["1e2", "0.001", "NaN", "-2", "92233720368547758.08"]) assert.throws(() => parseMoney(amount));
  assert.equal(parseMoney("-12.5", true), "-1250");
});
test("dates reject impossible days and month arithmetic crosses years", () => {
  assert.equal(validDate("2026-02-30"), false);
  assert.equal(validDate("2026-13-01"), false);
  assert.equal(validDate("2024-02-29"), true);
  assert.equal(previousMonth("2026-01"), "2025-12");
});
test("snapshot rejects changed fixed building and missing financial arrays", () => {
  const book = fixture();
  validateBook(book);
  book.flats[13].flat_number = "8B";
  assert.throws(() => validateBook(book));
  const incomplete = { ...fixture(), payments: undefined };
  assert.throws(() => validateBook(incomplete));
});
test("totals distinguish rent cash, opening credit, voids, refunds, and deposits", () => {
  const book = fixture(),
    month = book.rent_periods[0].period_date.slice(0, 7),
    t = id(200);
  assert.equal(paid(book, id(300)), 1800000n);
  assert.equal(due(book, t), 0n);
  assert.equal(deposit(book, t), 3600000n);
  book.payments.push(
    { ...book.payments[0], id: id(410), amount_paisa: "10000", kind: "opening_credit" },
    { ...book.payments[0], id: id(411), amount_paisa: "2000", kind: "credit_refund" },
    { ...book.payments[0], id: id(412), amount_paisa: "99999", voided: true },
  );
  assert.equal(credit(book, t), 8000n);
  assert.equal(summary(book, month).collected, 4498000n);
  assert.equal(summary(book, month).net, 4048000n);
});
test("backdated move-out removes generated future charges and releases allocations", () => {
  const book = fixture(),
    t = id(200),
    date = book.rent_periods[0].period_date;
  book.rent_periods.push({
    id: id(350),
    tenancy_id: t,
    period_date: "2028-01-01",
    kind: "monthly",
    amount_paisa: "500",
    revision: "1",
  });
  book.payments.push({ ...book.payments[0], id: id(450), amount_paisa: "200" });
  book.payment_allocations.push({ payment_id: id(450), rent_period_id: id(350), amount_paisa: "200" });
  const result = settlement(book, t, date, "1000", "-100");
  assert.equal(result.due, 0n);
  assert.equal(result.credit, 200n);
  assert.equal(result.net, -3599300n);
});
test("CSV quotes cells and neutralizes formulas including leading whitespace", () => {
  assert.equal(csvCell('A "quoted", name'), '"A ""quoted"", name"');
  assert.equal(csvCell(" \t=1+1"), '"\' \t=1+1"');
  assert.equal(csvCell("+cmd"), '"\'+cmd"');
});
test("local APIs reject cross-site origins and DNS rebinding hosts", () => {
  const req = (host: string, origin: string) =>
    new Request(`http://127.0.0.1:3000/api/book`, { headers: { host, origin, "x-pn-local": "1" } });
  validateLocal(req("127.0.0.1:3000", "http://127.0.0.1:3000"), true);
  validateLocal(req("localhost:3000", "http://localhost:3000"));
  validateLocal(req("localhost:3000", "http://localhost:3000"), true);
  assert.throws(() => validateLocal(req("localhost.evil.example:3000", "http://localhost.evil.example:3000"), true));
  assert.throws(() => validateLocal(req("localhost:3000", "http://127.0.0.1:3000"), true));
  assert.throws(() => validateLocal(req("localhost:3000", "http://localhost:3001"), true));
  assert.throws(() => validateLocal(req("evil.example:3000", "http://evil.example:3000"), true));
  assert.throws(() => validateLocal(req("127.0.0.1:3000", "https://evil.example"), true));
  assert.throws(() =>
    validateLocal(
      new Request("http://127.0.0.1:3000/api/book", {
        headers: { host: "127.0.0.1:3000", origin: "http://127.0.0.1:3000" },
      }),
      true,
    ),
  );
});
test("payment commands preserve request IDs and revisions; reject invalid fields", () => {
  const book = fixture(),
    values = initialValues("payment_save", book, book.payments[0]);
  const command = {
    action: "payment_save",
    request_id: id(999),
    data: payload("payment_save", values, { id: id(400), revision: "1" }),
  };
  validateCommand(command);
  assert.equal(command.data.amount_paisa, "1800000");
  assert.equal(command.request_id, id(999));
  assert.throws(() => validateCommand({ ...command, data: { ...command.data, revision: null } }));
  assert.throws(() => validateCommand({ ...command, data: { ...command.data, amount_paisa: "1.50" } }));
  assert.throws(() => validateCommand({ ...command, data: { ...command.data, voided: true } }));
});
test("tenancy rejects overlapping opening balances and invalid dates", () => {
  const book = fixture(),
    values = {
      ...initialValues("tenancy_save", book),
      flat_id: id(14),
      full_name: "Example",
      monthly_rent_paisa: "100",
      opening_due_paisa: "10",
      opening_credit_paisa: "10",
    };
  assert.throws(() =>
    validateCommand({ action: "tenancy_save", request_id: id(999), data: payload("tenancy_save", values, {}) }),
  );
});
test("file verification rejects disguised content and keeps linked paths protected", () => {
  assert.throws(() => inspectFile(new TextEncoder().encode("not a PDF"), "application/pdf"));
  assert.throws(() => inspectFile(new Uint8Array(), "text/plain"));
  assert.throws(() => inspectFile(new TextEncoder().encode("\0invalid"), "text/plain"));
  assert.match(inspectFile(new TextEncoder().encode("%PDF-1.7 test"), "application/pdf"), /^[a-f0-9]{64}$/);
  const path = `tenants/${id(100)}/test.pdf`,
    book = fixture();
  book.tenants[0].profile_image_path = path;
  assert.equal(referencedPaths(book).has(path), true);
  assert.equal(safePath(`tenants/${id(100)}/../test`), false);
});

test("staged document replacements retain the original revision and retry payload", async () => {
  const { uploadCommand } = await import("../src/lib/recovery");
  const path = `tenants/${id(100)}/${id(999)}-hash.pdf`;
  const command = uploadCommand(
    "tenants",
    id(100),
    id(999),
    path,
    { name: "nid.pdf", type: "application/pdf", size: 100 },
    "7",
    { id: id(600), revision: "3" },
    "NID",
  );
  validateCommand(command);
  const restored = JSON.parse(
    JSON.stringify({ command, path, uploaded: true, cleanup: [`tenants/${id(100)}/old.pdf`] }),
  );
  assert.deepEqual(restored.command, command);
  assert.equal(command.data.revision, "3");
  assert.equal(command.data.storage_path, path);
});

test("all management forms produce commands accepted by the shared server contract", () => {
  const book = fixture();
  const cases: [string, Record<string, unknown>, Record<string, unknown>][] = [
    ["tenancy_save", { ...book.tenancies[0] }, { id: id(200), revision: "1", tenant_revision: "1" }],
    ["payment_save", book.payments[0], { id: id(400), revision: "1" }],
    ["rent_charge", book.rent_periods[0], { id: id(300), revision: "1" }],
    ["deposit_save", { id: "", tenancy_id: id(200), amount_paisa: "100", type: "received" }, {}],
    ["expense_save", book.expenses[0], { id: id(600), revision: "1" }],
    ["maintenance_save", book.maintenance[0], { id: id(800), revision: "1", receipt_path: null }],
    ["recurring_save", book.recurring_expenses[0], { id: id(700), revision: "1" }],
    ["building_save", { ...book.building, id: "" }, { revision: "1" }],
    ["move_out", book.tenancies[0], { id: id(200), revision: "1" }],
    ["payment_void", book.payments[0], { id: id(400), revision: "1" }],
    ["expense_void", book.expenses[0], { id: id(600), revision: "1" }],
    ["maintenance_void", book.maintenance[0], { id: id(800), revision: "1" }],
    ["document_delete", {}, { id: id(600), revision: "1" }],
  ];
  for (const [action, row, meta] of cases) {
    const values = initialValues(action, book, row as never);
    assert.doesNotThrow(
      () => validateCommand({ action, request_id: id(999), data: payload(action, values, meta) }),
      action,
    );
  }
});

test("permanent delete requires an exact preview token, supported record type, and confirmation", () => {
  const command = {
    action: "permanent_delete",
    request_id: id(999),
    data: { entity_type: "tenants", id: id(100), token: "a".repeat(32), confirmation: "DELETE" },
  };
  assert.doesNotThrow(() => validateCommand(command));
  for (const data of [
    { ...command.data, confirmation: "" },
    { ...command.data, token: "" },
    { ...command.data, entity_type: "flats" },
  ])
    assert.throws(() => validateCommand({ ...command, data }));
});
