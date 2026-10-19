begin;
-- Operational markers contain no financial rows or audit payloads.
create table public.pn_erased_requests(request_id uuid primary key);
create table public.pn_delete_receipts(request_id uuid primary key,command_hash text not null,result jsonb not null);
create table public.pn_skipped_rent(tenancy_id uuid not null references public.tenancies on delete cascade,period_date date not null,primary key(tenancy_id,period_date));
alter table public.pn_erased_requests enable row level security;
alter table public.pn_delete_receipts enable row level security;
alter table public.pn_skipped_rent enable row level security;
revoke all on public.pn_erased_requests,public.pn_delete_receipts,public.pn_skipped_rent from public,anon,authenticated;

-- Deleting a generated charge must survive the next month-generation pass.
create or replace function public.pn_generate_for(p_id uuid) returns void
language plpgsql set search_path=pg_catalog,public as $$
declare tenancy public.tenancies; inserted integer;
begin
 select * into strict tenancy from public.tenancies where id=p_id;
 insert into public.rent_periods(tenancy_id,period_date,amount_paisa)
 select tenancy.id,month::date,tenancy.monthly_rent_paisa
 from generate_series(date_trunc('month',tenancy.ledger_start_date),date_trunc('month',least(coalesce(tenancy.move_out_date,(now() at time zone 'Asia/Dhaka')::date),(now() at time zone 'Asia/Dhaka')::date)),interval '1 month') month
 where not exists(select 1 from public.pn_skipped_rent s where s.tenancy_id=p_id and s.period_date=month::date)
 on conflict (tenancy_id,period_date) where kind='monthly' do nothing;
 get diagnostics inserted=row_count;
 if inserted>0 then perform public.pn_allocate(p_id); end if;
end $$;

create function public.pn_delete_targets(p_kind text,entity uuid) returns jsonb
language plpgsql set search_path=pg_catalog,public as $$
declare v_people uuid[]; v_owners uuid[]; v_payments uuid[]; v_charges uuid[]; v_deposits uuid[]; v_documents uuid[]; v_schedules uuid[]; v_expenses uuid[]; v_repairs uuid[];
begin
 if p_kind not in('tenants','tenancies','payments','rent_periods','deposit_transactions','tenant_documents','recurring_expenses','expenses','maintenance') or p_kind is null then raise exception 'This record type cannot be permanently deleted';end if;
 v_people:=case when p_kind='tenants' then array[entity] else '{}'::uuid[] end;
 select coalesce(array_agg(id),'{}') into v_owners from public.tenancies where tenant_id=any(v_people) or (p_kind='tenancies' and id=entity);
 select coalesce(array_agg(id),'{}') into v_payments from public.payments where tenancy_id=any(v_owners) or (p_kind='payments' and id=entity);
 select coalesce(array_agg(id),'{}') into v_charges from public.rent_periods where tenancy_id=any(v_owners) or (p_kind='rent_periods' and id=entity);
 select coalesce(array_agg(id),'{}') into v_deposits from public.deposit_transactions where tenancy_id=any(v_owners) or (p_kind='deposit_transactions' and id=entity);
 select coalesce(array_agg(id),'{}') into v_documents from public.tenant_documents where tenant_id=any(v_people) or (p_kind='tenant_documents' and id=entity);
 select coalesce(array_agg(id),'{}') into v_schedules from public.recurring_expenses where p_kind='recurring_expenses' and id=entity;
 select coalesce(array_agg(id),'{}') into v_expenses from public.expenses where recurring_expense_id=any(v_schedules) or (p_kind='expenses' and id=entity);
 select coalesce(array_agg(id),'{}') into v_repairs from public.maintenance where expense_id=any(v_expenses) or (p_kind='maintenance' and id=entity);
 select coalesce(array_agg(distinct id),'{}') into v_expenses from public.expenses where id=any(v_expenses) or id in(select expense_id from public.maintenance where id=any(v_repairs));
 return jsonb_build_object('tenants',v_people,'tenancies',v_owners,'payments',v_payments,'rent_periods',v_charges,'deposit_transactions',v_deposits,'tenant_documents',v_documents,'recurring_expenses',v_schedules,'expenses',v_expenses,'maintenance',v_repairs);
end $$;

