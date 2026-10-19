# Supabase development setup

The foundation migration seeds 14 fixed flats. The rent-book migration adds financial records, transactional RPCs, and the private `pn-files` storage bucket.

## Apply the development migration

1. Create or select a **development** Supabase project. Do not use a tenant-data production project for schema tests.
2. In the development project's SQL editor, run the complete contents of `supabase/migrations/202610010001_foundation.sql` once. Supabase already provides `anon` and `authenticated` roles. The migration runs in a transaction.
3. Verify `public.flats` contains 1A/1B through 7A/7B. All default rents start at zero, meaning unset; zero must never create a rent charge.
4. If you need to repeat the seed, run only its `insert into public.flats ... on conflict ... do nothing` statement. It preserves any rent already configured. The table-creation migration is not intended to run twice.

Alternatively, with `psql` installed and a development connection URL supplied locally:

```sh
psql "$PN_DEV_DATABASE_URL" -v ON_ERROR_STOP=1 -f supabase/migrations/202610010001_foundation.sql
```

The connection URL is an operator credential: keep it out of source, APK configuration, and logs.

## Configure the Android build

Copy the project URL and **publishable** key from the development project's connection/API settings into ignored `local.properties` (preserve your existing `sdk.dir`):

```properties
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_YOUR_PUBLIC_KEY
```

Gradle properties with the same names are also supported. Supply a project root HTTPS URL and a current `sb_publishable_` key. Do not use legacy JWT keys or `sb_secret_` keys. Never bundle a service-role key, database password, connection URL, or other privileged credential in the APK.

Both values are required for cloud access. Blank values still permit building and launching the app; Flats displays a configuration error after local unlock until they are supplied. Rebuild after changing build configuration.

## Run disposable database checks

`supabase/tests/foundation.sql` uses PostgreSQL `DO` assertions inside a transaction and rolls every test change back. Run it against a disposable PostgreSQL 17 database, not cloud tenant data. For ordinary local PostgreSQL, provision the Supabase-like roles once as administrator:

```sql
create role anon;
create role authenticated;
grant usage on schema public to anon, authenticated;
```

Then run:

```sh
psql "$PN_TEST_DATABASE_URL" -v ON_ERROR_STOP=1 -f supabase/migrations/202610010001_foundation.sql
psql "$PN_TEST_DATABASE_URL" -v ON_ERROR_STOP=1 -f supabase/tests/foundation.sql
```

The checks verify the exact building seed, unchanged configured rent after repeat seeding, constraints on tenants/tenancies, active-tenancy uniqueness, allowed historical tenancy, and anonymous read/mutation permissions. The administrator running the checks must be able to `SET ROLE anon`. Dispose of the test database when finished.

## Access boundary

There is no cloud authentication. Local PIN/biometrics protect the device interface; they do not authorize remote access. Anyone with the project URL and publishable key can read the publicly exposed flat list, including its default rent. APK distribution to two owners does not change this limitation; the owner accepted it in the approved design.

RLS is enabled on every application table. Direct anonymous financial and personal-table access is denied; the foundation flat list remains readable. The accepted no-auth application boundary exposes `pn_snapshot()` and `pn_execute(p_command jsonb)` to `anon`. These functions intentionally permit reading and changing the shared book without identifying an owner. Other internal helpers, table mutations, and sequence access are revoked from app roles. Each external function is a security-definer function with a fixed search path and explicit validation.

`pn-files` is private, capped at 20 MB per file, and permits PDF, JPEG, PNG, WebP, and plain text. Anonymous upload/read/delete policies constrain paths to `tenants|payments|expenses|maintenance/{UUID}/{safe filename}`. These path restrictions organize accepted shared access; they do not authenticate an owner. No anonymous object update policy exists: replacements use a new object followed by metadata confirmation.

