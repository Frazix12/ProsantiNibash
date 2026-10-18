begin;
-- Forward-only optimization: existing RPCs and financial contracts remain compatible.
create index payments_tenancy_date on public.payments(tenancy_id,payment_date,id);
create index rent_periods_tenancy_date on public.rent_periods(tenancy_id,period_date,id);
create index allocations_period on public.payment_allocations(rent_period_id) include(amount_paisa);
create index deposits_tenancy on public.deposit_transactions(tenancy_id);
create index audit_entity on public.audit_logs(entity_type,entity_id);
create index audit_page on public.audit_logs(created_at desc,id desc);

-- One latest version per entity, including tombstones; history is not duplicated here.
create sequence public.pn_version_sequence;
create table public.pn_versions(table_name text not null,entity_id uuid not null,
 version bigint not null,deleted boolean not null default false,primary key(table_name,entity_id));
create index pn_versions_since on public.pn_versions(version);
create table public.pn_generation_state(id integer primary key check(id=1),month date not null);
create function public.pn_mark(p_table text,p_id uuid,p_deleted boolean) returns void
language sql set search_path=pg_catalog,public as $$
 insert into public.pn_versions values(p_table,p_id,nextval('public.pn_version_sequence'),p_deleted)
 on conflict(table_name,entity_id) do update set version=excluded.version,deleted=excluded.deleted
$$;
create function public.pn_track() returns trigger
language plpgsql set search_path=pg_catalog,public as $$
begin
 perform public.pn_mark(tg_table_name,case when tg_table_name='building' then '00000000-0000-0000-0000-000000000001'::uuid
 else (case when tg_op='DELETE' then to_jsonb(old) else to_jsonb(new) end->>'id')::uuid end,tg_op='DELETE');
 return null;
end $$;
do $$ declare name text; begin
 foreach name in array array['flats','tenants','tenancies','rent_periods','payments','expenses','maintenance','deposit_transactions','recurring_expenses','tenant_documents','building'] loop
  execute format('create trigger pn_track after insert or update or delete on public.%I for each row execute function public.pn_track()',name);
  if name='building' then perform public.pn_mark(name,'00000000-0000-0000-0000-000000000001',false);
  else execute format('insert into public.pn_versions select %L,id,nextval(''public.pn_version_sequence''),false from public.%I',name,name); end if;
 end loop;
 insert into public.pn_versions select 'payment_allocations',id,nextval('public.pn_version_sequence'),false from public.tenancies;
end $$;
create or replace function public.pn_allocate(p_tenancy uuid) returns void
language plpgsql set search_path=pg_catalog,public as $$
declare pay record; charge record; available bigint; due bigint; used bigint; refund_total bigint;
begin
 select coalesce(sum(amount_paisa),0) into refund_total from public.payments where tenancy_id=p_tenancy and kind='credit_refund' and not voided;
 if refund_total>(select coalesce(sum(amount_paisa),0) from public.payments where tenancy_id=p_tenancy and kind<>'credit_refund' and not voided) then raise exception 'Refund exceeds available rent credit'; end if;
 delete from public.payment_allocations a using public.payments p where a.payment_id=p.id and p.tenancy_id=p_tenancy;
 for pay in select p.*,coalesce(sum(amount_paisa) over(order by payment_date desc,id desc rows between unbounded preceding and 1 preceding),0) as newer_cash
  from public.payments p where tenancy_id=p_tenancy and not voided and kind<>'credit_refund' order by payment_date,id loop
  -- Refunded credit is reserved before future charges, so it cannot become spendable again.
  available := pay.amount_paisa-greatest(0,least(pay.amount_paisa,refund_total-pay.newer_cash));
  for charge in select * from public.rent_periods where tenancy_id=p_tenancy and
    (period_date<date_trunc('month',pay.through_month)+interval '1 month' or created_at>pay.created_at) order by period_date,id loop
   select charge.amount_paisa-coalesce(sum(amount_paisa),0) into due from public.payment_allocations where rent_period_id=charge.id;
   used := least(available,due);
   if used>0 then insert into public.payment_allocations values(pay.id,charge.id,used); available:=available-used; end if;
   exit when available=0;
  end loop;
 end loop;
 perform public.pn_mark('payment_allocations',p_tenancy,false);
end $$;


-- Generate missing months only; settled histories no longer rebuild on every read.
create function public.pn_generate_for(p_id uuid) returns void
language plpgsql set search_path=pg_catalog,public as $$
declare tenancy public.tenancies; inserted integer;
begin
 select * into strict tenancy from public.tenancies where id=p_id;
 insert into public.rent_periods(tenancy_id,period_date,amount_paisa)
 select tenancy.id,month::date,tenancy.monthly_rent_paisa
 from generate_series(date_trunc('month',tenancy.ledger_start_date),date_trunc('month',least(coalesce(tenancy.move_out_date,(now() at time zone 'Asia/Dhaka')::date),(now() at time zone 'Asia/Dhaka')::date)),interval '1 month') month
 on conflict (tenancy_id,period_date) where kind='monthly' do nothing;
 get diagnostics inserted=row_count;
 if inserted>0 then perform public.pn_allocate(p_id); end if;