create function public.pn_delete_plan(kind text,entity uuid) returns jsonb
language plpgsql set search_path=pg_catalog,public as $$
declare targets jsonb; content jsonb:='{}'; counts jsonb:='{}'; name text; ids jsonb; rows jsonb; paths jsonb;
begin
 targets:=public.pn_delete_targets(kind,entity);
 for name,ids in select * from jsonb_each(targets) loop
  execute format('select coalesce(jsonb_agg(to_jsonb(r) order by id),''[]''::jsonb) from public.%I r where id in(select value::uuid from jsonb_array_elements_text($1))',name) into rows using ids;
  content:=content||jsonb_build_object(name,rows);counts:=counts||jsonb_build_object(name,jsonb_array_length(rows));
 end loop;
 if not exists(select 1 from jsonb_array_elements(content->kind) r where r->>'id'=entity::text) then raise exception 'Record not found. Reload before deleting.';end if;
 select coalesce(jsonb_agg(to_jsonb(a) order by payment_id,rent_period_id),'[]') into rows from public.payment_allocations a
 where payment_id in(select value::uuid from jsonb_array_elements_text(targets->'payments')) or rent_period_id in(select value::uuid from jsonb_array_elements_text(targets->'rent_periods'));
 content:=content||jsonb_build_object('payment_allocations',rows);
 counts:=counts||jsonb_build_object('payment_allocations',jsonb_array_length(rows));
 select coalesce(jsonb_agg(distinct v.value),'[]') into paths from jsonb_each(content) t cross join lateral jsonb_array_elements(t.value) r cross join lateral jsonb_each(r) v
 where v.key in('storage_path','profile_image_path','attachment_path','receipt_path') and jsonb_typeof(v.value)='string' and v.value<>'""'::jsonb;
 select coalesce(jsonb_agg(to_jsonb(t) order by id),'[]') into rows from public.tenants t where profile_image_path in(select value from jsonb_array_elements_text(paths));
 content:=content||jsonb_build_object('profile_owners',rows);
 return jsonb_build_object('targets',targets,'counts',counts,'paths',paths,'token',md5(content::text));
end $$;

create function public.pn_delete_preview(p_entity text,p_id uuid) returns jsonb
language plpgsql security definer set search_path=pg_catalog,public as $$
begin
 perform pg_advisory_xact_lock_shared(71402026);
 return public.pn_delete_plan(p_entity,p_id)-'targets';
end $$;

create function public.pn_delete_mentions(data jsonb,ids uuid[],paths text[]) returns boolean
language sql set search_path=pg_catalog,public as $$
 select coalesce(data::text ~ array_to_string(ids,'|'),false) or exists(select 1 from unnest(paths) p where strpos(data::text,p)>0)
$$;

