"use client";
import { Checkbox } from "@/components/ui/checkbox";
import { Button } from "@/components/ui/button";
import { NativeSelect } from "@/components/ui/native-select";
import { TableRow } from "@/components/ui/table";
import { TableCell } from "@/components/ui/table";
import { useState } from "react";
import type { Expense, Recurring } from "@/lib/types";
import { dateLabel, money, today } from "@/lib/money";
import { tenancyLabel } from "@/lib/book";
import type { ViewProps } from "./overview";
import { Badge, Empty, Icon, Panel, Table } from "./ui";
import { seed } from "./tenants";
export function Payments({ book, month, query, edit, receipt, files, selectTenant, erase }: ViewProps) {
  const [showVoided, setShowVoided] = useState(false),
    [allDates, setAllDates] = useState(false);
  const rows = book.payments
    .filter(
      (p) =>
        (showVoided || !p.voided) &&
        (allDates || p.payment_date.startsWith(month)) &&
        `${tenancyLabel(book, p.tenancy_id)} ${p.receipt_number} ${p.reference ?? ""}`
          .toLowerCase()
          .includes(query.toLowerCase()),
    )
    .sort((a, b) => b.payment_date.localeCompare(a.payment_date));
  return (
    <>
      <div className="toolbar">
        <div className="actions">
          <label className="check">
            <Checkbox checked={showVoided} onCheckedChange={(checked) => setShowVoided(checked)} />
            Include voided
          </label>
          <label className="check">
            <Checkbox checked={allDates} onCheckedChange={(checked) => setAllDates(checked)} />
            All dates
          </label>
        </div>
        <Button onClick={() => edit({ action: "payment_save" })}>
          <Icon name="plus" />
          Record payment
        </Button>
      </div>
      <Panel>
        {rows.length ? (
          <Table headers={["Tenant / flat", "Receipt", "Date", "Type", "Amount", "Status", "Actions"]}>
            {rows.map((p) => (
              <TableRow key={p.id}>
                <TableCell>
                  <Button variant="ghost" size="sm" onClick={() => selectTenant(p.tenancy_id)}>
                    {tenancyLabel(book, p.tenancy_id)}
                  </Button>
                </TableCell>
                <TableCell className="mono">{p.receipt_number}</TableCell>
                <TableCell>{dateLabel(p.payment_date)}</TableCell>
                <TableCell>{p.kind.replaceAll("_", " ")}</TableCell>
                <TableCell className="amount">{money(p.amount_paisa)}</TableCell>
                <TableCell>
                  <Badge>{p.voided ? "Voided" : "Recorded"}</Badge>
                </TableCell>
                <TableCell>
                  <div className="actions">
                    <Button variant="ghost" size="sm" onClick={() => receipt(p.id)}>
                      Receipt
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => files("payments", p.id)}>
                      Files
                    </Button>
                    <Button variant="destructive" size="sm" onClick={() => erase("payments", p)}>
                      Delete
                    </Button>
                    {!p.voided && (
                      <>
                        <Button variant="ghost" size="sm" onClick={() => edit({ action: "payment_save", row: p })}>
                          Edit
                        </Button>
                        <Button
                          variant="destructive"
                          size="sm"
                          onClick={() => edit({ action: "payment_void", row: p })}
                        >
                          Void
                        </Button>
                      </>
                    )}
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </Table>
        ) : (
          <Empty title="No payments found"></Empty>
        )}
      </Panel>
    </>
  );
}
export function Expenses({ book, month, query, edit, files, erase }: ViewProps) {
  const [category, setCategory] = useState("All"),
    [voided, setVoided] = useState(false),
    [allDates, setAllDates] = useState(false);
  const rows = book.expenses
    .filter(
      (e) =>
        (voided || !e.voided) &&
        (allDates || e.expense_date.startsWith(month)) &&
        (category === "All" || e.category === category) &&
        `${e.description} ${e.category} ${e.vendor ?? ""}`.toLowerCase().includes(query.toLowerCase()),
    )
    .sort((a, b) => b.expense_date.localeCompare(a.expense_date));
  const maintenance = (e: Expense) => book.maintenance.find((m) => m.expense_id === e.id);
  return (
    <>
      <div className="toolbar">
        <div className="actions">
          <label className="inline">
            Category
            <NativeSelect value={category} onChange={(e) => setCategory(e.target.value)}>
              {["All", ...new Set(book.expenses.map((e) => e.category))].map((c) => (
                <option key={c}>{c}</option>
              ))}
            </NativeSelect>
          </label>
          <label className="check">
            <Checkbox checked={voided} onCheckedChange={(checked) => setVoided(checked)} />
            Include voided
          </label>
          <label className="check">
            <Checkbox checked={allDates} onCheckedChange={(checked) => setAllDates(checked)} />
            All dates
          </label>
        </div>
        <Button onClick={() => edit({ action: "expense_save" })}>
          <Icon name="plus" />
          Add expense
        </Button>
      </div>
      <Panel>
        {rows.length ? (
          <Table headers={["Description", "Date", "Category", "Scope", "Amount", "Status", "Actions"]}>
            {rows.map((e) => (
              <TableRow key={e.id}>
                <TableCell>
                  <strong>{e.description}</strong>
                  {maintenance(e) && <small className="muted block">Linked to maintenance</small>}
                </TableCell>
                <TableCell>{dateLabel(e.expense_date)}</TableCell>
                <TableCell>{e.category}</TableCell>
                <TableCell>{book.flats.find((f) => f.id === e.flat_id)?.flat_number ?? "Building"}</TableCell>
                <TableCell className="amount">{money(e.amount_paisa)}</TableCell>
                <TableCell>
                  <Badge>{e.voided ? "Voided" : "Recorded"}</Badge>
                </TableCell>
                <TableCell>
                  <div className="actions">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => files(maintenance(e) ? "maintenance" : "expenses", maintenance(e)?.id ?? e.id)}
                    >
                      Files
                    </Button>
                    <Button variant="destructive" size="sm" onClick={() => erase("expenses", e)}>
                      Delete
                    </Button>
                    {!e.voided && (
                      <>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() =>
                            edit({
                              action: maintenance(e) ? "maintenance_save" : "expense_save",
                              row: maintenance(e) ?? e,
                            })
                          }
                        >
                          Edit
                        </Button>
                        <Button
                          variant="destructive"
                          size="sm"
                          onClick={() =>
                            edit({
                              action: maintenance(e) ? "maintenance_void" : "expense_void",
                              row: maintenance(e) ?? e,
                            })
                          }
                        >
                          Void
                        </Button>
                      </>
                    )}
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </Table>
        ) : (
          <Empty title="No expenses found"></Empty>
        )}
      </Panel>
    </>
  );
}
export function RecurringExpenses({ book, query, edit, erase }: ViewProps) {
  const rows = book.recurring_expenses
    .filter((r) => `${r.description} ${r.category}`.toLowerCase().includes(query.toLowerCase()))
    .sort((a, b) => a.next_date.localeCompare(b.next_date));
  const pay = (r: Recurring) =>
    edit({
      action: "expense_save",
      row: seed({ ...r, id: "", expense_date: today() }),
      meta: { recurring_expense_id: r.id, recurring_revision: r.revision, scheduled_date: r.next_date },
    });
  return (
    <>
      <div className="toolbar">
        <Button onClick={() => edit({ action: "recurring_save" })}>
          <Icon name="plus" />
          Add schedule
        </Button>
      </div>
      <div className="record-cards">
        {rows.map((r) => (
          <Panel key={r.id}>
            <div className="card-top">
              <span className="card-icon">
                <Icon name="recurring" />
              </span>
              <Badge>{r.active ? "Active" : "Paused"}</Badge>
            </div>
            <h2>{r.description}</h2>
            <strong className="card-money">{money(r.amount_paisa)}</strong>
            <p>
              {r.frequency} · {r.category}
            </p>
            <p className={r.active && r.next_date <= today() ? "due-text" : "muted"}>
              Next due {dateLabel(r.next_date)}
            </p>
            <div className="card-footer">
              <Button variant="ghost" size="sm" onClick={() => edit({ action: "recurring_save", row: r })}>
                Edit schedule
              </Button>
              <Button variant="destructive" size="sm" onClick={() => erase("recurring_expenses", r)}>
                Delete
              </Button>
              {r.active && (
                <Button variant="outline" onClick={() => pay(r)}>
                  Record payment <Icon name="arrow" />
                </Button>
              )}
            </div>
          </Panel>
        ))}
      </div>
      {!rows.length && (
        <Panel>
          <Empty title="No recurring schedules" />
        </Panel>
      )}
    </>
  );
}
export function MaintenanceRecords({ book, query, edit, files, erase }: ViewProps) {
  const [showVoided, setShowVoided] = useState(false),
    [flatId, setFlatId] = useState("all");
  const rows = book.maintenance
    .filter(
      (m) =>
        (showVoided || !m.voided) &&
        (flatId === "all" || m.flat_id === flatId || (flatId === "building" && !m.flat_id)) &&
        `${m.title} ${m.description ?? ""} ${m.vendor ?? ""}`.toLowerCase().includes(query.toLowerCase()),
    )
    .sort((a, b) => b.maintenance_date.localeCompare(a.maintenance_date));
  return (
    <>
      <div className="toolbar">
        <div className="actions">
          <label className="inline">
            Scope
            <NativeSelect value={flatId} onChange={(e) => setFlatId(e.target.value)}>
              <option value="all">All locations</option>
              <option value="building">Whole building</option>
              {book.flats.map((f) => (
                <option key={f.id} value={f.id}>
                  Flat {f.flat_number}
                </option>
              ))}
            </NativeSelect>
          </label>
          <label className="check">
            <Checkbox checked={showVoided} onCheckedChange={(checked) => setShowVoided(checked)} />
            Include voided
          </label>
        </div>
        <Button onClick={() => edit({ action: "maintenance_save" })}>
          <Icon name="plus" />
          Add maintenance
        </Button>
      </div>
      <Panel>
        {rows.length ? (
          <Table headers={["Maintenance", "Scope", "Date", "Cost", "Reminder", "Status", "Actions"]}>
            {rows.map((m) => (
              <TableRow key={m.id}>
                <TableCell>
                  <strong>{m.title}</strong>
                  <small className="muted block">{String(m.description ?? "")}</small>
                </TableCell>
                <TableCell>{book.flats.find((f) => f.id === m.flat_id)?.flat_number ?? "Building"}</TableCell>
                <TableCell>{dateLabel(m.maintenance_date)}</TableCell>
                <TableCell className="amount">{money(m.amount_paisa)}</TableCell>
                <TableCell>{dateLabel(m.reminder_date)}</TableCell>
                <TableCell>
                  <Badge>{m.voided ? "Voided" : m.expense_id ? "Expense linked" : "Reminder only"}</Badge>
                </TableCell>
                <TableCell>
                  <div className="actions">
                    <Button variant="ghost" size="sm" onClick={() => files("maintenance", m.id)}>
                      Files
                    </Button>
                    <Button variant="destructive" size="sm" onClick={() => erase("maintenance", m)}>
                      Delete
                    </Button>
                    {!m.voided && (
                      <>
                        <Button variant="ghost" size="sm" onClick={() => edit({ action: "maintenance_save", row: m })}>
                          Edit
                        </Button>
                        <Button
                          variant="destructive"
                          size="sm"
                          onClick={() => edit({ action: "maintenance_void", row: m })}
                        >
                          Void
                        </Button>
                      </>
                    )}
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </Table>
        ) : (
          <Empty title="No maintenance records" />
        )}
      </Panel>
    </>
  );
}
