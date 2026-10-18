"""Run only against a disposable Docker PostgreSQL database, never Supabase."""
from pathlib import Path
import subprocess
import uuid

container = "pn-performance-test"
database = "pn_benchmark_" + uuid.uuid4().hex[:8]
output = Path("docs/performance-audit-2026-10-04/database-benchmark.txt")


def sql(source):
    result = subprocess.run(["docker", "exec", "-i", container, "psql", "-U", "postgres",
                             "-d", database, "-v", "ON_ERROR_STOP=1"],
                            input=source, text=True, capture_output=True, check=True)
    return result.stdout


subprocess.run(["docker", "exec", container, "createdb", "-U", "postgres", database], check=True)
for migration in sorted(Path("supabase/migrations").glob("*.sql"))[:2]:
    sql(migration.read_text())
sql("""
insert into public.tenants(id,full_name) select id,'Synthetic benchmark tenant' from public.flats;
insert into public.tenancies(id,flat_id,tenant_id,monthly_rent_paisa,move_in_date,ledger_start_date,status)
select id,id,id,10000,'2021-01-01','2021-01-01','active' from public.flats;
insert into public.payments(tenancy_id,amount_paisa,payment_date,through_month,receipt_number)
select f.id,10000,m::date,m::date,f.flat_number||'-'||m::date::text from public.flats f,
generate_series('2021-01-01'::date,date_trunc('month',(now() at time zone 'Asia/Dhaka')),interval '1 month') m;
select public.pn_generate();
""")
measurement = """\\timing on
select count(*) as payments from public.payments;
select octet_length(public.pn_snapshot()::text) as snapshot_bytes;
select octet_length(public.pn_snapshot()::text) as snapshot_bytes;
select octet_length(public.pn_snapshot()::text) as snapshot_bytes;
select public.pn_execute(jsonb_build_object('action','expense_save','request_id',gen_random_uuid(),
'data',jsonb_build_object('amount_paisa',100,'expense_date',(now() at time zone 'Asia/Dhaka')::date,
'category','Other','description','Synthetic benchmark expense')))->>'id' is not null as expense_confirmed;
"""
before = sql(measurement)
sql(Path("supabase/migrations/202610040001_performance.sql").read_text())
after = sql(measurement + """
select octet_length(public.pn_sync()::text) as initial_sync_bytes;
select octet_length(public.pn_sync((select max(version) from public.pn_versions))::text) as unchanged_sync_bytes;
select octet_length(public.pn_sync((select max(version) from public.pn_versions))::text) as unchanged_sync_bytes;
""")
output.write_text("Disposable database: " + database + "\nBEFORE\n" + before + "\nAFTER\n" + after)
print(output.read_text())