create function public.pn_delete(p_command jsonb) returns jsonb
language plpgsql security definer set search_path=pg_catalog,public as $$
declare request uuid; digest text; prior public.pn_delete_receipts; data jsonb; plan jsonb; targets jsonb; name text; ids jsonb; all_ids uuid[]; paths text[]; owners uuid[]; owner uuid; result jsonb;
begin
 perform pg_advisory_xact_lock(71402026);
 data:=p_command->'data';request:=(p_command->>'request_id')::uuid;digest:=md5(p_command::text);
 if request is null or p_command->>'action' is distinct from 'permanent_delete' or data->>'confirmation' is distinct from 'DELETE' or jsonb_typeof(data) is distinct from 'object' then raise exception 'Confirm permanent deletion';end if;
 select * into prior from public.pn_delete_receipts where request_id=request;
 if found then if prior.command_hash<>digest then raise exception 'This request identifier was already used for different data';end if;return prior.result;end if;
 if exists(select 1 from public.pn_requests where request_id=request) or exists(select 1 from public.pn_erased_requests where request_id=request) then raise exception 'This request identifier was already used for different data';end if;
 plan:=public.pn_delete_plan(data->>'entity_type',(data->>'id')::uuid);targets:=plan->'targets';
 if data->>'token' is distinct from plan->>'token' then raise exception 'Related records changed on another device. Review deletion again.';end if;
 select coalesce(array_agg(v.value::uuid),'{}') into all_ids from jsonb_each(targets) t cross join lateral jsonb_array_elements_text(t.value) v;
 select coalesce(array_agg(value),'{}') into paths from jsonb_array_elements_text(plan->'paths');
 select coalesce(array_agg(distinct tenancy_id),'{}') into owners from (
  select tenancy_id from public.payments where id in(select value::uuid from jsonb_array_elements_text(targets->'payments')) union
  select tenancy_id from public.rent_periods where id in(select value::uuid from jsonb_array_elements_text(targets->'rent_periods'))
 ) t;
 insert into public.pn_skipped_rent select tenancy_id,period_date from public.rent_periods where id=any(all_ids) and kind='monthly' on conflict do nothing;
 delete from public.payment_allocations where payment_id=any(all_ids) or rent_period_id=any(all_ids);
 update public.tenants set profile_image_path=null,revision=revision+1 where profile_image_path=any(paths) and not(id=any(all_ids));
 foreach name in array array['tenant_documents','payments','rent_periods','deposit_transactions','maintenance','expenses','recurring_expenses','tenancies','tenants'] loop
  ids:=targets->name;
  execute format('delete from public.%I where id in(select value::uuid from jsonb_array_elements_text($1))',name) using ids;
 end loop;
 foreach owner in array owners loop
  if exists(select 1 from public.tenancies where id=owner) then perform public.pn_allocate(owner);else perform public.pn_mark('payment_allocations',owner,false);end if;
 end loop;
 delete from public.audit_logs where entity_id=any(all_ids) or public.pn_delete_mentions(old_data,all_ids,paths) or public.pn_delete_mentions(new_data,all_ids,paths);
 insert into public.pn_erased_requests select r.request_id from public.pn_requests r where public.pn_delete_mentions(r.command,all_ids,paths) or public.pn_delete_mentions(r.result,all_ids,paths) on conflict do nothing;
 delete from public.pn_requests where request_id in(select request_id from public.pn_erased_requests);
 result:=jsonb_build_object('paths',plan->'paths','counts',plan->'counts');
 insert into public.pn_delete_receipts values(request,digest,result);
 return result;
end $$;

-- Preserve normal saves while preventing old uncertain commands from resurrecting erased data.
create or replace function public.pn_execute(p_command jsonb) returns jsonb
language plpgsql security definer set search_path=pg_catalog,public as $$
declare action text; data jsonb; request uuid; prior public.pn_requests; result jsonb;
begin
 perform pg_advisory_xact_lock(71402026);
 if jsonb_typeof(p_command)<>'object' or jsonb_typeof(p_command->'data')<>'object' then raise exception 'Invalid command';end if;
 action:=p_command->>'action';data:=p_command->'data';request:=(p_command->>'request_id')::uuid;
 if request is null then raise exception 'A retry request identifier is required';end if;
 if exists(select 1 from public.pn_delete_receipts where request_id=request) then raise exception 'This request identifier was already used for different data';end if;
 if exists(select 1 from public.pn_erased_requests where request_id=request) then raise exception 'This request belongs to a permanently deleted record. Reload before saving.';end if;
 select * into prior from public.pn_requests where request_id=request;
 if prior.request_id is not null then if prior.command<>p_command then raise exception 'This request identifier was already used for different data';end if;return prior.result;end if;
 if action in('tenancy_save','rent_charge','payment_save','payment_void','move_out') then perform public.pn_ensure_month();end if;
 result:=public.pn_dispatch(action,data);
 if action='tenancy_save' then perform public.pn_generate_for((result->>'id')::uuid);
 elsif action in('rent_charge','payment_void') then perform public.pn_allocate((result->>'tenancy_id')::uuid);end if;
 insert into public.pn_requests values(request,p_command,result);return result;
exception when check_violation or not_null_violation or invalid_text_representation or datetime_field_overflow or numeric_value_out_of_range then raise exception 'Check the amount, date, and required fields';
when unique_violation then raise exception 'This flat, month, or recurring expense already has a record. Reload before saving.';
when foreign_key_violation or no_data_found then raise exception 'Related record not found. Reload before saving.';
end $$;
revoke all on function public.pn_delete_targets(text,uuid),public.pn_delete_plan(text,uuid),public.pn_delete_mentions(jsonb,uuid[],text[]),public.pn_delete_preview(text,uuid),public.pn_delete(jsonb) from public,anon,authenticated;
grant execute on function public.pn_delete_preview(text,uuid),public.pn_delete(jsonb) to anon;
commit;
