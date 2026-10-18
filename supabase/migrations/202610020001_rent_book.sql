begin;
alter table public.tenancies add column settlement_notes text;
alter table public.tenants add column revision bigint not null default 1 check (revision > 0);
create table public.rent_periods (
 id uuid primary key default gen_random_uuid(), tenancy_id uuid not null references public.tenancies,
 period_date date not null, amount_paisa bigint not null check(amount_paisa >= 0),
 kind text not null default 'monthly' check(kind in ('monthly','manual','opening_due','settlement')),
 notes text, revision bigint not null default 1 check(revision > 0), created_at timestamptz not null default clock_timestamp()
);
create unique index rent_month_unique on public.rent_periods(tenancy_id,period_date) where kind='monthly';
create sequence public.receipt_sequence;
create table public.payments (
 id uuid primary key default gen_random_uuid(), tenancy_id uuid not null references public.tenancies,
 amount_paisa bigint not null check(amount_paisa > 0), payment_date date not null, through_month date not null,
 payment_method text not null default 'Cash', reference text, notes text, attachment_path text,
 receipt_number text not null unique, kind text not null default 'cash' check(kind in ('cash','opening_credit','credit_refund')),
 settlement_credit boolean not null default false,
 voided boolean not null default false, revision bigint not null default 1 check(revision > 0),
 receipt_snapshot jsonb not null default '{}', created_at timestamptz not null default clock_timestamp()
);
create table public.payment_allocations (
 payment_id uuid not null references public.payments, rent_period_id uuid not null references public.rent_periods,
 amount_paisa bigint not null check(amount_paisa > 0), primary key(payment_id,rent_period_id)
);
create table public.recurring_expenses (
 id uuid primary key default gen_random_uuid(), category text not null check(length(btrim(category))>0),
 description text not null check(length(btrim(description))>0), amount_paisa bigint not null check(amount_paisa>0),
 next_date date not null, frequency text not null default 'monthly' check(frequency in ('monthly','yearly','weekly','custom')),
 interval_days integer not null default 30 check(interval_days between 1 and 3660), flat_id uuid references public.flats,
 active boolean not null default true, revision bigint not null default 1 check(revision>0)
);
create table public.expenses (
 id uuid primary key default gen_random_uuid(), amount_paisa bigint not null check(amount_paisa>0), expense_date date not null,
 category text not null check(length(btrim(category))>0), description text not null check(length(btrim(description))>0),
 flat_id uuid references public.flats, vendor text, payment_method text, receipt_path text, notes text,
 recurring_expense_id uuid references public.recurring_expenses, voided boolean not null default false,
 revision bigint not null default 1 check(revision>0)
);
create table public.maintenance (
 id uuid primary key default gen_random_uuid(), title text not null check(length(btrim(title))>0), maintenance_date date not null,
 amount_paisa bigint not null default 0 check(amount_paisa>=0), flat_id uuid references public.flats, description text,
 vendor text, receipt_path text, notes text, expense_id uuid unique references public.expenses,
 reminder_date date, voided boolean not null default false, revision bigint not null default 1 check(revision>0),
 check((amount_paisa=0 and expense_id is null) or (amount_paisa>0 and expense_id is not null))
);
create table public.deposit_transactions (
 id uuid primary key default gen_random_uuid(), tenancy_id uuid not null references public.tenancies,
 type text not null check(type in ('received','deduction','refund','adjustment')), amount_paisa bigint not null,
 transaction_date date not null, description text,
 check((type='adjustment' and amount_paisa<>0) or (type<>'adjustment' and amount_paisa>0))
);
create table public.tenant_documents (
 id uuid primary key default gen_random_uuid(), tenant_id uuid not null references public.tenants,
 document_type text not null check(length(btrim(document_type))>0), storage_path text not null check(length(storage_path)>0),
 file_name text not null check(length(file_name)>0), mime_type text not null check(length(mime_type)>0),
 created_at timestamptz not null default now(), size_bytes bigint not null default 0 check(size_bytes between 0 and 20971520),
 revision bigint not null default 1 check(revision>0)
);
create table public.building (
 id integer primary key check(id=1), name text not null default 'Prosanti Nibash' check(length(btrim(name))>0),
 address text not null default '', owner_name text not null default '', owner_phone text not null default '',
 revision bigint not null default 1 check(revision>0)
);
insert into public.building(id) values(1);
create table public.pn_requests(request_id uuid primary key, command jsonb not null, result jsonb not null);

