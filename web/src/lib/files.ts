import { createHash } from "node:crypto";
import type { Book } from "./types";
import { fileTypes } from "./validation";
export function inspectFile(bytes: Uint8Array, mime: string): string {
  if (!fileTypes[mime] || bytes.length === 0 || bytes.length > 20971520)
    throw new Error("Choose a supported file between 1 byte and 20 MB.");
  const start = Buffer.from(bytes.subarray(0, 12));
  const valid =
    mime === "application/pdf"
      ? start.subarray(0, 5).toString() === "%PDF-"
      : mime === "image/png"
        ? start.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))
        : mime === "image/jpeg"
          ? start[0] === 255 && start[1] === 216 && start[2] === 255
          : mime === "image/webp"
            ? start.subarray(0, 4).toString() === "RIFF" && start.subarray(8, 12).toString() === "WEBP"
            : !bytes.includes(0) && !!new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  if (!valid) throw new Error("File contents do not match the selected format.");
  return createHash("sha256").update(bytes).digest("hex");
}
export function referencedPaths(book: Book): Set<string> {
  return new Set(
    [
      ...book.tenant_documents.map((d) => d.storage_path),
      ...book.tenants.map((t) => t.profile_image_path),
      ...book.payments.map((p) => p.attachment_path),
      ...book.expenses.map((e) => e.receipt_path),
      ...book.maintenance.map((m) => m.receipt_path),
    ].filter((p): p is string => !!p),
  );
}
