import type { Book, Payment } from "../src/lib/types";
import { today } from "../src/lib/money";
export const id = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, "0")}`;
export function fixture(): Book {
  const month = today().slice(0, 7),
    date = `${month}-01`;
  const book: Book = {
    flats: Array.from({ length: 14 }, (_, i) => ({
      id: id(i + 1),
      floor: String(Math.floor(i / 2) + 1),
      flat_number: `${Math.floor(i / 2) + 1}${i % 2 ? "B" : "A"}`,
      default_rent_paisa: "1800000",
    })),
    tenants: [],
    tenancies: [],
    rent_periods: [],
    payments: [],
    payment_allocations: [],
    expenses: [],
    maintenance: [],
    deposit_transactions: [],
    recurring_expenses: [],
    tenant_documents: [],
    audit_logs: [],
    building: {
      id: "1",
      name: "Prosanti Nibash",
      address: "Example building address",
      owner_name: "Example owner",
      owner_phone: "",
      revision: "1",
    },
  };
  for (let i = 0; i < 5; i++) {
    book.tenants.push({
      id: id(100 + i),
      full_name: ["Example tenant A", "Example tenant B", "Example tenant C", "Example tenant D", "Example tenant E"][
        i
      ],
      phone: `0170000000${i}`,
      revision: "1",
    });
    book.tenancies.push({
      id: id(200 + i),
      tenant_id: id(100 + i),
      flat_id: id(i + 1),
      monthly_rent_paisa: "1800000",
      move_in_date: "2026-01-01",
      ledger_start_date: "2026-01-01",
      status: "active",
      revision: "1",
      agreement_end_date: today(),
    });
    book.rent_periods.push({
      id: id(300 + i),
      tenancy_id: id(200 + i),
      period_date: date,
      amount_paisa: "1800000",
      kind: "monthly",
      revision: "1",
    });
    book.deposit_transactions.push({
      id: id(500 + i),
      tenancy_id: id(200 + i),
      type: "received",
      amount_paisa: "3600000",
      transaction_date: date,
    });
    if (i < 3) {
      const p: Payment = {
        id: id(400 + i),
        tenancy_id: id(200 + i),
        amount_paisa: i === 2 ? "900000" : "1800000",
        payment_date: date,
        through_month: date,
        kind: "cash",
        voided: false,
        receipt_number: `PN-TEST-000${i + 1}`,
        receipt_snapshot: {},
        revision: "1",
      };
      p.receipt_snapshot = {
        ...p,
        receipt_snapshot: undefined,
        tenant: book.tenants[i],
        flat: book.flats[i],
        building: book.building,
        allocations: [{ period_date: date, amount_paisa: p.amount_paisa }],
        remaining_due_paisa: "0",
        credit_paisa: "0",
        payment_method: "Cash",
      };
      book.payments.push(p);
      book.payment_allocations.push({ payment_id: p.id, rent_period_id: id(300 + i), amount_paisa: p.amount_paisa });
    }
  }
  book.expenses.push({
    id: id(600),
    description: "Example water bill",
    category: "Water",
    amount_paisa: "450000",
    expense_date: date,
    voided: false,
    revision: "1",
  });
  book.recurring_expenses.push({
    id: id(700),
    description: "Example security salary",
    category: "Security",
    amount_paisa: "1200000",
    next_date: date,
    frequency: "monthly",
    active: true,
    revision: "1",
    interval_days: "30",
  });
  book.maintenance.push({
    id: id(800),
    title: "Example lift inspection",
    maintenance_date: date,
    amount_paisa: "0",
    reminder_date: today(),
    voided: false,
    revision: "1",
  });
  book.audit_logs.push({
    id: id(900),
    action: "create",
    entity_type: "tenants",
    entity_id: id(100),
    created_at: `${date}T09:00:00Z`,
    new_data: book.tenants[0],
    old_data: null,
  });
  return book;
}