-- Internal helper: a fixed allow-list controls every dynamic identifier and field.
create function public.pn_save(p_table text,p_data jsonb) returns jsonb
language plpgsql set search_path=pg_catalog,public as $$
declare allowed text[]; old_row jsonb; new_row jsonb; record_id uuid; fields text; values_sql text; cleaned jsonb;
begin
 allowed := case p_table
 when 'tenants' then array['full_name','phone','emergency_phone','occupation','permanent_address','profile_image_path','notes']
 when 'tenancies' then array['flat_id','tenant_id','monthly_rent_paisa','move_in_date','ledger_start_date','move_out_date','agreement_start_date','agreement_end_date','status']
 when 'rent_periods' then array['tenancy_id','period_date','amount_paisa','kind','notes']
 when 'payments' then array['tenancy_id','amount_paisa','payment_date','through_month','payment_method','reference','notes','attachment_path','kind','voided','receipt_number','receipt_snapshot','settlement_credit']
 when 'expenses' then array['amount_paisa','expense_date','category','description','flat_id','vendor','payment_method','receipt_path','notes','recurring_expense_id','voided']
 when 'maintenance' then array['title','maintenance_date','amount_paisa','flat_id','description','vendor','receipt_path','notes','expense_id','reminder_date','voided']
 when 'recurring_expenses' then array['category','description','amount_paisa','next_date','frequency','interval_days','flat_id','active']
 when 'tenant_documents' then array['tenant_id','document_type','storage_path','file_name','mime_type','size_bytes']
 else null end;
 if allowed is null then raise exception 'Unsupported record type'; end if;
 record_id := coalesce(nullif(p_data->>'id','')::uuid,gen_random_uuid());
 execute format('select to_jsonb(t) from public.%I t where id=$1 for update',p_table) into old_row using record_id;
 if old_row is not null and coalesce((p_data->>'revision')::bigint,0)<>(old_row->>'revision')::bigint then
  raise exception 'This record changed on another device. Reload and retry.';
 end if;
 select coalesce(jsonb_object_agg(key,case when value='""'::jsonb and key in ('flat_id','recurring_expense_id','agreement_start_date','agreement_end_date','move_out_date','reminder_date') then 'null'::jsonb else value end),'{}') into cleaned from jsonb_each(p_data) where key=any(allowed);
 cleaned := cleaned || jsonb_build_object('id',record_id,'revision',coalesce((old_row->>'revision')::bigint,0)+1);
 if old_row is null then
  select string_agg(format('%I',key),','),string_agg(format('r.%I',key),',') into fields,values_sql from jsonb_each(cleaned);
  execute format('insert into public.%I(%s) select %s from jsonb_populate_record(null::public.%I,$1) r returning to_jsonb(%I.*)',p_table,fields,values_sql,p_table,p_table) into new_row using cleaned;
 else
  select string_agg(format('%I=r.%I',key,key),',') into fields from jsonb_each(cleaned) where key<>'id';
  execute format('update public.%I t set %s from jsonb_populate_record(null::public.%I,$1) r where t.id=r.id returning to_jsonb(t.*)',p_table,fields,p_table) into new_row using cleaned;
 end if;
 insert into public.audit_logs(action,entity_type,entity_id,old_data,new_data) values(case when old_row is null then 'create' else 'update' end,p_table,record_id,old_row,new_row);
 return new_row;
end $$;

-- Rebuild derived allocations after backdated edits. Deposits never enter this ledger.
create function public.pn_allocate(p_tenancy uuid) returns void
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
end $$;

create function public.pn_generate() returns void
language plpgsql set search_path=pg_catalog,public as $$
declare tenancy record;
begin
 for tenancy in select * from public.tenancies loop
  insert into public.rent_periods(tenancy_id,period_date,amount_paisa)
   select tenancy.id,month::date,tenancy.monthly_rent_paisa
   from generate_series(date_trunc('month',tenancy.ledger_start_date),date_trunc('month',least(coalesce(tenancy.move_out_date,(now() at time zone 'Asia/Dhaka')::date),(now() at time zone 'Asia/Dhaka')::date)),interval '1 month') month
   on conflict (tenancy_id,period_date) where kind='monthly' do nothing;
  perform public.pn_allocate(tenancy.id);
 end loop;
