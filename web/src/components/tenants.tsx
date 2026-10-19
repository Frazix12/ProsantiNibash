"use client";
import { Button } from "@/components/ui/button";
import { TableRow } from "@/components/ui/table";
import { TableCell } from "@/components/ui/table";
import { useState } from "react";
import { ToggleGroup, ToggleGroupItem } from "./ui/toggle-group";
import { credit, deposit, due, flatStatus, paid, tenant, tenancyLabel, flat } from "@/lib/book";
import { dateLabel, money } from "@/lib/money";
import type { Row } from "@/lib/types";
import { Badge, Empty, Icon, MoneyStat, Panel, Table } from "./ui";
import type { ViewProps } from "./overview";
export function Flats(props: ViewProps) {
  const { book, month, query, selectTenant, edit, files } = props;
  const [filter, setFilter] = useState("All"),
    [selected, setSelected] = useState("");
  const rows = book.flats.filter(
    (f) =>
      JSON.stringify([f, ...book.tenancies.filter((t) => t.flat_id === f.id).map((t) => tenant(book, t))])
        .toLowerCase()
        .includes(query.toLowerCase()) &&
      (filter === "All" || flatStatus(book, f, month) === filter),
  );
  const unit = book.flats.find((f) => f.id === selected);
  return (
    <>
      <div className="toolbar">
        <ToggleGroup
          className="filter-chips"
          value={[filter]}
          onValueChange={(v) => {
            if (v[0]) setFilter(v[0]);
          }}
        >
          {["All", "Paid", "Partial", "Due", "Overdue", "Vacant", "No charge"].map((s) => (
            <ToggleGroupItem key={s} value={s}>
              {s}
            </ToggleGroupItem>
          ))}
        </ToggleGroup>
        <span className="muted">14 permanent homes · 7 floors</span>
      </div>
      <Panel>
        <Table headers={["Flat", "Floor", "Tenant", "Status", "Monthly rent", ""]}>
          {rows.map((f) => {
            const t = book.tenancies.find((t) => t.flat_id === f.id && t.status === "active");
            return (
              <TableRow key={f.id}>
                <TableCell>
                  <Button variant="ghost" size="sm" onClick={() => setSelected(f.id)}>
                    Flat {f.flat_number}
                  </Button>
                </TableCell>
                <TableCell>{f.floor}</TableCell>
                <TableCell>{t ? tenant(book, t)?.full_name : "Vacant"}</TableCell>
                <TableCell>
                  <Badge>{flatStatus(book, f, month)}</Badge>
                </TableCell>
                <TableCell className="amount">{money(t?.monthly_rent_paisa ?? f.default_rent_paisa)}</TableCell>
                <TableCell>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() =>
                      t
                        ? selectTenant(t.id)
                        : edit({
                            action: "tenancy_save",
                            row: { id: "", flat_id: f.id, monthly_rent_paisa: f.default_rent_paisa },
                          })
                    }
                  >
                    {t ? "Open tenancy" : "Add tenant"}
                    <Icon name="arrow" />
                  </Button>
                </TableCell>
              </TableRow>
            );
          })}
        </Table>
        {!rows.length && <Empty title="No matching flats" />}
      </Panel>
      {unit && (
        <Panel
          title={`Flat ${unit.flat_number} · tenancy history`}
          action={
            <Button variant="ghost" size="sm" onClick={() => setSelected("")}>
              Close
            </Button>
          }
        >
          {book.tenancies
            .filter((t) => t.flat_id === unit.id)
            .map((t) => (
              <Button variant="ghost" className="list-row" key={t.id} onClick={() => selectTenant(t.id)}>
                <span>
                  {tenant(book, t)?.full_name} · {dateLabel(t.move_in_date)} – {dateLabel(t.move_out_date)}
                </span>
                <Badge>{t.status}</Badge>
              </Button>
            ))}
          {!book.tenancies.some((t) => t.flat_id === unit.id) && <Empty title="No previous tenants" />}
          <h3 className="inset">Maintenance history</h3>
          {book.maintenance
            .filter((m) => m.flat_id === unit.id)
            .map((m) => (
              <div key={m.id} className="list-row">
                <span>
                  {m.title} · {dateLabel(m.maintenance_date)}
                </span>
                <Button variant="ghost" size="sm" onClick={() => files("maintenance", m.id)}>
                  Files
                </Button>
              </div>
            ))}
        </Panel>
      )}
    </>
  );
}
export function Tenants({ selected, onBack, ...props }: ViewProps & { selected: string; onBack: () => void }) {
  const { book, query, edit, selectTenant, erase } = props;
  const [filter, setFilter] = useState("active");
  const t = book.tenancies.find((t) => t.id === selected);
  if (t) return <TenantDetail {...props} tenancyId={t.id} onBack={onBack} />;
  const rows = book.tenancies.filter(
    (t) =>
      (filter === "all" || t.status === filter) && tenancyLabel(book, t.id).toLowerCase().includes(query.toLowerCase()),
  );
  const unassigned = book.tenants.filter(
    (person) =>
      !book.tenancies.some((t) => t.tenant_id === person.id) &&
      `${person.full_name} ${person.phone ?? ""}`.toLowerCase().includes(query.toLowerCase()),
  );
  return (
    <>
      <div className="toolbar">
        <ToggleGroup
          className="filter-chips"
          value={[filter]}
          onValueChange={(v) => {
            if (v[0]) setFilter(v[0]);
          }}
        >
          {[
            ["active", "Current tenants"],
            ["moved_out", "Previous tenants"],
            ["all", "All tenancies"],
          ].map(([value, label]) => (
            <ToggleGroupItem key={value} value={value}>
              {label}
            </ToggleGroupItem>
          ))}
        </ToggleGroup>
        <Button onClick={() => edit({ action: "tenancy_save" })}>
          <Icon name="plus" />
          Add tenant
        </Button>
      </div>
      <Panel>
        {rows.length ? (
          <Table headers={["Tenant", "Flat", "Phone", "Move-in", "Monthly rent", "Status", ""]}>
            {rows.map((t) => (
              <TableRow key={t.id}>
                <TableCell>
                  <strong>{tenant(book, t)?.full_name}</strong>
                </TableCell>
                <TableCell>{flat(book, t)?.flat_number}</TableCell>
                <TableCell>{tenant(book, t)?.phone || "—"}</TableCell>
                <TableCell>{dateLabel(t.move_in_date)}</TableCell>
                <TableCell className="amount">{money(t.monthly_rent_paisa)}</TableCell>
                <TableCell>
                  <Badge>{t.status}</Badge>
                </TableCell>
                <TableCell>
                  <Button variant="ghost" size="sm" onClick={() => selectTenant(t.id)}>
                    Open ledger <Icon name="arrow" />
                  </Button>
                  <Button
                    variant="destructive"
                    size="sm"
                    onClick={() => erase("tenants", tenant(book, t)!, tenant(book, t)?.full_name)}
                  >
                    Delete
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </Table>
        ) : (
          <Empty title="No matching tenants"></Empty>
        )}
      </Panel>
      {!!unassigned.length && (
        <Panel title="Tenants without a tenancy">
          <Table headers={["Tenant", "Phone", ""]}>
            {unassigned.map((person) => (
              <TableRow key={person.id}>
                <TableCell>{person.full_name}</TableCell>
                <TableCell>{person.phone || "—"}</TableCell>
                <TableCell>
                  <Button variant="ghost" size="sm" onClick={() => props.files("tenants", person.id)}>
                    Documents
                  </Button>
                  <Button variant="destructive" size="sm" onClick={() => erase("tenants", person, person.full_name)}>
                    Delete
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </Table>
        </Panel>
      )}
    </>
  );
}
function TenantDetail({ tenancyId, onBack, ...props }: ViewProps & { tenancyId: string; onBack: () => void }) {
  const { book, edit, receipt, files, erase } = props;
  const t = book.tenancies.find((t) => t.id === tenancyId)!,
    person = tenant(book, t);
  const charges = book.rent_periods.filter((r) => r.tenancy_id === t.id),
    payments = book.payments.filter((p) => p.tenancy_id === t.id);
  const deposits = book.deposit_transactions.filter((d) => d.tenancy_id === t.id);
  return (
    <>
      <Button variant="ghost" size="sm" className="back" onClick={onBack}>
        ← All tenants
      </Button>
      <div className="detail-heading">
        <div>
          <span className="eyebrow">FLAT {flat(book, t)?.flat_number}</span>
          <h1>{person?.full_name}</h1>
          <p>
            {person?.phone || "No phone recorded"} · Moved in {dateLabel(t.move_in_date)} · <Badge>{t.status}</Badge>
          </p>
        </div>
        <div className="actions">
          <Button variant="outline" onClick={() => edit({ action: "tenancy_save", row: t })}>
            Edit tenant
          </Button>
          <Button variant="destructive" size="sm" onClick={() => erase("tenants", person!, person?.full_name)}>
            Delete tenant
          </Button>
          <Button variant="destructive" size="sm" onClick={() => erase("tenancies", t, tenancyLabel(book, t.id))}>
            Delete tenancy
          </Button>
          <Button
            onClick={() =>
              edit({
                action: "payment_save",
                row: {
                  id: "",
                  tenancy_id: t.id,
                  amount_paisa: (due(book, t.id) > 0n ? due(book, t.id) : 0n).toString(),
                },
              })
            }
          >
            <Icon name="plus" />
            Record payment
          </Button>
        </div>
      </div>
      <div className="stats three">
        <MoneyStat label="Outstanding rent" value={due(book, t.id)} />
        <MoneyStat label="Available rent credit" value={credit(book, t.id)} />
        <MoneyStat label="Deposit held" value={deposit(book, t.id)} />
      </div>
      <Panel
        title="Tenant details"
        action={
          <Button variant="ghost" size="sm" onClick={() => files("tenants", t.tenant_id)}>
            Documents & profile photo <Icon name="arrow" />
          </Button>
        }
      >
        <dl className="details">
          {[
            ["Monthly rent", money(t.monthly_rent_paisa)],
            ["Ledger begins", dateLabel(t.ledger_start_date)],
            ["Agreement ends", dateLabel(t.agreement_end_date)],
            ["Occupation", person?.occupation],
            ["Emergency phone", person?.emergency_phone],
            ["Permanent address", person?.permanent_address],
            ["Notes", person?.notes],
            ["Settlement notes", t.settlement_notes],
          ].map(([label, value]) => (
            <div key={String(label)}>
              <dt>{String(label)}</dt>
              <dd>{String(value || "—")}</dd>
            </div>
          ))}
        </dl>
      </Panel>
      <Panel
        title="Rent ledger"
        action={
          <div className="actions">
            <Button variant="ghost" size="sm" onClick={() => window.print()}>
              Print ledger / PDF
            </Button>
            <Button
              variant="outline"
              onClick={() => edit({ action: "rent_charge", row: { id: "", tenancy_id: t.id } })}
            >
              <Icon name="plus" />
              Add charge
            </Button>
          </div>
        }
      >
        <Table headers={["Date", "Entry", "Charge / payment", "Allocated / reference", "Status", "Actions"]}>
          {[
            ...charges.map((r) => ({ date: r.period_date, id: r.id, charge: r, payment: undefined })),
            ...payments.map((p) => ({ date: p.payment_date, id: p.id, charge: undefined, payment: p })),
          ]
            .sort((a, b) => a.date.localeCompare(b.date) || a.id.localeCompare(b.id))
            .map(({ id, date, charge: r, payment: p }) => (
              <TableRow key={id}>
                <TableCell>{dateLabel(date)}</TableCell>
                <TableCell>{(r?.kind ?? p?.kind)?.replaceAll("_", " ")}</TableCell>
                <TableCell className="amount">{money(r?.amount_paisa ?? p!.amount_paisa)}</TableCell>
                <TableCell>{r ? money(paid(book, r.id)) : p?.receipt_number}</TableCell>
                <TableCell>
                  {p?.voided ? <Badge>Voided</Badge> : r ? <Badge>Charge</Badge> : <Badge>Payment</Badge>}
                </TableCell>
                <TableCell>
                  <div className="actions">
                    <Button
                      variant="destructive"
                      size="sm"
                      onClick={() =>
                        erase(r ? "rent_periods" : "payments", r ?? p!, r ? "Rent charge" : p!.receipt_number)
                      }
                    >
                      Delete
                    </Button>
                    {r ? (
                      <Button variant="ghost" size="sm" onClick={() => edit({ action: "rent_charge", row: r })}>
                        Correct
                      </Button>
                    ) : (
                      <>
                        <Button variant="ghost" size="sm" onClick={() => receipt(p!.id)}>
                          Receipt
                        </Button>
                        <Button variant="ghost" size="sm" onClick={() => files("payments", p!.id)}>
                          Files
                        </Button>
                        {!p?.voided && (
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
                      </>
                    )}
                  </div>
                </TableCell>
              </TableRow>
            ))}
        </Table>
        {!charges.length && !payments.length && <Empty title="No rent entries yet" />}
      </Panel>
      <Panel
        title="Deposit transactions"
        action={
          <Button variant="outline" onClick={() => edit({ action: "deposit_save", row: { id: "", tenancy_id: t.id } })}>
            <Icon name="plus" />
            Record transaction
          </Button>
        }
      >
        {deposits.length ? (
          <Table headers={["Date", "Transaction", "Amount", "Description", "Actions"]}>
            {deposits.map((d) => (
              <TableRow key={d.id}>
                <TableCell>{dateLabel(d.transaction_date)}</TableCell>
                <TableCell>{d.type}</TableCell>
                <TableCell className="amount">{money(d.amount_paisa)}</TableCell>
                <TableCell>{d.description || "—"}</TableCell>
                <TableCell>
                  <Button
                    variant="destructive"
                    size="sm"
                    onClick={() => erase("deposit_transactions", d, "Deposit transaction")}
                  >
                    Delete
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </Table>
        ) : (
          <Empty title="No deposit transactions" />
        )}
      </Panel>
      {t.status === "active" && (
        <div className="moveout-bar">
          <div>
            <h3>Move-out</h3>
          </div>
          <Button variant="outline" onClick={() => edit({ action: "move_out", row: t })}>
            Review move-out
          </Button>
        </div>
      )}
    </>
  );
}
// Keep partial defaults compatible with the same wire records used by edit forms.
export const seed = (values: Record<string, unknown>): Row => ({ id: "", ...values });
