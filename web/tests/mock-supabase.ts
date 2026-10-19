// Disposable in-memory backend for browser checks; never reads real credentials or data.
import { createHash } from "node:crypto";
import { createServer } from "node:http";
import type { Command, Row } from "../src/lib/types";
import { fixture, id } from "./fixtures";
let book = fixture(),
  sequence = 1000;
const commands = new Map<string, { payload: string; result: unknown }>(),
  objects = new Map<string, { bytes: Buffer; mime: string }>();
const tables: Record<string, keyof typeof book> = {
  payment_save: "payments",
  expense_save: "expenses",
  maintenance_save: "maintenance",
  rent_charge: "rent_periods",
  deposit_save: "deposit_transactions",
  recurring_save: "recurring_expenses",
  document_save: "tenant_documents",
};
function execute(c: Command): unknown {
  const old = commands.get(c.request_id);
  if (old) {
    if (old.payload !== JSON.stringify(c)) throw new Error("Request already used with different values");
    return old.result;
  }
  const table = tables[c.action],
    data = c.data;
  let result: unknown;
  if (table) {
    const rows = book[table] as Row[],
      existing = rows.find((r) => r.id === data.id);
    if (existing && existing.revision !== data.revision)
      throw new Error("This record changed on another device. Reload and retry.");
    const row = {
      voided: false,
      kind: "cash",
      receipt_snapshot: {},
      created_at: new Date().toISOString(),
      ...(existing ?? {}),
      ...data,
      id: existing?.id ?? id(sequence++),
      revision: String(Number(existing?.revision ?? "0") + 1),
    };
    if (c.action === "payment_save")
      Object.assign(row, { receipt_number: existing?.receipt_number ?? `PN-TEST-${sequence}` });
    if (existing) Object.assign(existing, row);
    else rows.push(row);
    result = row;
  } else if (c.action === "tenancy_save") {
    const t = book.tenancies.find((t) => t.id === data.id),
      p = book.tenants.find((p) => p.id === t?.tenant_id);
    if (t && t.revision !== data.revision) throw new Error("This record changed on another device. Reload and retry.");
    if (p) {
      Object.assign(p, data, { id: p.id, revision: String(Number(p.revision) + 1) });
      Object.assign(t!, data, { revision: String(Number(t!.revision) + 1) });
      result = t;
    } else {
      const person = { ...data, id: id(sequence++), revision: "1" };
      book.tenants.push(person as never);
      const tenancy = { ...data, id: id(sequence++), tenant_id: person.id, status: "active", revision: "1" };
      book.tenancies.push(tenancy as never);
      result = tenancy;
    }
  } else if (c.action === "building_save") {
    if (data.revision !== book.building.revision) throw new Error("This record changed on another device");
    Object.assign(book.building, data, { revision: String(Number(book.building.revision) + 1) });
    result = book.building;
  } else if (c.action === "attachment_save") {
    const rows = book[String(data.entity_type) as "payments"] as Row[],
      r = rows.find((r) => r.id === data.id)!;
    if (r.revision !== data.revision) throw new Error("This record changed on another device");
    r[data.entity_type === "payments" ? "attachment_path" : "receipt_path"] = data.storage_path;
    r.revision = String(Number(r.revision) + 1);
    result = r;
  } else if (c.action === "document_delete") {
    book.tenant_documents = book.tenant_documents.filter((d) => d.id !== data.id);
    result = { id: data.id };
  } else if (c.action.endsWith("_void")) {
    const table = c.action === "payment_void" ? "payments" : c.action === "expense_void" ? "expenses" : "maintenance";
    const r = book[table].find((r) => r.id === data.id)!;
    r.voided = true;
    r.revision = String(Number(r.revision) + 1);
    result = r;
  } else if (c.action === "move_out") {
    const t = book.tenancies.find((t) => t.id === data.id)!;
    Object.assign(t, data, { status: "moved_out", revision: String(Number(t.revision) + 1) });
    result = t;
  } else throw new Error("Unsupported action");
  book.audit_logs.unshift({
    id: id(sequence++),
    action: "update",
    entity_type: c.action,
    entity_id: String((result as Row).id),
    created_at: new Date().toISOString(),
    new_data: result,
    old_data: null,
  });
  commands.set(c.request_id, { payload: JSON.stringify(c), result });
  return result;
}
// Wire fixture supports individual rows; SQL tests exercise the real cascade graph.
function deletePreview(entity: keyof typeof book, target: string) {
  const rows = book[entity];
  if (!Array.isArray(rows)) throw new Error("Unsupported deletion");
  const row = (rows as Row[]).find((r) => r.id === target);
  if (!row) throw new Error("Record not found");
  return {
    token: createHash("md5").update(JSON.stringify(row)).digest("hex"),
    counts: { [entity]: 1 },
    paths: [row.storage_path, row.attachment_path, row.receipt_path].filter((p): p is string => typeof p === "string"),
  };
}
createServer(async (req, res) => {
  const chunks: Buffer[] = [];
  for await (const chunk of req) chunks.push(Buffer.from(chunk));
  const bytes = Buffer.concat(chunks),
    url = new URL(req.url!, "http://127.0.0.1:4010");
  const send = (value: unknown, status = 200) => {
    res.writeHead(status, { "Content-Type": "application/json" });
    res.end(JSON.stringify(value));
  };
  if (url.pathname === "/__state") return send({ book, commands: [...commands.values()] });
  if (url.pathname === "/__reset") {
    book = fixture();
    commands.clear();
    objects.clear();
    return send({ ok: true });
  }
  if (url.pathname.startsWith("/storage/v1/object/pn-files/")) {
    const path = url.pathname.split("/pn-files/")[1];
    if (req.method === "POST") {
      if (objects.has(path)) return send({ error: "duplicate" }, 409);
      objects.set(path, { bytes, mime: req.headers["content-type"] ?? "" });
      return send({ Key: path });
    }
    const object = objects.get(path);
    if (!object) return send({ error: "missing" }, 404);
    res.writeHead(200, { "Content-Type": object.mime });
    return res.end(object.bytes);
  }
  if (req.method === "DELETE" && url.pathname === "/storage/v1/object/pn-files") {
    for (const path of JSON.parse(bytes.toString()).prefixes) objects.delete(path);
    return send([]);
  }
  const name = url.pathname.split("/").at(-1),
    data = bytes.length ? JSON.parse(bytes.toString()) : {};
  try {
    if (name === "pn_delete_preview") return send(deletePreview(data.p_entity, data.p_id));
    if (name === "pn_delete") {
      const c = data.p_command as Command,
        old = commands.get(c.request_id);
      if (old) {
        if (old.payload !== JSON.stringify(c)) throw new Error("Request already used");
        return send(old.result);
      }
      const entity = c.data.entity_type as keyof typeof book;
      const preview = deletePreview(entity, String(c.data.id));
      if (c.data.confirmation !== "DELETE" || c.data.token !== preview.token) throw new Error("Review deletion again");
      (book[entity] as Row[]) = (book[entity] as Row[]).filter((r) => r.id !== c.data.id);
      book.audit_logs = book.audit_logs.filter((r) => r.entity_id !== c.data.id);
      const result = { paths: preview.paths, counts: preview.counts };
      commands.set(c.request_id, { payload: JSON.stringify(c), result });
      return send(result);
    }
    if (name === "pn_snapshot") return send(book);
    if (name === "pn_execute") {
      const duplicate = commands.has(data.p_command.request_id),
        result = execute(data.p_command);
      if (data.p_command.data.notes === "SIMULATE_UNCERTAIN" && !duplicate)
        return send({ message: "Connection lost after commit" }, 503);
      return send(result);
    }
    if (name === "pn_receipt") return send(book.payments.find((p) => p.id === data.p_id));
    if (name === "pn_audit_detail") return send(book.audit_logs.find((a) => a.id === data.p_id));
    if (name === "pn_audit_page")
      return send(book.audit_logs.filter((a) => !data.p_before_time || a.created_at < data.p_before_time).slice(0, 50));
    send({ code: "PGRST202", message: "missing" }, 404);
  } catch (error) {
    send({ code: "P0001", message: (error as Error).message }, 400);
  }
}).listen(4010, "127.0.0.1", () => console.log("Disposable fixture backend at 127.0.0.1:4010"));