end $$;

create function public.pn_snapshot() returns jsonb
language plpgsql security definer set search_path=pg_catalog,public as $$
declare result jsonb:='{}'; table_name text; rows jsonb;
begin
 -- ponytail: one building lock; use per-tenancy locks if this becomes a multi-building service.
 perform pg_advisory_xact_lock(71402026);
 perform public.pn_generate();
 foreach table_name in array array['flats','tenants','tenancies','rent_periods','payments','payment_allocations','expenses','maintenance','deposit_transactions','recurring_expenses','tenant_documents','audit_logs'] loop
  execute format('select coalesce(jsonb_agg(to_jsonb(t)),''[]''::jsonb) from public.%I t',table_name) into rows;
  result:=result||jsonb_build_object(table_name,rows);
 end loop;
 return result||jsonb_build_object('building',(select to_jsonb(b)-'id' from public.building b where id=1));
end $$;
create function public.pn_credit(p_id uuid) returns bigint language sql set search_path=pg_catalog,public as $$
 select coalesce((select sum(case when kind='credit_refund' then -amount_paisa else amount_paisa end) from public.payments where tenancy_id=p_id and not voided),0)
 -coalesce((select sum(a.amount_paisa) from public.payment_allocations a join public.payments p on p.id=a.payment_id where p.tenancy_id=p_id),0)
$$;
create function public.pn_due(p_id uuid) returns bigint language sql set search_path=pg_catalog,public as $$
 select coalesce((select sum(amount_paisa) from public.rent_periods where tenancy_id=p_id),0)
 -coalesce((select sum(a.amount_paisa) from public.payment_allocations a join public.payments p on p.id=a.payment_id where p.tenancy_id=p_id),0)
$$;
create function public.pn_deposit_balance(p_id uuid) returns bigint language sql set search_path=pg_catalog,public as $$
 select coalesce(sum(case when type in ('deduction','refund') then -amount_paisa else amount_paisa end),0)
 from public.deposit_transactions where tenancy_id=p_id
$$;

create function public.pn_payment(p_data jsonb,p_settlement boolean default false) returns jsonb
language plpgsql set search_path=pg_catalog,public as $$
declare result jsonb; original public.payments; tenant uuid; receipt text; document jsonb;
begin
 tenant := (p_data->>'tenancy_id')::uuid;
 if coalesce(p_data->>'kind','cash')='cash' and (p_data->>'payment_date')::date<(select ledger_start_date from public.tenancies where id=tenant) and
  (exists(select 1 from public.rent_periods where tenancy_id=tenant and kind='opening_due') or exists(select 1 from public.payments where tenancy_id=tenant and kind='opening_credit' and payment_date<(select ledger_start_date from public.tenancies where id=tenant) and not settlement_credit)) then
  raise exception 'This history is included in the opening balance. Correct that balance instead.';
 end if;
 select * into original from public.payments where id=nullif(p_data->>'id','')::uuid;
 if original.id is not null then
  if tenant<>original.tenancy_id then raise exception 'A payment cannot change tenancy'; end if;
  if coalesce(p_data->>'kind',original.kind)<>original.kind then raise exception 'A payment cannot change entry type'; end if;
  if original.kind='credit_refund' and (p_data->>'amount_paisa')::bigint>public.pn_credit(tenant)+original.amount_paisa then raise exception 'Refund exceeds available rent credit'; end if;
  p_data:=p_data||jsonb_build_object('receipt_number',original.receipt_number,'receipt_snapshot',original.receipt_snapshot,'voided',original.voided,'settlement_credit',original.settlement_credit);
 else
  if p_data->>'kind'='credit_refund' and (p_data->>'amount_paisa')::bigint>public.pn_credit(tenant) then raise exception 'Refund exceeds available rent credit'; end if;
  receipt:='PN-'||extract(year from (now() at time zone 'Asia/Dhaka'))::text||'-'||lpad(nextval('public.receipt_sequence')::text,6,'0');
  p_data:=(p_data-'receipt_snapshot')||jsonb_build_object('receipt_number',receipt,'receipt_snapshot','{}'::jsonb,'voided',false,'settlement_credit',p_settlement);
 end if;
 result:=public.pn_save('payments',p_data);
 perform public.pn_allocate(tenant);
 if original.id is null then
  document:=result||jsonb_build_object('building',(select to_jsonb(b)-'id' from public.building b where id=1),
   'tenant',(select to_jsonb(t) from public.tenants t join public.tenancies ty on ty.tenant_id=t.id where ty.id=tenant),
   'flat',(select to_jsonb(f) from public.flats f join public.tenancies ty on ty.flat_id=f.id where ty.id=tenant),
   'allocations',(select coalesce(jsonb_agg(jsonb_build_object('period_date',r.period_date,'amount_paisa',a.amount_paisa)),'[]') from public.payment_allocations a join public.rent_periods r on r.id=a.rent_period_id where a.payment_id=(result->>'id')::uuid),
   'remaining_due_paisa',public.pn_due(tenant),'credit_paisa',public.pn_credit(tenant));
  update public.payments set receipt_snapshot=document where id=(result->>'id')::uuid returning to_jsonb(payments.*) into result;
  update public.audit_logs set new_data=result where entity_type='payments' and entity_id=(result->>'id')::uuid and old_data is null;
 end if;
 return result;
