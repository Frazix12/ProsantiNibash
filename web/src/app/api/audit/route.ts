import { BackendError, failure, json, rpc } from "@/lib/server";
import { uuid, validateLocal } from "@/lib/validation";
import type { Audit, Book } from "@/lib/types";
export const dynamic = "force-dynamic";
export async function GET(request: Request) {
  try {
    validateLocal(request);
    const params = new URL(request.url).searchParams,
      id = params.get("id"),
      before = params.get("before"),
      time = params.get("time");
    if (
      (id && !uuid(id)) ||
      (before && !uuid(before)) ||
      !!time !== !!before ||
      (time && !Number.isFinite(Date.parse(time)))
    )
      throw new Error("Invalid audit cursor.");
    try {
      return json(
        id
          ? await rpc("pn_audit_detail", { p_id: id })
          : await rpc("pn_audit_page", before ? { p_before_id: before, p_before_time: time } : {}),
      );
    } catch (error) {
      if (!(error instanceof BackendError) || error.code !== "PGRST202") throw error;
      const book = await rpc<Book>("pn_snapshot");
      const rows = book.audit_logs.sort((a, b) => b.created_at.localeCompare(a.created_at) || b.id.localeCompare(a.id));
      return json(
        id
          ? rows.find((a) => a.id === id)
          : rows
              .filter((a: Audit) => !time || a.created_at < time || (a.created_at === time && a.id < before!))
              .slice(0, 50),
      );
    }
  } catch (error) {
    return failure(error);
  }
}
