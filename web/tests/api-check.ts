// Run against the production app configured ONLY for tests/mock-supabase.ts.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { resolve } from "node:path";
import { id } from "./fixtures";
import { today } from "../src/lib/money";
const app = "http://127.0.0.1:3011";
// Own the app process and its environment so these writes cannot reach a real backend.
const server = spawn(
  process.execPath,
  [resolve("node_modules/next/dist/bin/next"), "start", "--hostname", "127.0.0.1", "--port", "3011"],
  {
    env: {
      ...process.env,
      SUPABASE_URL: "http://127.0.0.1:4010",
      SUPABASE_PUBLISHABLE_KEY: "sb_publishable_disposable_fixture",
    },
    stdio: ["ignore", "pipe", "pipe"],
  },
);
try {
  await new Promise<void>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("Test app did not become ready")), 15000);
    server.stdout.on("data", (chunk) => {
      if (chunk.toString().includes("Ready")) {
        clearTimeout(timer);
        resolve();
      }
    });
    server.on("error", (error) => {
      clearTimeout(timer);
      reject(error);
    });
    server.on("exit", (code) => {
      if (code !== null) {
        clearTimeout(timer);
        reject(new Error(`Test app exited: ${code}`));
      }
    });
  });
  async function request(path: string, method = "GET", body?: unknown, origin = app) {
    const response = await fetch(`${app}${path}`, {
      method,
      headers: {
        Origin: origin,
        "x-pn-local": "1",
        ...(body instanceof FormData ? {} : { "Content-Type": "application/json" }),
      },
      body: body === undefined ? undefined : body instanceof FormData ? body : JSON.stringify(body),
    });
    return { status: response.status, data: await response.json() };
  }
  const state = await (await fetch("http://127.0.0.1:4010/__state")).json();
  assert.equal(state.book.building.owner_name, "Example owner", "Refusing tests against a non-fixture backend");
  assert.equal((await request("/api/book")).data.flats.length, 14);
  const localApp = "http://localhost:3011";
  assert.equal((await (await fetch(`${localApp}/api/book`)).json()).flats.length, 14);
  assert.equal((await request("/api/book", "POST", {}, "https://evil.example")).status, 400);
  const command = <T>(action: string, data: T, n: number) => ({ action, data, request_id: id(n) });
  const deposit = command(
    "deposit_save",
    {
      tenancy_id: id(200),
      type: "received",
      amount_paisa: "1234",
      transaction_date: today(),
      description: "API fixture check",
    },
    2000,
  );
  assert.equal((await fetch(`${localApp}/api/book`, {
    method: "POST",
    headers: { Origin: localApp, "x-pn-local": "1", "Content-Type": "application/json" },
    body: JSON.stringify(deposit),
  })).status, 200);
  assert.equal((await request("/api/book", "POST", deposit)).status, 200);
  assert.equal((await request("/api/book", "POST", deposit)).status, 200);
  const uncertain = command(
    "payment_save",
    {
      tenancy_id: id(200),
      amount_paisa: "125",
      payment_date: today(),
      through_month: today(),
      kind: "cash",
      payment_method: "Cash",
      reference: null,
      notes: "SIMULATE_UNCERTAIN",
    },
    2001,
  );
  const interrupted = await request("/api/book", "POST", uncertain);
  assert.equal(interrupted.status, 503);
  assert.equal(interrupted.data.uncertain, true);
  assert.equal((await request("/api/book", "POST", uncertain)).status, 200);
  const stale = await request(
    "/api/book",
    "POST",
    command(
      "expense_save",
      {
        id: id(600),
        revision: "999",
        amount_paisa: "1000",
        expense_date: today(),
        category: "Water",
        description: "Stale check",
        payment_method: "Cash",
        flat_id: null,
        vendor: null,
        notes: null,
      },
      2002,
    ),
  );
  assert.equal(stale.status, 400);
  assert.equal(stale.data.uncertain, false);
  assert.match(stale.data.error, /changed on another device/);
  async function upload(requestId: string, contents: string) {
    const body = new FormData();
    body.set("entity", "tenants");
    body.set("id", id(100));
    body.set("request_id", requestId);
    body.set("file", new Blob([contents], { type: "application/pdf" }), "fixture.pdf");
    const result = await request("/api/files", "POST", body);
    assert.equal(result.status, 200);
    return result.data;
  }
  const original = await upload(id(2100), "%PDF-1.7 original fixture");
  const duplicate = await upload(id(2100), "%PDF-1.7 original fixture");
  assert.equal(duplicate.path, original.path);
  const saved = await request(
    "/api/book",
    "POST",
    command(
      "document_save",
      {
        tenant_id: id(100),
        document_type: "NID",
        storage_path: original.path,
        file_name: "fixture.pdf",
        mime_type: "application/pdf",
        size_bytes: original.size,
      },
      2100,
    ),
  );
  assert.equal(saved.status, 200);
  const doc = saved.data.result;
  assert.equal(
    (await request("/api/files", "DELETE", { path: original.path })).status,
    400,
    "Linked objects must be protected",
  );
  const download = await fetch(`${app}/api/files?path=${encodeURIComponent(original.path)}`);
  assert.equal(download.status, 200);
  assert.equal(await download.text(), "%PDF-1.7 original fixture");
  const replacement = await upload(id(2101), "%PDF-1.7 replacement fixture");
  const staleReplacement = await request(
    "/api/book",
    "POST",
    command(
      "document_save",
      {
        id: doc.id,
        revision: "999",
        tenant_id: id(100),
        document_type: "NID",
        storage_path: replacement.path,
        file_name: "replacement.pdf",
        mime_type: "application/pdf",
        size_bytes: replacement.size,
      },
      2101,
    ),
  );
  assert.equal(staleReplacement.status, 400);
  assert.equal(
    (await request("/api/files", "DELETE", { path: original.path })).status,
    400,
    "Old file survives failed metadata save",
  );
  const replaced = await request(
    "/api/book",
    "POST",
    command(
      "document_save",
      {
        id: doc.id,
        revision: doc.revision,
        tenant_id: id(100),
        document_type: "NID",
        storage_path: replacement.path,
        file_name: "replacement.pdf",
        mime_type: "application/pdf",
        size_bytes: replacement.size,
      },
      2102,
    ),
  );
  assert.equal(replaced.status, 200);
  assert.equal((await request("/api/files", "DELETE", { path: original.path })).status, 200);
  const preview = await request(`/api/delete?entity=tenant_documents&id=${doc.id}`);
  assert.equal(preview.status, 200);
  const deletion = command(
    "permanent_delete",
    {
      entity_type: "tenant_documents",
      id: doc.id,
      token: preview.data.token,
      confirmation: "DELETE",
    },
    2103,
  );
  assert.equal(
    (await request("/api/book", "POST", { ...deletion, data: { ...deletion.data, confirmation: "" } })).status,
    400,
  );
  const erased = await request("/api/book", "POST", deletion);
  assert.equal(erased.status, 200);
  assert.deepEqual(erased.data.result.paths, [replacement.path]);
  assert.deepEqual((await request("/api/book", "POST", deletion)).data, erased.data);
  assert.equal((await request("/api/files", "DELETE", { path: replacement.path })).status, 200);
  assert.equal(
    (await request("/api/book")).data.tenant_documents.some((d: { id: string }) => d.id === doc.id),
    false,
  );
  const final = await (await fetch("http://127.0.0.1:4010/__state")).json();
  assert.equal(
    final.book.deposit_transactions.filter((d: { description: string }) => d.description === "API fixture check")
      .length,
    1,
  );
  assert.equal(
    final.commands.filter((c: { payload: string }) => JSON.parse(c.payload).request_id === id(2001)).length,
    1,
  );
  console.log(
    "API checks passed: snapshot, origin protection, idempotent save, uncertain retry, stale revisions, upload integrity, protected replacement, cleanup, and permanent deletion.",
  );
} finally {
  server.kill("SIGTERM");
}