end $$;

create function public.pn_tenancy(p_data jsonb) returns jsonb
language plpgsql set search_path=pg_catalog,public as $$
declare original public.tenancies; tenant_data jsonb; tenant_result jsonb; result jsonb; opening_date date;
begin
 select * into original from public.tenancies where id=nullif(p_data->>'id','')::uuid;
 tenant_data:=p_data;
 if original.id is not null then
  tenant_data:=tenant_data||jsonb_build_object('id',original.tenant_id,'revision',coalesce((p_data->>'tenant_revision')::bigint,(select revision from public.tenants where id=original.tenant_id)));
  if p_data ? 'ledger_start_date' and (p_data->>'ledger_start_date')::date<>original.ledger_start_date then raise exception 'Ledger start cannot change after creation'; end if;
  if p_data ? 'flat_id' and (p_data->>'flat_id')::uuid<>original.flat_id then raise exception 'A tenancy cannot move to another flat'; end if;
 else tenant_data:=tenant_data-'id'-'revision'; end if;
 tenant_result:=public.pn_save('tenants',tenant_data);
 result:=public.pn_save('tenancies',p_data||jsonb_build_object('tenant_id',tenant_result->>'id','status',coalesce(original.status,'active'),'move_out_date',original.move_out_date));
 if original.id is null then
  opening_date:=(p_data->>'ledger_start_date')::date-1;
  if coalesce((p_data->>'opening_due_paisa')::bigint,0)<0 or coalesce((p_data->>'opening_credit_paisa')::bigint,0)<0 then raise exception 'Opening amounts must not be negative'; end if;
  if coalesce((p_data->>'opening_due_paisa')::bigint,0)>0 and coalesce((p_data->>'opening_credit_paisa')::bigint,0)>0 then raise exception 'Choose an opening due or opening credit, not both'; end if;
  if coalesce((p_data->>'opening_due_paisa')::bigint,0)>0 then
   perform public.pn_save('rent_periods',jsonb_build_object('tenancy_id',result->>'id','period_date',date_trunc('month',(p_data->>'ledger_start_date')::date)::date-1,'amount_paisa',p_data->'opening_due_paisa','kind','opening_due','notes','Opening balance before ledger start'));
  end if;
  if coalesce((p_data->>'opening_credit_paisa')::bigint,0)>0 then
   perform public.pn_payment(jsonb_build_object('tenancy_id',result->>'id','payment_date',opening_date,'through_month',opening_date,'amount_paisa',p_data->'opening_credit_paisa','kind','opening_credit','notes','Opening balance before ledger start'));
  end if;
 end if;
 return result;
end $$;

