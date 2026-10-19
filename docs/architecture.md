# Maintaining the codebase

The repository contains the Android application, an on-demand Next.js workspace in
`web/`, and their shared Supabase backend.
Keep the existing feature packages and shared components; a new module or framework
is not needed for ordinary changes.

## Where code belongs

| Location | Responsibility |
| --- | --- |
| `AppContainer.kt` | Process-owned dependencies, confirmed snapshot caching, and repository wiring |
| `navigation/` | Routes, navigation arguments, app bar, and bottom tabs |
| `feature/home/` | Building dashboard |
| `feature/flats/` | Flat list, details, occupancy and payment-status presentation |
| `feature/tenants/` | Tenant details and tenancy forms |
| `feature/payments/` | Payment selection, forms, details, and ledgers |
| Other `feature/` packages | Their corresponding screens and UI state |
| `feature/book/` | Shared book state/save lifecycle, receipts, and exports |
| `core/ui/` and `core/design/` | Reusable Compose forms, components, and theme |
| `core/util/` | Shared money, date, and financial calculations |
| Other `core/` packages | Security, settings, localization, networking, exports, and reminders |
| `data/model/` | Serializable database records, the `Book` snapshot, and snapshot-owned indexes |
| `data/repository/` | Supabase RPCs, incremental merging, encrypted cache, and file operations |
| `supabase/migrations/` | Database schema, validation, transactions, and access policies |
| `supabase/tests/` | Disposable-database assertions for the SQL contract |
| `scripts/` | Localization checks, release launch checks, and database benchmarks |

`FlatsScreen`/`FlatsViewModel` provide the independent flat-only flow;
`BookFlatsScreen` uses the complete book. Keep both contracts while they have callers.

## How data moves

`MainNavigation` creates `BookViewModel` with functions supplied by `AppContainer`.
The view model may show the encrypted confirmed cache while refreshing the cloud
snapshot. `BookRepository.sync()` uses `pn_sync`; servers without that RPC fall
back to `pn_snapshot` and `pn_execute`. Full and incremental cloud snapshots share
building/index/overflow validation before they become current state.

Saves retain a request UUID and the original payload until confirmation.
`commitBook()` sends the command and receives the confirmed snapshot;
`executeBook()` supports the separate execute/load path. A timeout must not generate
a new UUID or change the pending payload. `BookCache` stores confirmed snapshots,
never pending commands. `BookIndex` belongs to a snapshot; create a new `Book`
rather than mutating its lists.

Database transactions own financial writes, allocation, revision checks, and
idempotency. UI calculations use `BookMath`, integer paisa, and `BuildingDates`.
The serialized snake_case properties are part of the SQL and cache contract;
renaming Kotlin properties requires preserving their serialized names.

## Making a change

1. Read the affected screen, its callers, view model, repository, and SQL action.
2. Keep display logic in the feature and transactional rules in the database.
3. Update callers and fixtures when moving a screen or changing a contract.
4. Add a focused test for changed calculations, validation, retries, or data handling.
5. Run the relevant checks below. Use an emulator for changed UI behavior.

```sh
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
python3 scripts/check-localization.py
```

Unit tests use local fixtures and mocked network responses. Compiling the
instrumentation APK does not execute device tests. With a configured emulator,
run `./gradlew connectedDebugAndroidTest`. Database checks require a disposable
backend; follow [Supabase setup](supabase-setup.md), including the prerequisites
for storage. Apply migrations in filename order and never replay table-creation
migrations against existing tenant data.

## Web workspace

The local Next.js client lives in `web/` with its own package file and lockfile.
See [web setup](../web/README.md) for startup, recovery, and checks. Its server
uses the same snapshot/execute and storage contracts. The database contract
lives in SQL, not in the Android UI. Review authentication and authorization before exposing
real tenant or financial data through a web deployment: the current local
PIN/biometric lock does not authenticate Supabase access. See the existing
[access boundary](supabase-setup.md#access-boundary). Keep operator credentials
and service-role keys out of client bundles.
