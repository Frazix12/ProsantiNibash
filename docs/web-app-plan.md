# On-demand web management app

Status: implemented in `web/`; see [setup and verification](../web/README.md).
This document records the agreed scope and implementation boundaries.

## Agreed requirements

- Next.js web application, English only.
- Full management scope of the existing Android app, using the same Supabase data.
- Manually start it when needed and stop it afterward; no always-running web service.
- Preserve the Android app, existing schema, financial rules, and recorded history.

## Operating model

Assume use on the owner's computer only. Bind both development and production
servers explicitly to `127.0.0.1`, not the default network-wide interface. Open
`http://localhost:3000` or `http://127.0.0.1:3000` in the browser. No public deployment, tunnel, background
service, or automatic startup is needed.

Place the application in `web/` with its own package file and lockfile. Leave the
Android project in its current location; no monorepo tooling is needed.

Planned commands, from `web/`:

```sh
npm ci                 # install locked dependencies
npm run dev            # develop locally
npm run build          # build once, and again after code changes
npm start              # ordinary on-demand use of the built app
# Ctrl+C stops the foreground server
```

Both `dev` and `start` scripts will include `--hostname 127.0.0.1`. Internet access
is required for Supabase. Stopping this server does not stop Supabase or Android,
and does not undo saved changes. The browser must show disconnected/error state
rather than imply a save succeeded after shutdown or a connection failure.

Initial proposal: no separate web account system for this computer-only tool.
Use the existing publishable-key RPC contract through server-only configuration
in ignored `web/.env.local`. Never require a service-role key. Enforce the expected
Host/Origin for mutations and protect local requests against cross-site submission;
loopback binding alone is not request authorization. Revisit access controls before
allowing other devices, tunnels, or public hosting. The existing Supabase RPCs
remain accessible under their current backend policy even while the web server
is stopped; this plan does not change that policy.

## Full feature scope

- Dashboard: month selection, rent collected, expected rent, current/older dues,
  expenses, net cash, outstanding tenants, and building occupancy.
- Flats: all 14 permanent units, search/status filters, active tenant, payment
  summary, previous tenancies, and maintenance history. Keep the building structure
  fixed, matching Android.
- Tenants and tenancies: create/edit personal and agreement details, rent, move-in
  date, ledger start, opening due or credit, notes, contact information, and profile
  documents. Preserve existing restrictions on editing an established tenancy.
- Rent ledgers: charges, allocation details, historical charges, monthly-rent
  corrections, opening balances, outstanding balances, and available credit.
- Payments: record, edit, void with confirmation, payment methods/references,
  advance credit and credit refunds supported by the existing contract, attachments,
  and original issuance receipts. Do not regenerate issuance facts from edited data.
- Deposits: received amounts, deductions, refunds, signed adjustments, and history;
  keep deposits separate from rent income.
- Move-out: dated settlement preview, damages/adjustments, deposit/credit use,
  refunds, retained debt, and explicit confirmation.
- Expenses: create, edit, void, category/month/search filters, vendors, notes,
  receipts, and recurring-expense linkage.
- Recurring expenses: create/edit schedules, activate/deactivate, and record payment
  using existing duplicate-payment protection.
- Maintenance: create/edit/void, building or flat association, dates, reminders,
  attachments, and linked expenses without double counting.
- Files: upload, view/download, replace, and delete tenant documents; manage
  supported payment/expense/maintenance attachments and profile images. Keep the
  existing bucket, supported MIME types, 20 MB limit, safe paths, and revisions.
- Reports: monthly/annual financial reports, outstanding list, tenant ledgers,
  spreadsheet-safe CSV download, and printable reports/receipts with browser PDF
  saving. Clearly label changed/voided receipts while retaining issuance contents.
- Search and audit: search records, page through audit history, inspect changes.
- Building: edit name, address, and owner contact information.
- Reminders: show overdue rent, expiring agreements, recurring items, and maintenance
  reminders while open. Android notification permissions, biometrics, and device PIN
  settings remain device-specific. Scheduled web notifications while the server is
  stopped require a separate always-running system and are outside this operating model.

The later approved scope adds permanent deletion of all editable record types and
their audit history. Tenant deletion cascades to tenancies, finances, and files.
A preview and typed DELETE confirmation are required. Fixed flats/building stay
intact. The permanent-delete migration adds the transactional RPC contract.

## Implementation boundaries

Use Next.js App Router and TypeScript. Keep route pages in `web/src/app`, shared
forms/tables in `web/src/components`, and the database contract and calculation
helpers in `web/src/lib`. Isolate Supabase requests in server-only files. Add a
feature folder only when related code needs grouping; avoid a second service layer
or generic CRUD/form framework.

Start with fresh full snapshots through `pn_snapshot`, mutations through
`pn_execute`, and a refresh after confirmed saves. This avoids porting Android's
incremental cache machinery into an occasional-use tool. Use the existing receipt
and paged-audit RPCs for details, with legacy behavior where needed. Never fetch
private tenant data at build time or put it in shared/static caches.

Every mutation keeps its UUID and exact payload until confirmed. Preserve uncertain
requests across page reloads in tab-scoped storage, expose a recovery/retry view,
and never silently discard them or automatically submit them on startup. Clear
confirmed entries. Closing the tab may discard local drafts; warn about dirty or
uncertain forms. Revisions must detect simultaneous Android/web edits and offer
reload/review instead of overwriting newer data.

Reuse database-side transactional rules. Port only required display calculations
from `BookMath`/settlement preview and test parity using existing fixture cases.
Use exact integer paisa and precision-safe JSON handling for PostgreSQL bigints;
do not round money through JavaScript floating-point parsing. Business dates use
Asia/Dhaka regardless of the computer timezone. Validate fields on both client
and server, including IDs, revisions, supported actions, dates, money, file types,
size, and storage paths.

Upload new files before committing metadata. Preserve old files on failure;
cleanup failures must be visible and retryable. Do not blindly retry a timed-out
upload/save with a new UUID. Keep tenant contents, request payloads, and credentials
out of logs.

## Build order and completion checks

1. Create the local Next.js shell, configuration example, server-only RPC adapter,
   typed wire records, exact money/date helpers, retry handling, and shared form UI.
2. Build dashboard, flats, tenants, rent ledger, payments, receipts, deposits, and
   move-out as complete read/write flows.
3. Add expenses, recurring schedules, maintenance, files, and building details.
4. Complete reports, exports, search, audit history, and the reminder overview.
5. Verify with mocked/disposable data, document setup/start/stop, and review parity
   against every supported SQL action and Android flow.

Run focused tests for money/settlement parity, stale revisions, uncertain-save
retries, file replacement failure, input validation, and CSV formula protection.
Run type checking, lint, a production build, and browser checks covering each
management section and validation failures. Confirm shutdown makes the local web
server unavailable and that restarting reloads saved database records. Do not
seed or run destructive checks against real tenant data. Android remains usable
without the web app running.

## References

- [Next.js CLI: hostname binding and dev/build/start](https://nextjs.org/docs/app/api-reference/cli/next)
- [Next.js Node.js server operation](https://nextjs.org/docs/app/getting-started/deploying)
- [Server-only environment variables](https://nextjs.org/docs/app/guides/environment-variables)
- [Existing backend access boundary](supabase-setup.md#access-boundary)
- [Codebase architecture](architecture.md)
