begin;

create table public.flats (
    id uuid primary key default gen_random_uuid(),
    floor integer not null check (floor between 1 and 7),
    flat_number text not null check (flat_number in (floor::text || 'A', floor::text || 'B')),
    default_rent_paisa bigint not null default 0 check (default_rent_paisa >= 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (floor, flat_number)
);
comment on column public.flats.default_rent_paisa is 'Zero means unset, never an actual rent charge.';

create table public.tenants (
    id uuid primary key default gen_random_uuid(),
    full_name text not null check (length(btrim(full_name)) > 0),
    phone text,
    emergency_phone text,
    occupation text,
    permanent_address text,
    profile_image_path text,
    notes text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.tenancies (
    id uuid primary key default gen_random_uuid(),
    flat_id uuid not null references public.flats(id),
    tenant_id uuid not null references public.tenants(id),
    monthly_rent_paisa bigint not null check (monthly_rent_paisa > 0),
    move_in_date date not null,
    move_out_date date,
    ledger_start_date date not null check (ledger_start_date >= move_in_date),
    agreement_start_date date,
    agreement_end_date date,
    status text not null default 'active' check (status in ('active', 'moved_out')),
    revision bigint not null default 1 check (revision > 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check ((status = 'active' and move_out_date is null) or
           (status = 'moved_out' and move_out_date is not null)),
    check (move_out_date >= move_in_date),
    check (agreement_end_date >= agreement_start_date)
);
create unique index tenancies_one_active_per_flat
    on public.tenancies(flat_id) where status = 'active';
comment on column public.tenancies.revision is 'Later mutation functions must compare and increment revisions for conflicting edits.';

create table public.audit_logs (
    id uuid primary key default gen_random_uuid(),
    action text not null check (length(btrim(action)) > 0),
    entity_type text not null check (length(btrim(entity_type)) > 0),
    entity_id uuid not null,
    old_data jsonb,
    new_data jsonb,
    created_at timestamptz not null default now()
);
comment on table public.audit_logs is 'Later mutation functions write audit records atomically; no owner identity exists without authentication.';

insert into public.flats(id, floor, flat_number, default_rent_paisa)
select md5('prosanti-nibash-flat-' || floor || suffix)::uuid,
       floor, floor::text || suffix, 0
from generate_series(1, 7) as floors(floor)
cross join (values ('A'), ('B')) as sides(suffix)
on conflict (floor, flat_number) do nothing;

alter table public.flats enable row level security;
alter table public.tenants enable row level security;
alter table public.tenancies enable row level security;
alter table public.audit_logs enable row level security;

revoke all on table public.flats, public.tenants, public.tenancies, public.audit_logs
    from public, anon, authenticated;
grant usage on schema public to anon;
grant select on table public.flats to anon;
create policy flats_public_read on public.flats for select to anon using (true);

commit;
