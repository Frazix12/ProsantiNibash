# Prosanti Nibash web workspace

English-only management of the same building book used by Android. The web server
runs only when you start it and listens on `127.0.0.1`; no web deployment is needed.

## First setup

Use a supported Node.js LTS release satisfying Next.js's engine requirement
(Node.js 20.9 or newer), and npm. From the repository root:

```sh
cd web
npm ci
cp .env.example .env.local
```

Edit `.env.local` with your Supabase project root URL and **publishable** key.
Use the same project as Android when you are ready to work with the shared book.
The values stay on the Next.js server; they have no `NEXT_PUBLIC_` prefix. Never
use a service-role key or database password. The web app does not automatically
read Android's `local.properties` or ship any credentials.

The existing database migrations and storage policies must already be installed.
See [Supabase setup](../docs/supabase-setup.md). Apply
[`202610190001_permanent_delete.sql`](../supabase/migrations/202610190001_permanent_delete.sql)
once after the existing migrations to enable permanent deletion. The
performance migration enables dedicated receipts and paged audit history; legacy
RPCs remain supported. An empty/misconfigured backend shows an explicit error.

```sh
npm run build
npm start
```

Open **http://localhost:3000** or **http://127.0.0.1:3000**. Both local addresses
support viewing and saving. Press **Ctrl+C** in the terminal
to stop. On later visits, run `npm start` again. Rebuild after changing code.
Changing `.env.local` needs a server restart. Do not enable automatic startup.

For development, use `npm run dev` instead. Both commands bind explicitly to the
loopback interface. To change ports: `npm start -- --port 3001`.

## What is included

- Dashboard, fixed flat list/details, occupancy, current and previous tenants.
- Tenant/agreement editing, opening balances, historical and monthly charges.
- Payments, corrections, voids, credit refunds, original receipts, and rent ledgers.
- Deposit transactions and move-out settlement review.
- Expenses, recurring schedules/payments, and linked maintenance expenses.
- Tenant documents/profile photos and financial/maintenance attachments.
- Monthly/annual reports, full-book CSV, printed reports/receipts/ledgers with
  browser **Save as PDF**, search, audit inspection, and reminder overview.
- Building name, address, and owner contact details.

Use **Refresh** to see edits made from Android or another tab. A stale revision is
rejected instead of overwriting it; close the stale form, refresh, and reopen it
to review current values. The database owns allocation, revision validation,
settlement, and audit transactions.

**Delete** previews affected records and files, then requires typing `DELETE`.
Permanent deletion removes the records and their audit history. Deleting a tenant
also removes their tenancies, financial records, and files. Deleting a recurring
schedule removes its expenses; linked maintenance and expenses are removed
together. Fixed flats and the building remain. Voiding remains available.

File cleanup runs after the database transaction; keep the app open until it
finishes, or use the cleanup retry. Minimal request IDs, deletion cleanup receipts,
and skipped-month markers remain to prevent retries or rent generation from
restoring erased records. Database/storage backups and other devices' cached data
are outside this operation; refresh Android to synchronize deletions.

## Interrupted saves and files

An unconfirmed save keeps its original UUID and exact payload in tab-scoped
`sessionStorage`. Use **Retry original save**; do not enter the same payment again.
Recovery survives reloads in the same tab. No save is automatically resubmitted
on startup. Closing the tab or clearing browser storage can lose recovery data;
check audit history first if you are unsure whether a payment was accepted.
Unsaved form changes warn before leaving but are not a permanent draft store.

Uploads are staged before metadata changes. If interrupted, the workspace offers
**Resume upload** (reselect the exact original file), **Finish document save** for
confirmed uploads, or discard/cleanup. Replacements retain old objects until the
new metadata save is confirmed. Old-file cleanup is separate and retryable; it
refuses to delete objects still linked to any current record. **View** opens a
sandboxed browser preview; **Download** saves the original.

Money uses exact integer paisa, not floating-point arithmetic. PostgreSQL integer
JSON tokens are decoded as strings before they can be rounded. Business dates
use Asia/Dhaka. Files are limited to 20 MB and PDF/JPEG/PNG/WebP/UTF-8 text, with
content-signature and integrity checks.

## Access and shutdown

This is a local, single-computer tool without separate web accounts. Same-origin,
Host, custom-header, and frame protections guard the local API; they do not add
Supabase user authentication. The existing publishable-key backend policy remains
unchanged and accessible while the web server is off. Do not expose this app via
a tunnel, LAN binding, or public host without first redesigning access control.
Other software/users on your computer share its local trust boundary.

Stopping the web server does not stop Supabase or Android and does not undo saves.
The web app needs an internet connection for Supabase. Reminders are an on-screen
overview; no web notifications run while it is stopped. Android PIN, biometrics,
language preferences, and scheduled notifications stay device-specific.

## Verification

```sh
npm test
npm run typecheck
npm run lint
npm run build
```

Unit tests cover exact money/JSON, calculations and settlement, validation, local
request protection, CSV formula escaping, and file integrity. The production
build does not fetch tenant data or require backend credentials.

For disposable API/browser checks, run these in separate terminals from `web/`:

```sh
./node_modules/.bin/tsx tests/mock-supabase.ts
SUPABASE_URL=http://127.0.0.1:4010 SUPABASE_PUBLISHABLE_KEY=sb_publishable_disposable_fixture npm start
./node_modules/.bin/tsx tests/api-check.ts
```

The fixture backend stores synthetic data in memory and loses it when stopped.
`api-check.ts` starts its own loopback app on port 3011 with fixture-only backend
configuration, and refuses to run unless the dedicated fixture backend responds.
It checks idempotency, ambiguous confirmations, stale edits, upload retries, safe
replacement, downloads, and cleanup against the actual Next.js routes. Stop both
processes after checks; never run write tests against real tenant data.

Source map: `src/app/api` handles local HTTP and server-only Supabase requests;
`src/components` owns management screens/forms and pending-request recovery;
`src/lib` owns typed wire records, validation, calculations, and field definitions.

The interface uses shadcn/ui (Base UI, Nova), Tailwind CSS v4, and Lucide icons.
`src/components/ui/` contains the installed component source; `ui.tsx` composes
shared business cards, tables, and dialogs. `app-sidebar.tsx` owns navigation.
Theme tokens, responsive layouts, and print styles live in `src/app/globals.css`.
Keep control sizes and variants in the shared components. Use `npx shadcn@latest
add @shadcn/<component>` for additions, and review diffs before replacing edited
components. Fonts use the system stack with no external font requests.

References: [Next.js local server](https://nextjs.org/docs/app/api-reference/cli/next),
[environment variables](https://nextjs.org/docs/app/guides/environment-variables),
[Supabase storage](https://supabase.com/docs/guides/storage/serving/downloads).