end $$;
create or replace function public.pn_generate() returns void
language plpgsql set search_path=pg_catalog,public as $$
declare tenancy record;
begin
 for tenancy in select id from public.tenancies loop perform public.pn_generate_for(tenancy.id); end loop;
end $$;
create function public.pn_ensure_month() returns void
language plpgsql set search_path=pg_catalog,public as $$
declare current_month date:=date_trunc('month',(now() at time zone 'Asia/Dhaka'))::date;
begin
 if exists(select 1 from public.pn_generation_state where id=1 and month=current_month) then return; end if;
 perform pg_advisory_xact_lock(71402026);
 if exists(select 1 from public.pn_generation_state where id=1 and month=current_month) then return; end if;
 perform public.pn_generate();
 insert into public.pn_generation_state values(1,current_month) on conflict(id) do update set month=excluded.month;
end $$;
create or replace function public.pn_snapshot() returns jsonb
language plpgsql security definer set search_path=pg_catalog,public as $$
declare result jsonb:='{}'; table_name text; rows jsonb;
begin
 -- ponytail: one building lock; use per-tenancy locks if this becomes a multi-building service.
 perform pg_advisory_xact_lock(71402026);
 perform public.pn_ensure_month();
 foreach table_name in array array['flats','tenants','tenancies','rent_periods','payments','payment_allocations','expenses','maintenance','deposit_transactions','recurring_expenses','tenant_documents','audit_logs'] loop
  execute format('select coalesce(jsonb_agg(to_jsonb(t)),''[]''::jsonb) from public.%I t',table_name) into rows;
  result:=result||jsonb_build_object(table_name,rows);
 end loop;
 return result||jsonb_build_object('building',(select to_jsonb(b)-'id' from public.building b where id=1));
end $$;
create or replace function public.pn_execute(p_command jsonb) returns jsonb
language plpgsql security definer set search_path=pg_catalog,public as $$
declare action text; data jsonb; request uuid; prior public.pn_requests; result jsonb; original jsonb; entity uuid; table_name text; linked uuid; saved public.building;
begin
 perform pg_advisory_xact_lock(71402026);
 if jsonb_typeof(p_command)<>'object' or jsonb_typeof(p_command->'data')<>'object' then raise exception 'Invalid command'; end if;
 action:=p_command->>'action'; data:=p_command->'data'; request:=(p_command->>'request_id')::uuid;
 if request is null then raise exception 'A retry request identifier is required'; end if;
 select * into prior from public.pn_requests where request_id=request;
 if prior.request_id is not null then
  if prior.command<>p_command then raise exception 'This request identifier was already used for different data'; end if;
  return prior.result;
 end if;
 if action in ('tenancy_save','rent_charge','payment_save','payment_void','move_out') then
  perform public.pn_ensure_month();
 end if;
 result:=public.pn_dispatch(action,data);
 if action='tenancy_save' then perform public.pn_generate_for((result->>'id')::uuid);
 elsif action='rent_charge' then perform public.pn_allocate((result->>'tenancy_id')::uuid);
 elsif action='payment_void' then perform public.pn_allocate((result->>'tenancy_id')::uuid);
 end if;
 insert into public.pn_requests values(request,p_command,result);
 return result;
exception when check_violation or not_null_violation or invalid_text_representation or datetime_field_overflow or numeric_value_out_of_range then
 raise exception 'Check the amount, date, and required fields';
when unique_violation then raise exception 'This flat, month, or recurring expense already has a record. Reload before saving.';
when foreign_key_violation or no_data_found then raise exception 'Related record not found. Reload before saving.';
end $$;