create function public.pn_expense(p_data jsonb,p_maintenance boolean default false) returns jsonb
language plpgsql set search_path=pg_catalog,public as $$
declare result jsonb; recurring public.recurring_expenses; original public.expenses;
begin
 select * into original from public.expenses where id=nullif(p_data->>'id','')::uuid;
 if not p_maintenance and exists(select 1 from public.maintenance where expense_id=original.id) then raise exception 'Edit this linked expense through Maintenance'; end if;
 p_data:=(p_data-'voided')||jsonb_build_object('voided',coalesce(original.voided,false));
 if original.id is null and nullif(p_data->>'recurring_expense_id','') is not null then
  select * into strict recurring from public.recurring_expenses where id=(p_data->>'recurring_expense_id')::uuid for update;
  if not recurring.active then raise exception 'Recurring expense is inactive'; end if;
  if recurring.revision is distinct from (p_data->>'recurring_revision')::bigint or recurring.next_date is distinct from (p_data->>'scheduled_date')::date then
   raise exception 'This recurring expense changed or was already paid. Reload before saving.';
  end if;
 end if;
 result:=public.pn_save('expenses',p_data);
 if recurring.id is not null then
  perform public.pn_save('recurring_expenses',to_jsonb(recurring)||jsonb_build_object('next_date',greatest(recurring.next_date,(p_data->>'expense_date')::date)+case recurring.frequency when 'monthly' then interval '1 month' when 'yearly' then interval '1 year' when 'weekly' then interval '7 days' else make_interval(days=>recurring.interval_days) end));
 end if;
 return result;
end $$;
create unique index recurring_expense_paid_once on public.expenses(recurring_expense_id,expense_date) where recurring_expense_id is not null and not voided;

create function public.pn_maintenance(p_data jsonb) returns jsonb
language plpgsql set search_path=pg_catalog,public as $$
declare original public.maintenance; expense public.expenses; saved jsonb; amount bigint;
begin
 select * into original from public.maintenance where id=nullif(p_data->>'id','')::uuid;
 if original.voided then raise exception 'A voided maintenance record cannot be edited'; end if;
 amount:=coalesce((p_data->>'amount_paisa')::bigint,0);
 if original.expense_id is not null then select * into expense from public.expenses where id=original.expense_id; end if;
 if amount>0 then
  saved:=public.pn_expense(jsonb_build_object('id',expense.id,'revision',expense.revision,'amount_paisa',amount,
   'expense_date',p_data->>'maintenance_date','category','Maintenance','description',p_data->>'title',
   'flat_id',p_data->'flat_id','vendor',p_data->'vendor','receipt_path',p_data->'receipt_path','notes',p_data->'notes'),true);
  p_data:=p_data||jsonb_build_object('expense_id',saved->>'id');
 else
  if expense.id is not null then perform public.pn_save('expenses',to_jsonb(expense)||jsonb_build_object('voided',true)); end if;
  p_data:=p_data||jsonb_build_object('expense_id',null);
 end if;
 return public.pn_save('maintenance',(p_data-'voided')||jsonb_build_object('voided',coalesce(original.voided,false)));
end $$;

create function public.pn_deposit(p_data jsonb) returns jsonb
language plpgsql set search_path=pg_catalog,public as $$
declare result jsonb; tenant uuid;
begin
 tenant:=(p_data->>'tenancy_id')::uuid;
 if coalesce(p_data->>'id','')<>'' then raise exception 'Deposits are corrected with an adjustment, preserving history'; end if;
 insert into public.deposit_transactions(tenancy_id,type,amount_paisa,transaction_date,description)
 values(tenant,p_data->>'type',(p_data->>'amount_paisa')::bigint,(p_data->>'transaction_date')::date,p_data->>'description') returning to_jsonb(deposit_transactions.*) into result;
 if public.pn_deposit_balance(tenant)<0 then raise exception 'Deduction or refund exceeds deposit balance'; end if;
 insert into public.audit_logs(action,entity_type,entity_id,new_data) values('create','deposit_transactions',(result->>'id')::uuid,result);
 return result;
