"use client";
import { Button } from "@/components/ui/button";
import { TableRow } from "@/components/ui/table";
import { TableCell } from "@/components/ui/table";
import type { Book, Section, Row } from "@/lib/types";
import { due, flatStatus, summary, tenancyLabel, tenant } from "@/lib/book";
import { money, dateLabel, today } from "@/lib/money";
import { Badge, Empty, Icon, MoneyStat, Panel, Table } from "./ui";
import type { Editor } from "./record-form";
export type ViewProps = {
  erase: (entity: string, row: Row, label?: string) => void;
  book: Book;
  month: string;
  query: string;
  edit: (editor: Editor) => void;
  selectTenant: (id: string) => void;
  receipt: (id: string) => void;
  files: (entity: string, id: string) => void;
  navigate: (section: Section) => void;
};
export function Overview({ book, month, selectTenant, navigate, receipt }: ViewProps) {
  const totals = summary(book, month),
    occupied = book.tenancies.filter((t) => t.status === "active").length;
  const outstanding = book.tenancies
    .filter((t) => due(book, t.id, month) > 0n)
    .sort((a, b) => (due(book, a.id, month) > due(book, b.id, month) ? -1 : 1));
  const latest = [...book.payments]
    .filter((p) => !p.voided)
    .sort((a, b) => b.payment_date.localeCompare(a.payment_date))
    .slice(0, 5);
  return (
    <>
      <div className="stats">
        <MoneyStat label="Rent collected" value={totals.collected} accent />
        <MoneyStat label="Outstanding rent" value={totals.currentDue + totals.oldDue} />
        <MoneyStat label="Building expenses" value={totals.expenses} />
        <MoneyStat label="Net cash" value={totals.net} />
      </div>
      <div className="overview-grid">
        <Panel
          title="Your building"
          action={
            <Button variant="ghost" size="sm" onClick={() => navigate("flats")}>
              View all flats <Icon name="arrow" />
            </Button>
          }
        >
          <div className="occupancy">
            <span>
              <strong>{occupied}</strong> / {book.flats.length} flats occupied
            </span>
            <Badge>{`${book.flats.length - occupied} vacant`}</Badge>
          </div>
          <div className="flat-grid">
            {book.flats.map((f) => {
              const t = book.tenancies.find((t) => t.flat_id === f.id && t.status === "active"),
                status = flatStatus(book, f, month);
              return (
                <Button
                  key={f.id}
                  variant="outline"
                  className={`flat-tile ${status.toLowerCase().replace(" ", "-")}`}
                  onClick={() => (t ? selectTenant(t.id) : navigate("flats"))}
                >
                  <strong>{f.flat_number}</strong>
                  {t && <small>{tenant(book, t)?.full_name}</small>}
                  <span>
                    <i />
                    {status}
                  </span>
                </Button>
              );
            })}
          </div>
          <div className="legend">
            <span>
              <i className="green-dot" />
              Paid
            </span>
            <span>
              <i className="amber-dot" />
              Needs attention
            </span>
            <span>
              <i className="gray-dot" />
              Vacant
            </span>
          </div>
        </Panel>
        <Panel title="Needs attention" action={<span className="count">{outstanding.length}</span>}>
          {outstanding.length ? (
            <div className="attention-list">
              {outstanding.slice(0, 5).map((t) => (
                <Button key={t.id} variant="ghost" className="attention-row" onClick={() => selectTenant(t.id)}>
                  <span className="avatar">{tenant(book, t)?.full_name.slice(0, 1)}</span>
                  <span>
                    <strong>{tenant(book, t)?.full_name}</strong>
                    <small>Flat {book.flats.find((f) => f.id === t.flat_id)?.flat_number} · Outstanding rent</small>
                  </span>
                  <b>{money(due(book, t.id, month))}</b>
                </Button>
              ))}
            </div>
          ) : (
            <Empty title="All caught up"></Empty>
          )}
        </Panel>
      </div>
      <Panel
        title="Recent payments"
        action={
          <Button variant="ghost" size="sm" onClick={() => navigate("payments")}>
            All payments <Icon name="arrow" />
          </Button>
        }
      >
        {latest.length ? (
          <Table headers={["Tenant / flat", "Date", "Receipt", "Amount", ""]}>
            {latest.map((p) => (
              <TableRow key={p.id}>
                <TableCell>
                  <strong>{tenancyLabel(book, p.tenancy_id)}</strong>
                </TableCell>
                <TableCell>{dateLabel(p.payment_date)}</TableCell>
                <TableCell className="mono">{p.receipt_number}</TableCell>
                <TableCell className="amount">{money(p.amount_paisa)}</TableCell>
                <TableCell>
                  <Button variant="ghost" size="sm" onClick={() => receipt(p.id)}>
                    View receipt <Icon name="arrow" />
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </Table>
        ) : (
          <Empty title="No recent payments"></Empty>
        )}
      </Panel>
    </>
  );
}
export function Reminders({ book, month, selectTenant, navigate }: ViewProps) {
  const now = today(),
    in30 = new Date(`${now}T00:00:00Z`);
  in30.setUTCDate(in30.getUTCDate() + 30);
  const agreements = book.tenancies.filter(
    (t) =>
      t.status === "active" &&
      t.agreement_end_date &&
      t.agreement_end_date >= now &&
      t.agreement_end_date <= in30.toISOString().slice(0, 10),
  );
  const dues = book.tenancies.filter((t) => due(book, t.id, month) > 0n);
  const recurring = book.recurring_expenses.filter((r) => r.active && r.next_date <= now);
  const maintenance = book.maintenance.filter((m) => !m.voided && m.reminder_date && m.reminder_date <= now);
  return (
    <div className="two-columns">
      <Panel title="Outstanding rent">
        {dues.map((t) => (
          <Button variant="ghost" className="list-row" key={t.id} onClick={() => selectTenant(t.id)}>
            <span>{tenancyLabel(book, t.id)}</span>
            <strong>{money(due(book, t.id, month))}</strong>
          </Button>
        ))}
        {!dues.length && <Empty title="No rent outstanding" />}
      </Panel>
      <Panel title="Agreements ending within 30 days">
        {agreements.map((t) => (
          <Button variant="ghost" className="list-row" key={t.id} onClick={() => selectTenant(t.id)}>
            <span>{tenancyLabel(book, t.id)}</span>
            <span>{dateLabel(t.agreement_end_date)}</span>
          </Button>
        ))}
        {!agreements.length && <Empty title="No agreements ending soon" />}
      </Panel>
      <Panel title="Recurring expenses due">
        {recurring.map((r) => (
          <Button variant="ghost" className="list-row" key={r.id} onClick={() => navigate("recurring")}>
            <span>{r.description}</span>
            <span>
              {dateLabel(r.next_date)} · {money(r.amount_paisa)}
            </span>
          </Button>
        ))}
        {!recurring.length && <Empty title="No scheduled expenses due" />}
      </Panel>
      <Panel title="Maintenance reminders">
        {maintenance.map((m) => (
          <Button variant="ghost" className="list-row" key={m.id} onClick={() => navigate("maintenance")}>
            <span>{m.title}</span>
            <span>{dateLabel(m.reminder_date)}</span>
          </Button>
        ))}
        {!maintenance.length && <Empty title="No maintenance reminders due" />}
      </Panel>
    </div>
  );
}
