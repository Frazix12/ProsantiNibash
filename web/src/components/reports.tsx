"use client";
import { Button } from "@/components/ui/button";
import { TableRow } from "@/components/ui/table";
import { TableCell } from "@/components/ui/table";
import { useEffect, useState } from "react";
import { ToggleGroup, ToggleGroupItem } from "./ui/toggle-group";
import { csv, due, summary, tenancyLabel } from "@/lib/book";
import { dateLabel, money, sum } from "@/lib/money";
import type { Audit, Payment } from "@/lib/types";
import type { ViewProps } from "./overview";
import { api } from "./book-provider";
import { Dialog, Empty, Icon, Panel, Table } from "./ui";
export function Reports({ book, month, selectTenant }: ViewProps) {
  const [annual, setAnnual] = useState(false),
    [error, setError] = useState("");
  const months = annual
    ? Array.from({ length: 12 }, (_, i) => `${month.slice(0, 4)}-${String(i + 1).padStart(2, "0")}`)
    : [month];
  const totals = months.map((m) => ({ month: m, ...summary(book, m) }));
  const download = () => {
    try {
      const url = URL.createObjectURL(new Blob(["\uFEFF", csv(book)], { type: "text/csv;charset=utf-8" }));
      const link = document.createElement("a");
      link.href = url;
      link.download = "rent-book.csv";
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch {
      setError("Export could not be generated. Retry.");
    }
  };
  return (
    <>
      <div className="toolbar no-print">
        <ToggleGroup
          value={[annual ? "annual" : "monthly"]}
          onValueChange={(v) => {
            if (v[0]) setAnnual(v[0] === "annual");
          }}
          aria-label="Report period"
        >
          <ToggleGroupItem value="monthly">Monthly report</ToggleGroupItem>
          <ToggleGroupItem value="annual">Annual report</ToggleGroupItem>
        </ToggleGroup>
        <div className="actions">
          <Button variant="outline" onClick={download}>
            <Icon name="download" />
            Download ledger CSV
          </Button>
          <Button onClick={() => window.print()}>Print / save PDF</Button>
        </div>
      </div>
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      <Panel title={`${book.building.name} · ${annual ? month.slice(0, 4) : month} financial report`}>
        <Table headers={["Month", "Expected rent", "Rent cash", "Current due", "Older dues", "Expenses", "Net cash"]}>
          {totals.map((t) => (
            <TableRow key={t.month}>
              <TableCell>{t.month}</TableCell>
              {[t.expected, t.collected, t.currentDue, t.oldDue, t.expenses, t.net].map((v, i) => (
                <TableCell className="amount" key={i}>
                  {money(v)}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </Table>
        {annual && (
          <p className="report-total">
            Annual net cash <strong>{money(sum(totals, (t) => t.net))}</strong>
          </p>
        )}
      </Panel>
      <Panel title="Outstanding balances">
        {book.tenancies
          .filter((t) => due(book, t.id) > 0n)
          .map((t) => (
            <Button key={t.id} variant="ghost" className="list-row" onClick={() => selectTenant(t.id)}>
              <span>{tenancyLabel(book, t.id)}</span>
              <strong>{money(due(book, t.id))}</strong>
            </Button>
          ))}
        {!book.tenancies.some((t) => due(book, t.id) > 0n) && <Empty title="No outstanding balances" />}
      </Panel>
    </>
  );
}
export function Receipt({ id, onClose }: { id: string; onClose: () => void }) {
  const [payment, setPayment] = useState<Payment | null>(null),
    [error, setError] = useState(""),
    [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    api<Payment>(`/api/receipt?id=${id}`)
      .then((p) => {
        if (active) setPayment(p);
      })
      .catch((e) => {
        if (active) setError(e.message);
      });
    return () => {
      active = false;
    };
  }, [id, retry]);
  const snapshot = payment?.receipt_snapshot ?? {},
    person = snapshot.tenant as Record<string, unknown> | undefined,
    unit = snapshot.flat as Record<string, unknown> | undefined,
    building = snapshot.building as Record<string, unknown> | undefined;
  const allocations = (snapshot.allocations ?? []) as { period_date: string; amount_paisa: string }[];
  return (
    <Dialog title="Original payment receipt" onClose={onClose} wide>
      <article className="receipt">
        <div className="receipt-brand">
          <Icon name="building" />
          <h2>{String(building?.name ?? "Prosanti Nibash")}</h2>
          <span>PAYMENT RECEIPT</span>
        </div>
        {error && (
          <div className="error" role="alert">
            {error}
            <Button
              variant="outline"
              onClick={() => {
                setError("");
                setRetry((r) => r + 1);
              }}
            >
              Retry
            </Button>
          </div>
        )}
        {!payment && !error && <p>Loading original receipt…</p>}
        {payment && (
          <>
            <div className="receipt-number">
              <span>{payment.receipt_number}</span>
              <span>{dateLabel(snapshot.payment_date)}</span>
            </div>
            <h2>{String(person?.full_name ?? "Tenant")}</h2>
            <p>
              Flat {String(unit?.flat_number ?? "—")} · {String(snapshot.kind ?? payment.kind).replaceAll("_", " ")}
            </p>
            <strong className="receipt-amount">{money(String(snapshot.amount_paisa ?? payment.amount_paisa))}</strong>
            <dl className="details">
              <div>
                <dt>Method</dt>
                <dd>{String(snapshot.payment_method ?? "—")}</dd>
              </div>
              <div>
                <dt>Reference</dt>
                <dd>{String(snapshot.reference ?? "—")}</dd>
              </div>
              <div>
                <dt>Remaining due at issuance</dt>
                <dd>{money(String(snapshot.remaining_due_paisa ?? "0"))}</dd>
              </div>
              <div>
                <dt>Credit at issuance</dt>
                <dd>{money(String(snapshot.credit_paisa ?? "0"))}</dd>
              </div>
            </dl>
            <Table headers={["Allocated rent period", "Amount"]}>
              {allocations.map((a, i) => (
                <TableRow key={i}>
                  <TableCell>{dateLabel(a.period_date)}</TableCell>
                  <TableCell>{money(a.amount_paisa)}</TableCell>
                </TableRow>
              ))}
            </Table>
            {(payment.voided || BigInt(payment.revision ?? "1") > 1n) && (
              <p className="error">Changed or voided after issue.</p>
            )}
            <Button variant="default" className="no-print" onClick={() => window.print()}>
              Print receipt / save PDF
            </Button>
          </>
        )}
      </article>
    </Dialog>
  );
}
export function AuditHistory() {
  const [rows, setRows] = useState<Audit[]>([]),
    [loading, setLoading] = useState(true),
    [error, setError] = useState(""),
    [more, setMore] = useState(false),
    [detail, setDetail] = useState<Audit | null>(null);
  const load = async (before?: Audit) => {
    setLoading(true);
    setError("");
    try {
      const page = await api<Audit[]>(
        `/api/audit${before ? `?before=${before.id}&time=${encodeURIComponent(before.created_at)}` : ""}`,
      );
      setRows((current) => (before ? [...current, ...page] : page));
      setMore(page.length === 50);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    let active = true;
    api<Audit[]>("/api/audit")
      .then((page) => {
        if (active) {
          setRows(page);
          setMore(page.length === 50);
        }
      })
      .catch((e) => {
        if (active) setError(e.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);
  const inspect = async (id: string) => {
    try {
      setDetail(await api<Audit>(`/api/audit?id=${id}`));
    } catch (e) {
      setError((e as Error).message);
    }
  };
  return (
    <Panel
      title="History of changes"
      action={
        <Button variant="ghost" size="sm" disabled={loading} onClick={() => void load()}>
          Refresh
        </Button>
      }
    >
      {error && (
        <p className="error inset" role="alert">
          {error}
          <Button variant="outline" onClick={() => void load()}>
            Retry
          </Button>
        </p>
      )}
      <Table headers={["When", "Action", "Record type", "Record ID", ""]}>
        {rows.map((a) => (
          <TableRow key={a.id}>
            <TableCell>
              {dateLabel(a.created_at)}
              <small className="muted block">
                {new Date(a.created_at).toLocaleTimeString("en-GB", { timeZone: "Asia/Dhaka" })} Dhaka
              </small>
            </TableCell>
            <TableCell>{a.action}</TableCell>
            <TableCell>{a.entity_type.replaceAll("_", " ")}</TableCell>
            <TableCell className="mono">{a.entity_id.slice(0, 8)}…</TableCell>
            <TableCell>
              <Button variant="ghost" size="sm" onClick={() => void inspect(a.id)}>
                Inspect change
              </Button>
            </TableCell>
          </TableRow>
        ))}
      </Table>
      {!rows.length && !loading && !error && <Empty title="No changes recorded" />}
      {loading && <p className="inset">Loading history…</p>}
      {more && (
        <div className="inset">
          <Button disabled={loading} variant="outline" onClick={() => void load(rows.at(-1))}>
            Load older changes
          </Button>
        </div>
      )}
      {detail && (
        <Dialog title={`${detail.action} · ${detail.entity_type}`} onClose={() => setDetail(null)} wide>
          <div className="dialog-body">
            <p className="muted">
              {detail.entity_id} · {dateLabel(detail.created_at)}
            </p>
            <div className="two-columns">
              <section>
                <h3>Before</h3>
                <pre>{JSON.stringify(detail.old_data ?? null, null, 2)}</pre>
              </section>
              <section>
                <h3>After</h3>
                <pre>{JSON.stringify(detail.new_data ?? null, null, 2)}</pre>
              </section>
            </div>
          </div>
        </Dialog>
      )}
    </Panel>
  );
}