end $$;
create function public.pn_move_out(p_data jsonb) returns jsonb
language plpgsql set search_path=pg_catalog,public as $$
declare tenant uuid; tenancy public.tenancies; departure date; through_date date; damage bigint; adjustment bigint; balance bigint; used bigint; credit bigint; period record; result jsonb;
begin
 tenant:=(p_data->>'id')::uuid; departure:=(p_data->>'move_out_date')::date;
 select * into strict tenancy from public.tenancies where id=tenant for update;
 if tenancy.status<>'active' then raise exception 'This tenancy has already moved out'; end if;
 if tenancy.revision<>coalesce((p_data->>'revision')::bigint,0) then raise exception 'This record changed on another device. Reload and retry.'; end if;
 if departure<tenancy.move_in_date or departure>(now() at time zone 'Asia/Dhaka')::date then raise exception 'Choose a move-out date between move-in and today'; end if;
 damage:=coalesce((p_data->>'damage_paisa')::bigint,0); adjustment:=coalesce((p_data->>'adjustment_paisa')::bigint,0);
 if damage<0 then raise exception 'Damage must not be negative'; end if;
 for period in select * from public.rent_periods where tenancy_id=tenant and kind='monthly' and period_date>date_trunc('month',departure)::date loop
  perform public.pn_save('rent_periods',to_jsonb(period)||jsonb_build_object('amount_paisa',0,'notes','Cancelled after confirmed move-out'));
 end loop;
 -- Manual charges survive departure, so settlement must cover their dates as well.
 select greatest(departure,coalesce(max(period_date),departure)) into through_date from public.rent_periods where tenancy_id=tenant and amount_paisa>0;
 if damage+adjustment>0 then
  perform public.pn_save('rent_periods',jsonb_build_object('tenancy_id',tenant,'period_date',departure,'amount_paisa',damage+adjustment,'kind','settlement','notes','Move-out damage and adjustment'));
 elsif damage+adjustment<0 then
  perform public.pn_payment(jsonb_build_object('tenancy_id',tenant,'payment_date',departure,'through_month',through_date,'amount_paisa',-(damage+adjustment),'kind','opening_credit','notes','Move-out adjustment credit'),true);
 end if;
 perform public.pn_allocate(tenant);
 balance:=public.pn_deposit_balance(tenant); used:=least(balance,public.pn_due(tenant));
 if used>0 then
  perform public.pn_deposit(jsonb_build_object('tenancy_id',tenant,'type','deduction','amount_paisa',used,'transaction_date',departure,'description','Applied to move-out rent and damage'));
  perform public.pn_payment(jsonb_build_object('tenancy_id',tenant,'payment_date',departure,'through_month',through_date,'amount_paisa',used,'kind','opening_credit','notes','Deposit applied in settlement'),true);
 end if;
 balance:=public.pn_deposit_balance(tenant);
 if balance>0 then perform public.pn_deposit(jsonb_build_object('tenancy_id',tenant,'type','refund','amount_paisa',balance,'transaction_date',departure,'description','Move-out deposit refund')); end if;
 credit:=public.pn_credit(tenant);
 if credit>0 then perform public.pn_payment(jsonb_build_object('tenancy_id',tenant,'payment_date',departure,'through_month',through_date,'amount_paisa',credit,'kind','credit_refund','notes','Move-out rent credit refund')); end if;
 result:=public.pn_save('tenancies',to_jsonb(tenancy)||jsonb_build_object('status','moved_out','move_out_date',departure,'settlement_notes',p_data->>'notes'));
 return result||jsonb_build_object('remaining_due_paisa',public.pn_due(tenant),'deposit_refund_paisa',balance,'credit_refund_paisa',credit);
end $$;

create function public.pn_building(data jsonb) returns jsonb
language plpgsql set search_path=pg_catalog,public as $$
declare saved public.building; original jsonb; result jsonb;
begin
  select * into saved from public.building where id=1 for update;
  if saved.revision<>coalesce((data->>'revision')::bigint,0) then raise exception 'Building details changed on another device. Reload and retry.'; end if;
  original:=to_jsonb(saved);
  update public.building set name=coalesce(data->>'name',name),address=coalesce(data->>'address',address),owner_name=coalesce(data->>'owner_name',owner_name),owner_phone=coalesce(data->>'owner_phone',owner_phone),revision=revision+1 where id=1 returning to_jsonb(building.*)-'id' into result;
  insert into public.audit_logs(action,entity_type,entity_id,old_data,new_data) values('update','building','00000000-0000-0000-0000-000000000001',original,result);
 return result;
end $$;

create function public.pn_void(action text,data jsonb) returns jsonb
language plpgsql set search_path=pg_catalog,public as $$
declare table_name text; entity uuid; original jsonb; result jsonb;
begin
  table_name:=case action when 'payment_void' then 'payments' when 'expense_void' then 'expenses' else 'maintenance' end;
  entity:=(data->>'id')::uuid;
  execute format('select to_jsonb(t) from public.%I t where id=$1 for update',table_name) into original using entity;
  if original is null then raise exception 'Record not found'; end if;
  if action='expense_void' and exists(select 1 from public.maintenance where expense_id=entity) then raise exception 'Void this linked expense through Maintenance'; end if;
  result:=public.pn_save(table_name,original||jsonb_build_object('revision',data->'revision','voided',true));
  if action='maintenance_void' and original->>'expense_id' is not null then
   select to_jsonb(e) into original from public.expenses e where id=(original->>'expense_id')::uuid;
   perform public.pn_save('expenses',original||jsonb_build_object('voided',true));
  end if;
 return result;