The grant/policy boundary follows [Supabase's RLS guidance](https://supabase.com/docs/guides/database/postgres/row-level-security); key handling follows [Supabase's API key guidance](https://supabase.com/docs/guides/getting-started/api-keys).

## Deployment status

This guide describes configuring a separate development environment. It does not provide a shared project URL or credentials. Do not point a public build at a real tenant-data project. Keep deployment status, database contents, and credentials in private operational records, not in public repository documentation.

The migrations are intended for a newly created, isolated project after review of the access model. Do not rerun table-creation migrations against an existing database.

Each device can enable reminders under More → Reminders and grant notification permission. WorkManager targets approximately 9:00 AM Asia/Dhaka; Android power saving and connectivity may delay delivery.

## Remaining sections migration

After the foundation is already installed, apply **once**:

```sh
psql "$PN_DEV_DATABASE_URL" -v ON_ERROR_STOP=1 -f supabase/migrations/202610020001_rent_book.sql
```

The file is transactional. Do not run either table-creation migration twice. No fake tenant/payment records are required for setup. `select public.pn_snapshot();` must return 14 flats and empty financial arrays on a fresh building.

For local verification, apply both migrations in order to disposable PostgreSQL 17, then run `supabase/tests/rent_book.sql`. Storage policy assertions run when Supabase's storage schema or an equivalent disposable stub is present. The business assertions roll back all data and audit changes. They cover opening history, oldest-first/partial payments, retry payload conflicts, stale edits, immutable receipt snapshots, backdated recalculation, refundable advance credit, signed deposit adjustments, atomic failed saves, recurring payments, one maintenance expense, document/profile metadata, move-out refunds, retained debt, anonymous RPC access, and denied direct writes.

Every mutation needs `{action, request_id, data}`. Keep the same UUID and payload when retrying a timed-out save. Reusing it with different data is rejected. Existing records require their current revision; a stale edit is rejected before the transaction can commit. Receipt numbers and issuance snapshots survive corrections and backdated changes. Monthly generation runs on snapshot/save and preserves explicitly corrected past rents. Detailed entries before a tenancy's ledger start are rejected when they would duplicate an opening balance.

All monetary values are integer paisa. Deposit adjustments are signed; ordinary charges/payments/expenses are nonnegative or positive as constrained by their table. Move-out uses `id`, `revision`, `move_out_date`, optional nonnegative `damage_paisa`, and optional signed `adjustment_paisa`; it applies credit and available deposit, refunds remaining balances, closes the tenancy, and retains any unpaid debt. Generated monthly charges after a backdated departure are cancelled with audit history; manual historical charges remain.

Local verification on 2026-10-02 passed both ordinary and Supabase-style default-grant databases, plus simultaneous duplicate payment submissions and simultaneous stale corrections using independent PostgreSQL sessions. Live application is handled separately after local verification.

## Permanent deletion migration

After the foundation, rent-book, and `202610040001_performance.sql` migrations,
apply `supabase/migrations/202610190001_permanent_delete.sql` **once** in the
Supabase SQL editor (complete file), or with `psql -v ON_ERROR_STOP=1 -f`.
It preserves existing rows and adds preview/deletion RPCs and operational markers.
These RPCs use the existing accepted anonymous access boundary: a publishable-key
client can permanently delete records. Keep the web server on loopback.

Permanent deletion removes affected audit and old retry payloads. Tenant deletion
also removes related finances and document metadata; the web app then removes
unlinked storage objects with retryable cleanup. Minimal request identifiers and
cleanup receipts prevent replay; skipped-month markers stop deleted rent charges
from regenerating. This does not erase backups or offline device caches.

For disposable verification, apply all migrations in filename order, then run
`supabase/tests/permanent_delete.sql` alongside the existing SQL suites. Tests
roll back fixtures and cover cascade deletion, stale previews, required
confirmation, erased retry rejection, reallocation, and Android sync tombstones.
The migration has not been applied to the shared backend by this code change.
