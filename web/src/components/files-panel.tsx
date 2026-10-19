"use client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import { TableRow } from "@/components/ui/table";
import { TableCell } from "@/components/ui/table";
import { useRef, useState } from "react";
import type { Document, Row } from "@/lib/types";
import { uploadCommand } from "@/lib/recovery";
import { fileTypes } from "@/lib/validation";
import { api, useBook } from "./book-provider";
import { Field, FieldGroup, FieldLabel } from "./ui/field";
import { Dialog, Empty, Icon, Panel, Table } from "./ui";
import { dateLabel } from "@/lib/money";
import type { ViewProps } from "./overview";
const documentTypes = ["Profile photo", "NID", "Agreement", "Other"];
export function Documents({ book, query, files }: ViewProps) {
  const rows = book.tenants.filter((t) =>
    `${t.full_name} ${t.phone ?? ""}`.toLowerCase().includes(query.toLowerCase()),
  );
  return (
    <Panel title="Tenant documents">
      {rows.length ? (
        <Table headers={["Tenant", "Phone", "Documents", ""]}>
          {rows.map((t) => (
            <TableRow key={t.id}>
              <TableCell>
                <strong>{t.full_name}</strong>
              </TableCell>
              <TableCell>{t.phone || "—"}</TableCell>
              <TableCell>{book.tenant_documents.filter((d) => d.tenant_id === t.id).length}</TableCell>
              <TableCell>
                <Button variant="ghost" size="sm" onClick={() => files("tenants", t.id)}>
                  Manage documents <Icon name="arrow" />
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </Table>
      ) : (
        <Empty title="No matching tenants" />
      )}
    </Panel>
  );
}
export function FilesPanel({
  entity,
  id,
  onClose,
  erase,
}: {
  entity: string;
  id: string;
  onClose: () => void;
  erase: ViewProps["erase"];
}) {
  const { book, saving, pending, submit, error: remoteError, staged, stage } = useBook();
  const [file, setFile] = useState<File | null>(null),
    [type, setType] = useState("Other"),
    [existing, setExisting] = useState<Document | null>(null);
  const [error, setError] = useState(""),
    [uploading, setUploading] = useState(false),
    [request, setRequest] = useState(() =>
      staged?.entity === entity && staged.id === id ? staged.command.request_id : crypto.randomUUID(),
    );
  const uploadBusy = useRef(false);
  if (!book) return null;
  const row = (book[entity as "tenants" | "payments" | "expenses" | "maintenance"] as Row[]).find((r) => r.id === id);
  const attachment = String(row?.[entity === "payments" ? "attachment_path" : "receipt_path"] ?? "");
  const documents = book.tenant_documents.filter((d) => d.tenant_id === id);
  const reset = () => {
    setExisting(null);
    setFile(null);
    setType("Other");
    setRequest(crypto.randomUUID());
  };
  const close = () => {
    if (
      !uploading &&
      !saving &&
      (!file || window.confirm("Discard this file selection? Any staged upload is kept for recovery."))
    )
      onClose();
  };
  const upload = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!file || uploadBusy.current) return;
    uploadBusy.current = true;
    setUploading(true);
    setError("");
    try {
      if (file.size > 20971520 || !file.size || !fileTypes[file.type])
        throw new Error("Choose a PDF, JPEG, PNG, WebP, or UTF-8 text file up to 20 MB.");
      if (type === "Profile photo" && !file.type.startsWith("image/"))
        throw new Error("A profile photo must be an image.");
      const digest = Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", await file.arrayBuffer())), (b) =>
        b.toString(16).padStart(2, "0"),
      ).join("");
      const path = `${entity}/${id}/${request}-${digest}.${fileTypes[file.type]}`;
      if (staged && staged.path !== path)
        throw new Error("Reselect the exact original file, or discard the staged upload from the workspace first.");
      const old = existing?.storage_path ?? (entity === "tenants" ? "" : attachment);
      const command =
        staged?.command ?? uploadCommand(entity, id, request, path, file, row?.revision, existing ?? undefined, type);
      const recovery = staged ?? {
        path,
        entity,
        id,
        command,
        cleanup: old && old !== path ? [old] : [],
        uploaded: false,
      };
      stage(recovery);
      const body = new FormData();
      body.set("file", file);
      body.set("entity", entity);
      body.set("id", id);
      body.set("request_id", request);
      const uploaded = await api<{ path: string }>("/api/files", { method: "POST", body });
      if (uploaded.path !== path) throw new Error("Upload path did not match the original file.");
      stage({ ...recovery, uploaded: true });
      if (await submit(command, recovery.cleanup)) {
        stage(null);
        reset();
      }
    } catch (e) {
      setError((e as Error).message);
    } finally {
      uploadBusy.current = false;
      setUploading(false);
    }
  };
  const remove = async () => {
    if (!window.confirm("Remove this attachment?")) return;
    await submit(
      {
        action: "attachment_save",
        request_id: crypto.randomUUID(),
        data: { id, revision: row?.revision, entity_type: entity, storage_path: null },
      },
      [attachment],
    );
  };
  return (
    <Dialog title={entity === "tenants" ? "Documents & profile photo" : "Supporting attachment"} onClose={close} wide>
      <div className="dialog-body">
        <p className="muted">PDF, images or text · Up to 20 MB</p>
        {entity === "tenants" ? (
          documents.map((d) => (
            <div key={d.id} className="file-row">
              <Icon name="documents" />
              <div>
                <strong>{d.file_name}</strong>
                <small>
                  {d.document_type} · {dateLabel(d.created_at)}
                </small>
              </div>
              <Button
                variant="ghost"
                size="sm"
                nativeButton={false}
                render={
                  <a
                    target="_blank"
                    rel="noopener noreferrer"
                    href={`/api/files?view=1&path=${encodeURIComponent(d.storage_path)}`}
                  />
                }
              >
                {" "}
                View{" "}
              </Button>
              <Button
                variant="ghost"
                size="sm"
                nativeButton={false}
                render={<a href={`/api/files?path=${encodeURIComponent(d.storage_path)}`} />}
              >
                {" "}
                Download{" "}
              </Button>
              <Button
                variant="ghost"
                size="sm"
                disabled={saving || !!pending || uploading}
                onClick={() => {
                  setExisting(d);
                  setType(d.document_type);
                }}
              >
                Replace
              </Button>
              <Button
                variant="destructive"
                size="sm"
                disabled={saving || !!pending || uploading}
                onClick={() => erase("tenant_documents", d, d.file_name)}
              >
                Delete
              </Button>
            </div>
          ))
        ) : attachment ? (
          <div className="file-row">
            <Icon name="documents" />
            <strong>Current attachment</strong>
            <Button
              variant="ghost"
              size="sm"
              nativeButton={false}
              render={
                <a
                  target="_blank"
                  rel="noopener noreferrer"
                  href={`/api/files?view=1&path=${encodeURIComponent(attachment)}`}
                />
              }
            >
              {" "}
              View{" "}
            </Button>
            <Button
              variant="ghost"
              size="sm"
              nativeButton={false}
              render={<a href={`/api/files?path=${encodeURIComponent(attachment)}`} />}
            >
              {" "}
              Download{" "}
            </Button>
            <Button
              variant="destructive"
              size="sm"
              disabled={saving || !!pending || uploading}
              onClick={() => void remove()}
            >
              Remove
            </Button>
          </div>
        ) : (
          <Empty title="No attachment yet" />
        )}
        {entity === "tenants" && !documents.length && <Empty title="No documents yet" />}
        {staged && (
          <p className="hint">
            An upload is staged. Reselect the original file to resume unchanged, or return to the workspace to confirm
            its metadata / discard it.
          </p>
        )}
        <form className="upload-form" onSubmit={upload}>
          <h3>{existing ? `Replace ${existing.file_name}` : "Upload a document"}</h3>
          <FieldGroup>
            {entity === "tenants" && (
              <Field>
                <FieldLabel htmlFor="document-type">Document type</FieldLabel>
                <NativeSelect
                  id="document-type"
                  value={type}
                  disabled={uploading || !!pending}
                  onChange={(e) => setType(e.target.value)}
                >
                  {[...new Set([...documentTypes, type])].map((t) => (
                    <option key={t}>{t}</option>
                  ))}
                </NativeSelect>
              </Field>
            )}
            <Field>
              <FieldLabel htmlFor="upload-file">Choose a file</FieldLabel>
              <Input
                id="upload-file"
                aria-label="Choose file"
                type="file"
                accept={Object.keys(fileTypes).join(",")}
                required
                disabled={uploading || saving || !!pending}
                onChange={(e) => {
                  setFile(e.target.files?.[0] ?? null);
                  if (!staged) setRequest(crypto.randomUUID());
                }}
              />
            </Field>
          </FieldGroup>
          {file && (
            <p>
              {file.name} · {(file.size / 1024).toFixed(1)} KB
            </p>
          )}
          {(error || remoteError) && (
            <p className="error" role="alert">
              {error || remoteError}
            </p>
          )}
          {pending && (
            <Button type="button" disabled={saving} onClick={() => void submit(pending.command, pending.cleanup)}>
              Retry original save
            </Button>
          )}
          <div className="actions">
            {existing && (
              <Button type="button" variant="outline" onClick={reset} disabled={uploading || saving || !!pending}>
                Cancel replacement
              </Button>
            )}
            <Button type="submit" disabled={!file || uploading || saving || !!pending}>
              {uploading ? "Uploading…" : "Upload & save"}
            </Button>
          </div>
        </form>
      </div>
    </Dialog>
  );
}