end $$;

create function public.pn_document(action text,data jsonb) returns jsonb
language plpgsql set search_path=pg_catalog,public as $$
declare entity uuid; original jsonb; result jsonb; person public.tenants;
begin
 if action='document_save' then
  if coalesce(data->>'storage_path','') !~ ('^tenants/'||(data->>'tenant_id')||'/[A-Za-z0-9_.-]+$') then raise exception 'Document path does not match this tenant'; end if;
  if data->>'mime_type' not in ('application/pdf','image/jpeg','image/png','image/webp','text/plain') then raise exception 'Unsupported document format'; end if;
  result:=public.pn_save('tenant_documents',data);
  if data->>'document_type'='Profile photo' then
   if data->>'mime_type' not in ('image/jpeg','image/png','image/webp') then raise exception 'A profile photo must be an image'; end if;
   select * into strict person from public.tenants where id=(data->>'tenant_id')::uuid;
   perform public.pn_save('tenants',to_jsonb(person)||jsonb_build_object('profile_image_path',data->>'storage_path'));
  end if;
 else
  entity:=(data->>'id')::uuid;
  select to_jsonb(d) into strict original from public.tenant_documents d where id=entity for update;
  if (original->>'revision')::bigint<>coalesce((data->>'revision')::bigint,0) then raise exception 'This record changed on another device. Reload and retry.'; end if;
  delete from public.tenant_documents where id=entity;
  insert into public.audit_logs(action,entity_type,entity_id,old_data) values('delete','tenant_documents',entity,original);
  select * into strict person from public.tenants where id=(original->>'tenant_id')::uuid;
  if person.profile_image_path=original->>'storage_path' then perform public.pn_save('tenants',to_jsonb(person)||jsonb_build_object('profile_image_path',null)); end if;
  result:=jsonb_build_object('id',entity);
 end if;
 return result;
end $$;

create function public.pn_attachment(data jsonb) returns jsonb
language plpgsql set search_path=pg_catalog,public as $$
declare table_name text:=data->>'entity_type'; original jsonb; result jsonb; field text; path text:=nullif(data->>'storage_path',''); linked public.expenses;
begin
 if table_name not in ('payments','expenses','maintenance') or table_name is null then raise exception 'Unsupported attachment type'; end if;
 if path is not null and path !~ ('^'||table_name||'/'||(data->>'id')||'/[A-Za-z0-9_.-]+$') then raise exception 'Attachment path does not match this record'; end if;
 execute format('select to_jsonb(t) from public.%I t where id=$1',table_name) into original using (data->>'id')::uuid;
 if original is null then raise exception 'Record not found'; end if;
 field:=case table_name when 'payments' then 'attachment_path' else 'receipt_path' end;
 if table_name='expenses' and exists(select 1 from public.maintenance where expense_id=(data->>'id')::uuid) then raise exception 'Attach this receipt through Maintenance'; end if;
 result:=public.pn_save(table_name,original||jsonb_build_object('revision',data->'revision',field,path));
 if table_name='maintenance' and original->>'expense_id' is not null then
  select * into linked from public.expenses where id=(original->>'expense_id')::uuid;
  perform public.pn_save('expenses',to_jsonb(linked)||jsonb_build_object('receipt_path',path));
 end if;
 return result;
end $$;

