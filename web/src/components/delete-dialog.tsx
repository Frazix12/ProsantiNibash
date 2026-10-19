"use client";
import { Button } from "@/components/ui/button";
import { Field, FieldLabel } from "./ui/field";
import { Input } from "@/components/ui/input";
import { useEffect, useState } from "react";
import type { Deletion, DeletePreview } from "@/lib/deletion";
import { recordLabels } from "@/lib/deletion";
import { api, useBook } from "./book-provider";
import { Dialog } from "./ui";
export function DeleteDialog({ target, onClose }: { target: Deletion; onClose: () => void }) {
  const { submit, saving, pending, error: saveError } = useBook();
  const [preview, setPreview] = useState<DeletePreview | null>(null),
    [error, setError] = useState(""),
    [confirmation, setConfirmation] = useState("");
  const [request] = useState(() => crypto.randomUUID());
  useEffect(() => {
    let active = true;
    api<DeletePreview>(`/api/delete?entity=${target.entity}&id=${target.row.id}`)
      .then((p) => {
        if (active) setPreview(p);
      })
      .catch((e) => {
        if (active) setError(e.message);
      });
    return () => {
      active = false;
    };
  }, [target.entity, target.row.id]);
  const retrying =
    pending?.command.action === "permanent_delete" &&
    pending.command.data.entity_type === target.entity &&
    pending.command.data.id === target.row.id;
  const erase = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!preview) return;
    const command = retrying
      ? pending!.command
      : {
          action: "permanent_delete",
          request_id: request,
          data: { entity_type: target.entity, id: target.row.id, token: preview.token, confirmation },
        };
    if (await submit(command)) onClose();
  };
  return (
    <Dialog
      title="Delete permanently"
      onClose={() => {
        if (!saving) onClose();
      }}
    >
      <form className="dialog-body" onSubmit={erase}>
        <p className="delete-target">{target.label}</p>
        <p className="error">Cannot be undone. Selected records and their audit history will be removed.</p>
        {preview && (
          <>
            <ul className="delete-counts">
              {Object.entries(preview.counts)
                .filter(([, count]) => Number(count) > 0)
                .map(([name, count]) => (
                  <li key={name}>
                    <span>{recordLabels[name] ?? name}</span>
                    <strong>{count}</strong>
                  </li>
                ))}
              {!!preview.paths.length && (
                <li>
                  <span>Files</span>
                  <strong>{preview.paths.length}</strong>
                </li>
              )}
            </ul>
            <Field>
              <FieldLabel htmlFor="delete-confirmation">Type DELETE</FieldLabel>
              <Input
                id="delete-confirmation"
                value={confirmation}
                onChange={(e) => setConfirmation(e.target.value)}
                autoComplete="off"
                disabled={saving || !!pending}
              />
            </Field>
          </>
        )}
        {!preview && !error && <p>Loading affected records…</p>}
        {(error || saveError) && (
          <p className="error" role="alert">
            {error || saveError}
          </p>
        )}
        <div className="form-footer">
          <Button type="button" variant="outline" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button
            type="submit"
            variant="destructive"
            disabled={saving || !preview || (pending ? !retrying : confirmation !== "DELETE")}
          >
            {saving ? "Deleting…" : pending ? "Retry deletion" : "Delete permanently"}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
