-- Run only against a disposable database; every test change is rolled back.
begin;

-- A missing migration must fail before any other assertions.
do $$
begin
    if (select count(*) from public.flats) <> 14 then
        raise exception 'Expected exactly 14 flats';
    end if;
    if exists (
        select floor, floor::text || suffix as flat_number
        from generate_series(1, 7) floors(floor)
        cross join (values ('A'), ('B')) sides(suffix)
        except select floor, flat_number from public.flats
    ) then
        raise exception 'Expected A/B flats on floors 1–7';
    end if;
end $$;

-- Specific constraint errors prove invalid input is rejected.
do $$
begin
    begin
        insert into public.flats(floor, flat_number) values (8, '8A');
        raise exception 'Invalid floor accepted';
    exception when check_violation then null; end;
    begin
        insert into public.flats(floor, flat_number) values (1, '2A');
        raise exception 'Invalid flat name accepted';
    exception when check_violation then null; end;
    begin
        update public.flats set default_rent_paisa = -1 where flat_number = '1A';
        raise exception 'Negative default rent accepted';
    exception when check_violation then null; end;
    begin
        insert into public.tenants(full_name) values ('  ');
        raise exception 'Empty tenant name accepted';
    exception when check_violation then null; end;
end $$;

update public.flats set default_rent_paisa = 1500000 where flat_number = '1A';
insert into public.flats(id, floor, flat_number, default_rent_paisa)
select md5('prosanti-nibash-flat-' || floor || suffix)::uuid,
       floor, floor::text || suffix, 0
from generate_series(1, 7) floors(floor)
cross join (values ('A'), ('B')) sides(suffix)
on conflict (floor, flat_number) do nothing;
do $$
begin
    if (select count(*) from public.flats) <> 14 or
       (select default_rent_paisa from public.flats where flat_number = '1A') <> 1500000 then
        raise exception 'Repeated seed changed existing rent or flat count';
    end if;
end $$;

-- Real inserts exercise the unique index and tenancy date/rent constraints.
do $$
declare
    test_tenant uuid;
    test_flat uuid;
    test_tenancy uuid;
begin
    insert into public.tenants(full_name) values ('SQL test tenant') returning id into test_tenant;
    select id into test_flat from public.flats where flat_number = '1A';
    insert into public.tenancies(flat_id, tenant_id, monthly_rent_paisa, move_in_date, ledger_start_date)
    values (test_flat, test_tenant, 1500000, '2026-01-01', '2026-02-01') returning id into test_tenancy;
    begin
        insert into public.tenancies(flat_id, tenant_id, monthly_rent_paisa, move_in_date, ledger_start_date)
        values (test_flat, test_tenant, 1500000, '2026-03-01', '2026-03-01');
        raise exception 'Duplicate active tenancy accepted';
    exception when unique_violation then null; end;
    insert into public.tenancies(flat_id, tenant_id, monthly_rent_paisa, move_in_date,
                                 ledger_start_date, move_out_date, status)
    values (test_flat, test_tenant, 1000000, '2025-01-01', '2025-01-01', '2025-12-31', 'moved_out');
    begin
        update public.tenancies set monthly_rent_paisa = 0 where id = test_tenancy;
        raise exception 'Zero monthly rent accepted';
    exception when check_violation then null; end;
    begin
        update public.tenancies set status = 'moved_out' where id = test_tenancy;
        raise exception 'Moved-out tenancy without date accepted';
    exception when check_violation then null; end;
    begin
        update public.tenancies set move_out_date = '2026-03-01' where id = test_tenancy;
        raise exception 'Active tenancy with move-out date accepted';
    exception when check_violation then null; end;
    begin
        update public.tenancies set status = 'moved_out', move_out_date = '2025-12-31' where id = test_tenancy;
        raise exception 'Reversed move dates accepted';
    exception when check_violation then null; end;
    begin
        update public.tenancies set ledger_start_date = '2025-12-31' where id = test_tenancy;
        raise exception 'Ledger before move-in accepted';
    exception when check_violation then null; end;
    begin
        update public.tenancies set agreement_start_date = '2026-01-02',
            agreement_end_date = '2026-01-01' where id = test_tenancy;
        raise exception 'Reversed agreement dates accepted';
    exception when check_violation then null; end;
    begin
        update public.tenancies set revision = 0 where id = test_tenancy;
        raise exception 'Nonpositive revision accepted';
    exception when check_violation then null; end;
end $$;

-- App grants and RLS must remain restrictive even on a Supabase-style database.
do $$
declare
    table_name text;
begin
    foreach table_name in array array['flats', 'tenants', 'tenancies', 'audit_logs'] loop
        if not (select relrowsecurity from pg_class
                where oid = ('public.' || table_name)::regclass) then
            raise exception 'RLS disabled for %', table_name;
        end if;
        if has_table_privilege('authenticated', 'public.' || table_name, 'SELECT') or
           has_table_privilege('authenticated', 'public.' || table_name, 'INSERT') or
           has_table_privilege('authenticated', 'public.' || table_name, 'UPDATE') or
           has_table_privilege('authenticated', 'public.' || table_name, 'DELETE') then
            raise exception 'Authenticated access unexpectedly granted to %', table_name;
        end if;
    end loop;
end $$;

-- The same anonymous role used by the app can read only the public flat list.
set local role anon;
do $$
begin
    if (select count(*) from public.flats) <> 14 then
        raise exception 'Anonymous flat SELECT failed';
    end if;
    begin
        insert into public.flats(floor, flat_number) values (1, '1A');
        raise exception 'Anonymous flat INSERT allowed';
    exception when insufficient_privilege then null; end;
    begin
        update public.flats set default_rent_paisa = 1;
        raise exception 'Anonymous flat UPDATE allowed';
    exception when insufficient_privilege then null; end;
    begin
        delete from public.flats;
        raise exception 'Anonymous flat DELETE allowed';
    exception when insufficient_privilege then null; end;
    begin
        perform * from public.tenants;
        raise exception 'Anonymous tenant SELECT allowed';
    exception when insufficient_privilege then null; end;
    begin
        perform * from public.tenancies;
        raise exception 'Anonymous tenancy SELECT allowed';
    exception when insufficient_privilege then null; end;
    begin
        perform * from public.audit_logs;
        raise exception 'Anonymous audit SELECT allowed';
    exception when insufficient_privilege then null; end;
end $$;
reset role;
rollback;