-- Atomic delta read/commit. Shared read locks allow both owners to read concurrently.
-- Allocation deltas replace the complete allocation group for each affected tenancy.
create function public.pn_sync(p_after bigint default 0,p_command jsonb default null) returns jsonb
language plpgsql security definer set search_path=pg_catalog,public as $$
declare current_version bigint; full_read boolean; name text; rows jsonb; deleted jsonb:='{}'; book jsonb:='{}'; owners jsonb;
begin
 if p_after is null or p_after<0 then raise exception 'Invalid sync version'; end if;
 if p_command is not null then perform public.pn_execute(p_command); end if;
 perform public.pn_ensure_month();
 perform pg_advisory_xact_lock_shared(71402026);
 select coalesce(max(version),0) into current_version from public.pn_versions;
 full_read:=p_after=0 or p_after>current_version;
 foreach name in array array['flats','tenants','tenancies','rent_periods','payments','expenses','maintenance','deposit_transactions','recurring_expenses','tenant_documents'] loop
  execute format('select coalesce(jsonb_agg(to_jsonb(t)%s),''[]''::jsonb) from public.%I t where $1 or exists(select 1 from public.pn_versions v where v.table_name=$3 and v.entity_id=t.id and v.version>$2)',
   case when name='payments' then ' - ''receipt_snapshot''' else '' end,name) into rows using full_read,p_after,name;
  book:=book||jsonb_build_object(name,rows);
  select coalesce(jsonb_agg(entity_id),'[]'::jsonb) into rows from public.pn_versions v where v.table_name=name and v.deleted and v.version>p_after;
  deleted:=deleted||jsonb_build_object(name,rows);
 end loop;
 select coalesce(jsonb_agg(entity_id),'[]'::jsonb) into owners from public.pn_versions where table_name='payment_allocations' and (full_read or version>p_after);
 select coalesce(jsonb_agg(to_jsonb(a)),'[]'::jsonb) into rows from public.payment_allocations a join public.payments p on p.id=a.payment_id
 where full_read or exists(select 1 from public.pn_versions v where v.table_name='payment_allocations' and v.entity_id=p.tenancy_id and v.version>p_after);
 book:=book||jsonb_build_object('payment_allocations',rows);
 if full_read or exists(select 1 from public.pn_versions where table_name='building' and version>p_after) then
  book:=book||jsonb_build_object('building',(select to_jsonb(b)-'id' from public.building b where id=1));
 end if;
 return jsonb_build_object('version',current_version,'full',full_read,'book',book,'deleted',deleted,'allocation_tenancies',owners);
end $$;
create function public.pn_audit_page(p_before_time timestamptz default null,p_before_id uuid default null,p_limit integer default 50) returns jsonb
language plpgsql security definer set search_path=pg_catalog,public as $$
begin
 if p_limit not between 1 and 100 or (p_before_time is null)<>(p_before_id is null) then raise exception 'Invalid audit page'; end if;
 return (select coalesce(jsonb_agg(to_jsonb(t) order by created_at desc,id desc),'[]'::jsonb) from
  (select id,action,entity_type,entity_id,created_at from public.audit_logs
   where p_before_time is null or (created_at,id)<(p_before_time,p_before_id)
   order by created_at desc,id desc limit p_limit) t);
end $$;
create function public.pn_audit_detail(p_id uuid) returns jsonb
language sql security definer set search_path=pg_catalog,public as $$ select to_jsonb(a) from public.audit_logs a where id=p_id $$;
create function public.pn_receipt(p_id uuid) returns jsonb
language sql security definer set search_path=pg_catalog,public as $$ select to_jsonb(p) from public.payments p where id=p_id $$;
-- Reminder reads transfer only aggregate balances and actionable dates, never full history.
create function public.pn_reminders() returns jsonb
language plpgsql security definer set search_path=pg_catalog,public as $$
declare today date:=(now() at time zone 'Asia/Dhaka')::date; old_due bigint; due bigint;
begin
 perform public.pn_ensure_month();
 perform pg_advisory_xact_lock_shared(71402026);
 select coalesce(sum(r.amount_paisa-coalesce(a.paid,0)) filter(where r.period_date<date_trunc('month',today)),0),
 coalesce(sum(r.amount_paisa-coalesce(a.paid,0)) filter(where r.period_date<date_trunc('month',today)+interval '1 month'),0)
 into old_due,due from public.rent_periods r left join (select rent_period_id,sum(amount_paisa) paid from public.payment_allocations group by rent_period_id) a on a.rent_period_id=r.id;
 return jsonb_build_object('old_due',old_due,'due',due,
 'agreement',exists(select 1 from public.tenancies where status='active' and agreement_end_date between today and today+30),
 'recurring',exists(select 1 from public.recurring_expenses where active and next_date<=today),
 'maintenance',exists(select 1 from public.maintenance where not voided and reminder_date<=today));
end $$;
-- Keep the accepted no-auth RPC boundary; internal bookkeeping stays inaccessible.
alter table public.pn_versions enable row level security;
alter table public.pn_generation_state enable row level security;
revoke all on public.pn_versions,public.pn_generation_state from public,anon,authenticated;
revoke all on sequence public.pn_version_sequence from public,anon,authenticated;
do $$ declare routine record; begin
 for routine in select p.oid::regprocedure signature from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname like 'pn\_%' escape '\' loop
  execute format('revoke all on function %s from public,anon,authenticated',routine.signature);
 end loop;
end $$;
grant execute on function public.pn_snapshot(),public.pn_execute(jsonb),public.pn_sync(bigint,jsonb),public.pn_audit_page(timestamptz,uuid,integer),public.pn_audit_detail(uuid),public.pn_receipt(uuid),public.pn_reminders() to anon;
-- Notify PostgREST only after all definitions are committed.
notify pgrst,'reload schema';
commit;
