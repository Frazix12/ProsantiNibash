"use client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useState } from "react";
import { AppSidebar, navigation } from "./app-sidebar";
import { SidebarProvider, SidebarInset, SidebarTrigger } from "./ui/sidebar";
import { Separator } from "./ui/separator";
import { Skeleton } from "./ui/skeleton";
import { Alert } from "./ui/alert";
import { InputGroup, InputGroupAddon, InputGroupInput } from "./ui/input-group";
import type { Deletion } from "@/lib/deletion";
import { DeleteDialog } from "./delete-dialog";
import type { Section } from "@/lib/types";
import { today } from "@/lib/money";
import { tenancyLabel } from "@/lib/book";
import { BookProvider, useBook } from "./book-provider";
import { Badge, Empty, Icon, Panel } from "./ui";
import { RecordForm, type Editor } from "./record-form";
import { Overview, Reminders, type ViewProps } from "./overview";
import { Flats, Tenants } from "./tenants";
import { Expenses, MaintenanceRecords, Payments, RecurringExpenses } from "./records";
import { Documents, FilesPanel } from "./files-panel";
import { AuditHistory, Receipt, Reports } from "./reports";
export function Workspace() {
  return (
    <BookProvider>
      <SidebarProvider>
        <WorkspaceContent />
      </SidebarProvider>
    </BookProvider>
  );
}
function WorkspaceContent() {
  const state = useBook(),
    {
      book,
      loading,
      saving,
      error,
      notice,
      pending,
      cleanup,
      refresh,
      submit,
      retryCleanup,
      dismissNotice,
      staged,
      abandonUpload,
    } = state;
  const [section, setSection] = useState<Section>("overview"),
    [month, setMonth] = useState(() => today().slice(0, 7)),
    [query, setQuery] = useState("");
  const [deletion, setDeletion] = useState<Deletion | null>(null);
  const [editor, setEditor] = useState<Editor | null>(null),
    [tenancy, setTenancy] = useState(""),
    [receiptId, setReceipt] = useState("");
  const [fileTarget, setFiles] = useState<{ entity: string; id: string } | null>(null);
  const navigate = (next: Section) => {
    setSection(next);
    setTenancy("");
    setQuery("");
  };
  const selectTenant = (id: string) => {
    setSection("tenants");
    setTenancy(id);
    setQuery("");
  };
  const props: ViewProps | undefined = book
    ? {
        book,
        erase: (entity, row, label) =>
          setDeletion({
            entity,
            row,
            label:
              label ?? String(row.full_name ?? row.description ?? row.title ?? row.receipt_number ?? "Selected record"),
          }),
        month,
        query,
        edit: setEditor,
        selectTenant,
        receipt: setReceipt,
        files: (entity, id) => setFiles({ entity, id }),
        navigate,
      }
    : undefined;
  const current = navigation.flatMap((g) => g.items).find((item) => item.id === section)!;
  return (
    <>
      <AppSidebar section={section} navigate={navigate} name={book?.building.name ?? "Prosanti Nibash"} />
      <SidebarInset className="main-shell">
        <header className="topbar">
          <div className="header-location">
            <SidebarTrigger />
            <Separator orientation="vertical" className="h-5" />
            <span>Workspace</span>
            <span className="breadcrumb-divider">/</span>
            <strong>{current.label}</strong>
          </div>
          <span className="header-property">{book?.building.name ?? "Prosanti Nibash"}</span>
        </header>
        <div className="page-content">
          <div className="page-heading">
            <div>
              <h1>{current.label}</h1>
            </div>
            <div className="heading-actions">
              <label className="month-control">
                <span>Viewing</span>
                <Input
                  type="month"
                  aria-label="Selected month"
                  value={month}
                  onChange={(e) => {
                    if (/^\d{4}-\d{2}$/.test(e.target.value)) setMonth(e.target.value);
                  }}
                />
              </label>
              <Button variant="outline" disabled={loading || saving} onClick={() => void refresh()}>
                <Icon name="refresh" />
                {loading ? "Refreshing…" : "Refresh"}
              </Button>
            </div>
          </div>
          {notice && (
            <Alert className="notice" role="status">
              <Icon name="check" />
              <span>{notice}</span>
              <Button variant="ghost" size="sm" onClick={dismissNotice}>
                Dismiss
              </Button>
            </Alert>
          )}
          {error && (
            <Alert className="error-banner" variant="destructive">
              <div>
                <strong>{book ? "The last operation needs attention" : "Could not open the building book"}</strong>
                <p>{error}</p>
                {book && <p>Displayed records may be out of date.</p>}
              </div>
              <Button variant="outline" onClick={() => void refresh()} disabled={loading || saving}>
                Retry refresh
              </Button>
            </Alert>
          )}
          {pending && (
            <Alert className="pending-banner">
              <strong>An earlier save needs confirmation</strong>
              <p>
                {pending.command.action.replaceAll("_", " ")} · Request {pending.command.request_id}. Retry sends
                exactly the original entry. Editing is paused until resolved.
              </p>
              <details>
                <summary>Review the pending entry</summary>
                <pre>{JSON.stringify(pending.command.data, null, 2)}</pre>
              </details>
              <Button disabled={saving} onClick={() => void submit(pending.command, pending.cleanup)}>
                {saving ? "Confirming…" : "Retry original save"}
              </Button>
            </Alert>
          )}
          {staged && !pending && (
            <Alert className="pending-banner">
              <strong>An upload needs to be completed</strong>
              <p>
                The old document remains safe.{" "}
                {staged.uploaded
                  ? "The upload was confirmed; save its metadata to finish."
                  : "Reselect the original file to confirm its upload."}
              </p>
              <div className="actions">
                {staged.uploaded && (
                  <Button disabled={saving} onClick={() => void submit(staged.command, staged.cleanup)}>
                    Finish document save
                  </Button>
                )}
                <Button variant="outline" onClick={() => setFiles({ entity: staged.entity, id: staged.id })}>
                  Resume upload
                </Button>
                <Button
                  variant="outline"
                  disabled={saving}
                  onClick={() => {
                    if (window.confirm("Discard this staged upload? The unlinked object will be queued for cleanup."))
                      abandonUpload();
                  }}
                >
                  Discard staged upload
                </Button>
              </div>
            </Alert>
          )}
          {!!cleanup.length && (
            <Alert className="notice">
              <div>
                <strong>Records saved; {cleanup.length} old file(s) still need cleanup.</strong>
                <p>Files still linked anywhere will be preserved.</p>
                <details>
                  <summary>Cleanup paths</summary>
                  {cleanup.map((p) => (
                    <p key={p} className="mono">
                      {p}
                    </p>
                  ))}
                </details>
              </div>
              <Button variant="outline" onClick={() => void retryCleanup()}>
                Retry file cleanup
              </Button>
            </Alert>
          )}
          {book && (
            <div className="search-bar">
              <InputGroup>
                <InputGroupAddon>
                  <Icon name="search" />
                </InputGroupAddon>
                <InputGroupInput
                  type="search"
                  aria-label="Search building records"
                  placeholder="Search tenants, payments, expenses…"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                />
              </InputGroup>
              <span>
                {book.flats.length} homes · {book.tenants.length} tenants
              </span>
            </div>
          )}
          {!book && !loading && !error && <Empty title="No building records available" />}
          {!book && loading && (
            <div className="loading-state" role="status">
              <span className="sr-only">Loading building records</span>
              <div className="stats">
                {Array.from({ length: 4 }, (_, i) => (
                  <Skeleton key={i} className="h-28" />
                ))}
              </div>
              <Skeleton className="h-96" />
            </div>
          )}
          {props &&
            (query ? (
              <SearchResults {...props} />
            ) : (
              <SectionContent section={section} props={props} selected={tenancy} onBack={() => setTenancy("")} />
            ))}
        </div>
      </SidebarInset>
      {deletion && (
        <DeleteDialog
          key={`${deletion.entity}/${deletion.row.id}`}
          target={deletion}
          onClose={() => setDeletion(null)}
        />
      )}
      {editor && (
        <RecordForm
          key={`${editor.action}-${editor.row?.id ?? "new"}`}
          editor={editor}
          onClose={() => setEditor(null)}
        />
      )}
      {receiptId && <Receipt key={receiptId} id={receiptId} onClose={() => setReceipt("")} />}
      {fileTarget && (
        <FilesPanel
          erase={(entity, row, label) => setDeletion({ entity, row, label: label ?? "Document" })}
          key={`${fileTarget.entity}/${fileTarget.id}`}
          {...fileTarget}
          onClose={() => setFiles(null)}
        />
      )}
    </>
  );
}
function SectionContent({
  section,
  props,
  selected,
  onBack,
}: {
  section: Section;
  props: ViewProps;
  selected: string;
  onBack: () => void;
}) {
  switch (section) {
    case "overview":
      return <Overview {...props} />;
    case "flats":
      return <Flats {...props} />;
    case "tenants":
      return <Tenants {...props} selected={selected} onBack={onBack} />;
    case "payments":
      return <Payments {...props} />;
    case "expenses":
      return <Expenses {...props} />;
    case "recurring":
      return <RecurringExpenses {...props} />;
    case "maintenance":
      return <MaintenanceRecords {...props} />;
    case "documents":
      return <Documents {...props} />;
    case "reports":
      return <Reports {...props} />;
    case "audit":
      return <AuditHistory />;
    case "reminders":
      return <Reminders {...props} />;
    case "building":
      return (
        <Panel
          title="Building & owner details"
          action={
            <Button
              onClick={() =>
                props.edit({
                  action: "building_save",
                  meta: { revision: props.book.building.revision },
                  row: { ...props.book.building, id: "" },
                })
              }
            >
              Edit details
            </Button>
          }
        >
          <dl className="details">
            {["name", "address", "owner_name", "owner_phone"].map((k) => (
              <div key={k}>
                <dt>{k.replaceAll("_", " ")}</dt>
                <dd>{String(props.book.building[k] || "—")}</dd>
              </div>
            ))}
          </dl>
        </Panel>
      );
  }
}
function SearchResults({ book, query, selectTenant, receipt, edit }: ViewProps) {
  const matches = (value: unknown) => JSON.stringify(value).toLowerCase().includes(query.toLowerCase());
  const tenancies = book.tenancies.filter((t) =>
    matches([tenancyLabel(book, t.id), book.tenants.find((p) => p.id === t.tenant_id)]),
  );
  const payments = book.payments.filter((p) =>
    matches([p.receipt_number, p.reference, tenancyLabel(book, p.tenancy_id)]),
  );
  const expenses = book.expenses.filter((e) => matches([e.description, e.category, e.vendor]));
  const maintenance = book.maintenance.filter((m) => matches([m.title, m.description, m.vendor]));
  return (
    <>
      <Panel title={`Search results for “${query}”`}>
        <h3 className="inset">Tenants</h3>
        {tenancies.map((t) => (
          <Button key={t.id} variant="ghost" className="list-row" onClick={() => selectTenant(t.id)}>
            <span>{tenancyLabel(book, t.id)}</span>
            <Badge>{t.status}</Badge>
          </Button>
        ))}
        <h3 className="inset">Payments</h3>
        {payments.map((p) => (
          <Button key={p.id} variant="ghost" className="list-row" onClick={() => receipt(p.id)}>
            <span>
              {p.receipt_number} · {tenancyLabel(book, p.tenancy_id)}
            </span>
            <Icon name="arrow" />
          </Button>
        ))}
        <h3 className="inset">Expenses & maintenance</h3>
        {expenses.map((e) => (
          <Button
            variant="ghost"
            className="list-row"
            key={e.id}
            onClick={() => edit({ action: "expense_save", row: e })}
          >
            {e.description}
          </Button>
        ))}
        {maintenance.map((m) => (
          <Button
            variant="ghost"
            className="list-row"
            key={m.id}
            onClick={() => edit({ action: "maintenance_save", row: m })}
          >
            {m.title}
          </Button>
        ))}
        {!tenancies.length && !payments.length && !expenses.length && !maintenance.length && (
          <Empty title="No matching records" />
        )}
      </Panel>
    </>
  );
}