create function public.pn_dispatch(action text,data jsonb) returns jsonb
language plpgsql set search_path=pg_catalog,public as $$
declare result jsonb; original jsonb; entity uuid; table_name text; saved public.building;
begin
 case action
 when 'tenancy_save' then result:=public.pn_tenancy(data);
 when 'rent_charge' then
  if data->>'kind'='manual' and (data->>'period_date')::date<(select ledger_start_date from public.tenancies where id=(data->>'tenancy_id')::uuid) and
   (exists(select 1 from public.rent_periods where tenancy_id=(data->>'tenancy_id')::uuid and kind='opening_due') or exists(select 1 from public.payments where tenancy_id=(data->>'tenancy_id')::uuid and kind='opening_credit' and payment_date<(select ledger_start_date from public.tenancies where id=(data->>'tenancy_id')::uuid) and not settlement_credit)) then raise exception 'This history is included in the opening balance. Correct that balance instead.'; end if;
  if data->>'kind'='monthly' then data:=data||jsonb_build_object('period_date',date_trunc('month',(data->>'period_date')::date)::date); end if;
  if data ? 'id' then
   select to_jsonb(r) into original from public.rent_periods r where id=nullif(data->>'id','')::uuid;
   if original is not null and (data->>'tenancy_id'<>original->>'tenancy_id' or data->>'kind'<>original->>'kind') then raise exception 'A charge cannot change tenancy or type'; end if;
  end if;
  result:=public.pn_save('rent_periods',data);
 when 'payment_save' then result:=public.pn_payment(data);
 when 'expense_save' then result:=public.pn_expense(data);
 when 'maintenance_save' then result:=public.pn_maintenance(data);
 when 'deposit_save' then result:=public.pn_deposit(data);
 when 'recurring_save' then result:=public.pn_save('recurring_expenses',data);
 when 'document_save','document_delete' then result:=public.pn_document(action,data);
 when 'attachment_save' then result:=public.pn_attachment(data);
 when 'building_save' then result:=public.pn_building(data);
 when 'move_out' then result:=public.pn_move_out(data);
 when 'payment_void','expense_void','maintenance_void' then result:=public.pn_void(action,data);
 else raise exception 'Unsupported action';
 end case;
 return result;
end $$;

create function public.pn_execute(p_command jsonb) returns jsonb
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
 perform public.pn_generate();
 result:=public.pn_dispatch(action,data);
 perform public.pn_generate();
 insert into public.pn_requests values(request,p_command,result);
 return result;
exception when check_violation or not_null_violation or invalid_text_representation or datetime_field_overflow or numeric_value_out_of_range then
 raise exception 'Check the amount, date, and required fields';
when unique_violation then raise exception 'This flat, month, or recurring expense already has a record. Reload before saving.';
when foreign_key_violation or no_data_found then raise exception 'Related record not found. Reload before saving.';
end $$;

-- No direct table writes. Only the two deliberately unauthenticated RPC entry points are exposed.
do $$ declare table_name text; routine record; begin
 foreach table_name in array array['flats','tenants','tenancies','audit_logs','rent_periods','payments','payment_allocations','expenses','maintenance','deposit_transactions','recurring_expenses','tenant_documents','building','pn_requests'] loop
  execute format('alter table public.%I enable row level security',table_name);
  execute format('revoke all on public.%I from public,anon,authenticated',table_name);
 end loop;
 for routine in select p.oid::regprocedure as signature from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname like 'pn\_%' escape '\' loop
  execute format('revoke all on function %s from public,anon,authenticated',routine.signature);
 end loop;
end $$;
revoke all on sequence public.receipt_sequence from public,anon,authenticated;
revoke create on schema public from public,anon,authenticated;
grant execute on function public.pn_snapshot(),public.pn_execute(jsonb) to anon;
-- Foundation reader remains usable while clients migrate to the complete snapshot.
grant select on public.flats to anon;

-- Storage is provided by Supabase, absent in disposable PostgreSQL tests.
do $$ begin
 if to_regclass('storage.buckets') is not null then
  insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
   values('pn-files','pn-files',false,20971520,array['application/pdf','image/jpeg','image/png','image/webp','text/plain']) on conflict(id) do nothing;
  execute $policy$create policy pn_files_read on storage.objects for select to anon using(bucket_id='pn-files' and name ~ '^(tenants|payments|expenses|maintenance)/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/[A-Za-z0-9_.-]+$')$policy$;
  execute $policy$create policy pn_files_upload on storage.objects for insert to anon with check(bucket_id='pn-files' and name ~ '^(tenants|payments|expenses|maintenance)/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/[A-Za-z0-9_.-]+$')$policy$;
  execute $policy$create policy pn_files_delete on storage.objects for delete to anon using(bucket_id='pn-files' and name ~ '^(tenants|payments|expenses|maintenance)/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/[A-Za-z0-9_.-]+$')$policy$;
 end if;
end $$;
commit;
