import { backend, BackendError, failure, json, rpc } from "@/lib/server";
import { fileTypes, safePath, uuid, validateLocal } from "@/lib/validation";
import { inspectFile, referencedPaths } from "@/lib/files";
import { createHash } from "node:crypto";
import type { Book } from "@/lib/types";
export const dynamic = "force-dynamic";
const storage = (path: string) => `/storage/v1/object/pn-files/${path}`;
export async function POST(request: Request) {
  try {
    validateLocal(request, true);
    if (Number(request.headers.get("content-length") ?? "0") > 21000000) throw new Error("File exceeds 20 MB.");
    const form = await request.formData(),
      file = form.get("file"),
      entity = String(form.get("entity")),
      id = form.get("id"),
      requestId = form.get("request_id");
    if (
      !(file instanceof File) ||
      !["tenants", "payments", "expenses", "maintenance"].includes(entity) ||
      !uuid(id) ||
      !uuid(requestId)
    )
      throw new Error("Invalid upload request.");
    const bytes = new Uint8Array(await file.arrayBuffer()),
      digest = inspectFile(bytes, file.type);
    const path = `${entity}/${id}/${requestId}-${digest}.${fileTypes[file.type]}`;
    const book = await rpc<Book>("pn_snapshot");
    const records = book[entity as "tenants" | "payments" | "expenses" | "maintenance"];
    if (!records.some((row) => row.id === id)) throw new Error("The record no longer exists. Reload before uploading.");
    const response = await backend(storage(path), {
      method: "POST",
      headers: { "Content-Type": file.type, "x-upsert": "false" },
      body: bytes,
    });
    if (!response.ok) {
      const existing = await backend(storage(path));
      if (
        !existing.ok ||
        createHash("sha256")
          .update(new Uint8Array(await existing.arrayBuffer()))
          .digest("hex") !== digest
      )
        throw new BackendError("Upload was not confirmed. Retry with the same file.");
    }
    return json({
      path,
      mime: file.type,
      name: file.name.replace(/[\r\n]/g, " ").slice(0, 200),
      size: String(bytes.length),
    });
  } catch (error) {
    return failure(error);
  }
}
export async function GET(request: Request) {
  try {
    validateLocal(request);
    const path = new URL(request.url).searchParams.get("path");
    if (!safePath(path) || !referencedPaths(await rpc<Book>("pn_snapshot")).has(path))
      throw new Error("File is not linked to a current record.");
    const response = await backend(storage(path));
    if (!response.ok) throw new BackendError("File could not be downloaded.");
    const bytes = new Uint8Array(await response.arrayBuffer());
    const hash = path
      .split("/")
      .at(-1)!
      .match(/-([a-f0-9]{64})\./)?.[1];
    if (hash && createHash("sha256").update(bytes).digest("hex") !== hash)
      throw new Error("Downloaded file failed its integrity check.");
    const mime = response.headers.get("content-type")?.split(";")[0] ?? "application/octet-stream";
    return new Response(bytes, {
      headers: {
        "Content-Type": mime,
        "Content-Disposition": `${new URL(request.url).searchParams.get("view") === "1" ? "inline" : "attachment"}; filename="${path.split("/").at(-1)}"`,
        "Cache-Control": "no-store",
        "X-Content-Type-Options": "nosniff",
        "Content-Security-Policy": "sandbox; default-src 'none'",
      },
    });
  } catch (error) {
    return failure(error);
  }
}
export async function DELETE(request: Request) {
  try {
    validateLocal(request, true);
    const { path } = await request.json();
    if (!safePath(path)) throw new Error("Invalid cleanup path.");
    if (referencedPaths(await rpc<Book>("pn_snapshot")).has(path))
      throw new Error("This file is still linked to a record and cannot be removed.");
    const response = await backend("/storage/v1/object/pn-files", {
      method: "DELETE",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ prefixes: [path] }),
    });
    if (!response.ok) throw new BackendError("Record saved, but the old file could not be removed. Retry cleanup.");
    return json({ removed: true });
  } catch (error) {
    return failure(error);
  }
}
