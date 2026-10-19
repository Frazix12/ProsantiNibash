import "server-only";
import { decode } from "./wire";
export class BackendError extends Error {
  constructor(
    message: string,
    public status = 503,
    public code = "",
  ) {
    super(message);
  }
}
export function config() {
  const url = process.env.SUPABASE_URL?.replace(/\/$/, ""),
    key = process.env.SUPABASE_PUBLISHABLE_KEY;
  if (!url || !key)
    throw new BackendError(
      "Configure SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY in web/.env.local, then restart the server.",
      503,
      "CONFIG",
    );
  const parsed = new URL(url);
  if (
    (parsed.protocol !== "https:" &&
      !(parsed.protocol === "http:" && ["127.0.0.1", "localhost"].includes(parsed.hostname))) ||
    parsed.pathname !== "/" ||
    parsed.search ||
    parsed.hash ||
    parsed.username ||
    parsed.password ||
    !key.startsWith("sb_publishable_")
  )
    throw new BackendError("Use a Supabase project root URL and publishable key in web/.env.local.", 503, "CONFIG");
  return { url, key };
}
export async function backend(path: string, init: RequestInit = {}): Promise<Response> {
  const { url, key } = config();
  let response: Response;
  try {
    response = await fetch(`${url}${path}`, {
      ...init,
      cache: "no-store",
      signal: AbortSignal.timeout(30_000),
      headers: { apikey: key, Authorization: `Bearer ${key}`, ...init.headers },
    });
  } catch {
    throw new BackendError("Supabase could not be reached. A save may be unconfirmed; retry the original request.");
  }
  return response;
}
function friendly(message: string): string {
  const patterns = [
    /changed on another device/i,
    /permanently deleted record/i,
    /already paid/i,
    /refund exceeds/i,
    /exceeds deposit/i,
    /opening balance/i,
    /already occupies/i,
    /already used/i,
    /through Maintenance/i,
    /cannot change/i,
    /voided.*cannot/i,
  ];
  return patterns.some((p) => p.test(message))
    ? message.slice(0, 240)
    : "The database rejected this entry. Check its values or reload the record.";
}
export async function rpc<T>(name: string, data: unknown = {}): Promise<T> {
  const response = await backend(`/rest/v1/rpc/${name}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
  const raw = await response.text();
  if (!response.ok) {
    const error = JSON.parse(raw) as { message?: string; code?: string };
    throw new BackendError(
      response.status >= 500 || response.status === 408
        ? "Save was not confirmed. Retry sends the original unchanged entry."
        : friendly(error.message ?? ""),
      response.status,
      error.code,
    );
  }
  return decode(raw) as T;
}
export function json(data: unknown, status = 200): Response {
  return Response.json(data, { status, headers: { "Cache-Control": "no-store" } });
}
export function failure(error: unknown): Response {
  if (error instanceof BackendError)
    return json(
      { error: error.message, uncertain: error.status >= 500 || error.status === 408, code: error.code },
      error.status,
    );
  return json({ error: error instanceof Error ? error.message : "Request failed.", uncertain: false }, 400);
}
