"use client";
import { Checkbox } from "@/components/ui/checkbox";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import { Textarea } from "@/components/ui/textarea";
import { useEffect, useState } from "react";
import { forms, initialValues, payload } from "@/lib/forms";
import { validateCommand } from "@/lib/validation";
import { settlement, tenancyLabel } from "@/lib/book";
import { money, parseMoney } from "@/lib/money";
import type { Row } from "@/lib/types";
import { useBook } from "./book-provider";
import { Dialog } from "./ui";
import { Alert } from "./ui/alert";
import { Field, FieldGroup, FieldLabel } from "./ui/field";
export type Editor = { action: string; row?: Row; meta?: Record<string, unknown>; cleanup?: string[] };
export function RecordForm({ editor, onClose }: { editor: Editor; onClose: () => void }) {
  const { book, saving, pending, submit, error: remoteError } = useBook();
  const { action, row, meta = {} } = editor;
  const [initial] = useState(() => initialValues(action, book!, row));
  const [values, setValues] = useState(initial),
    [request] = useState(() => crypto.randomUUID()),
    [error, setError] = useState("");
  const [metadata] = useState<Record<string, unknown>>(() => ({
    ...(row?.id ? { id: row.id, revision: row.revision } : {}),
    ...meta,
    ...(action === "tenancy_save" && row?.id
      ? { tenant_revision: book!.tenants.find((t) => t.id === row.tenant_id)?.revision }
      : {}),
    ...(action === "maintenance_save" && row?.id ? { receipt_path: row.receipt_path ?? null } : {}),
  }));
  const dirty = JSON.stringify(initial) !== JSON.stringify(values);
  useEffect(() => {
    const warn = (e: BeforeUnloadEvent) => {
      if (dirty) e.preventDefault();
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);
  const close = () => {
    if (!saving && (!dirty || window.confirm("Discard unsaved changes?"))) onClose();
  };
  if (!book) return null;
  const readonly = (key: string) =>
    !!row?.id &&
    ((action === "tenancy_save" && ["flat_id", "ledger_start_date"].includes(key)) ||
      (["payment_save", "rent_charge"].includes(action) && ["tenancy_id", "kind"].includes(key)));
  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    setError("");
    try {
      const command = { action, request_id: request, data: payload(action, values, metadata) };
      validateCommand(command);
      if (
        (action === "move_out" || action.endsWith("_void") || action === "document_delete") &&
        !window.confirm("Confirm this change?")
      )
        return;
      if (await submit(command, editor.cleanup)) onClose();
    } catch (e) {
      setError((e as Error).message);
    }
  };
  return (
    <Dialog title={`${row?.id ? "Edit " : ""}${forms[action].title}`} onClose={close} wide>
      <form onSubmit={save} className="record-form">
        {!forms[action].fields.length && <p>Confirm {forms[action].title.toLowerCase()}. </p>}
        <FieldGroup className="form-grid">
          {forms[action].fields.map((f) => {
            const options =
              f.key === "tenancy_id"
                ? [
                    ["", "Select tenancy"],
                    ...book.tenancies.map((t) => [
                      t.id,
                      `${tenancyLabel(book, t.id)}${t.status !== "active" ? " (moved out)" : ""}`,
                    ]),
                  ]
                : f.key === "flat_id"
                  ? [
                      ["", action === "tenancy_save" ? "Select flat" : "Whole building"],
                      ...book.flats.map((f) => [f.id, f.flat_number]),
                    ]
                  : (f.options ?? []).map((o) => [o, o.replaceAll("_", " ")]);
            const common = {
              id: `field-${f.key}`,
              name: f.key,
              disabled: saving || !!pending || readonly(f.key),
              required: f.required,
              value: String(values[f.key] ?? ""),
              onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
                setValues((v) => ({ ...v, [f.key]: e.target.value })),
            };
            return (
              <Field
                className={f.type === "textarea" ? "span-2" : undefined}
                orientation={f.type === "checkbox" ? "horizontal" : "vertical"}
                data-disabled={common.disabled}
                key={f.key}
              >
                <FieldLabel htmlFor={common.id}>
                  {f.label}
                  {f.required && " *"}
                </FieldLabel>
                {f.type === "select" ? (
                  <NativeSelect {...common}>
                    {options.map(([value, label]) => (
                      <option key={value} value={value}>
                        {label}
                      </option>
                    ))}
                  </NativeSelect>
                ) : f.type === "checkbox" ? (
                  <Checkbox
                    id={common.id}

                    checked={!!values[f.key]}
                    disabled={saving || !!pending}
                    onCheckedChange={(checked) => setValues((v) => ({ ...v, [f.key]: checked }))}
                  />
                ) : f.type === "textarea" ? (
                  <Textarea {...common} rows={3} />
                ) : (
                  <Input
                    {...common}
                    type={f.type === "money" ? "text" : (f.type ?? "text")}
                    inputMode={f.type === "money" ? "decimal" : undefined}
                    maxLength={10000}
                  />
                )}
              </Field>
            );
          })}
        </FieldGroup>
        {action === "move_out" && <SettlementPreview id={String(metadata.id)} values={values} />}
        {(error || remoteError) && (
          <p className="error" role="alert">
            {error || remoteError}
          </p>
        )}
        {pending && (
          <Alert className="pending-banner">
            <p>Save unconfirmed. Retry the original entry.</p>
            <Button
              type="button"
              disabled={saving}
              onClick={async () => {
                if (await submit(pending.command, pending.cleanup)) onClose();
              }}
            >
              Retry original save
            </Button>
          </Alert>
        )}
        <div className="form-footer">
          <Button type="button" variant="outline" onClick={close} disabled={saving}>
            Cancel
          </Button>
          <Button disabled={saving || !!pending} type="submit">
            {saving ? "Saving…" : "Save changes"}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
function SettlementPreview({ id, values }: { id: string; values: Record<string, string | boolean> }) {
  const { book } = useBook();
  let p;
  try {
    p = settlement(
      book!,
      id,
      String(values.move_out_date),
      parseMoney(String(values.damage_paisa)),
      parseMoney(String(values.adjustment_paisa), true),
    );
  } catch {
    return <p className="hint">Enter valid amounts to preview the settlement.</p>;
  }
  return (
    <div className="settlement">
      <h3>Settlement preview</h3>
      <p>
        Rent due <strong>{money(p.due)}</strong> · Credit <strong>{money(p.credit)}</strong> · Deposit{" "}
        <strong>{money(p.deposit)}</strong>
      </p>
      <h3>
        {p.net > 0n ? "Debt retained" : "Refund to tenant"}: {money(p.net < 0n ? -p.net : p.net)}
      </h3>
    </div>
  );
}
