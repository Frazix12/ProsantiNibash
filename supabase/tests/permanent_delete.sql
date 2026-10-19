-- Disposable database only. Every fixture and deletion is rolled back.
begin;
create function pg_temp.expect(ok boolean,message text) returns void language plpgsql as $$ begin if ok is distinct from true then raise exception '%',message; end if; end $$;
select pg_temp.expect(to_regprocedure('public.pn_delete(jsonb)') is not null,'Permanent-delete RPC is missing');
select pg_temp.expect(has_function_privilege('anon','public.pn_delete(jsonb)','execute') and not has_function_privilege('anon','public.pn_delete_plan(text,uuid)','execute'),'Only the validated deletion RPC may be exposed');
select pg_temp.expect(not has_table_privilege('anon','public.pn_delete_receipts','select') and not has_table_privilege('anon','public.pn_erased_requests','insert'),'Operational markers must remain private');
create function pg_temp.run(action text,data jsonb) returns jsonb language sql as $$ select public.pn_execute(jsonb_build_object('action',action,'request_id',gen_random_uuid(),'data',data)) $$;
create function pg_temp.erase(kind text,entity uuid,request uuid default gen_random_uuid()) returns jsonb language sql as $$
 select public.pn_delete(jsonb_build_object('action','permanent_delete','request_id',request,'data',jsonb_build_object('entity_type',kind,'id',entity,'token',public.pn_delete_preview(kind,entity)->>'token','confirmation','DELETE')))
$$;
do $$
declare old_command jsonb;ty jsonb; other jsonb; payment jsonb; second jsonb; preview jsonb; command jsonb; result jsonb; replay jsonb;
 request uuid:=gen_random_uuid(); version bigint; tenant uuid; person uuid; month date:=date_trunc('month',(now() at time zone 'Asia/Dhaka'))::date; charge uuid; doc jsonb; schedule jsonb; expense jsonb; repair jsonb;
begin
 ty:=pg_temp.run('tenancy_save',jsonb_build_object('flat_id',(select id from public.flats where flat_number='1A'),'full_name','Delete fixture','monthly_rent_paisa',10000,'move_in_date',month,'ledger_start_date',month));
 tenant:=(ty->>'id')::uuid;person:=(ty->>'tenant_id')::uuid;
 other:=pg_temp.run('tenancy_save',jsonb_build_object('flat_id',(select id from public.flats where flat_number='1B'),'full_name','Preserved fixture','monthly_rent_paisa',20000,'move_in_date',month,'ledger_start_date',month));
 payment:=pg_temp.run('payment_save',jsonb_build_object('tenancy_id',tenant,'amount_paisa',3000,'payment_date',month,'through_month',month));
 second:=pg_temp.run('payment_save',jsonb_build_object('tenancy_id',tenant,'amount_paisa',500,'payment_date',month,'through_month',month));
 select r.command into old_command from public.pn_requests r where r.result->>'id'=payment->>'id' order by request_id limit 1;
 preview:=public.pn_delete_preview('payments',(payment->>'id')::uuid);
 command:=jsonb_build_object('action','permanent_delete','request_id',request,'data',jsonb_build_object('entity_type','payments','id',payment->>'id','token',preview->>'token','confirmation','DELETE'));
 payment:=pg_temp.run('payment_save',payment||jsonb_build_object('amount_paisa',2000));
 begin perform public.pn_delete(command);raise exception 'Stale delete accepted';exception when raise_exception then if sqlerrm='Stale delete accepted' then raise;end if;end;
 perform pg_temp.expect(exists(select 1 from public.payments where id=(payment->>'id')::uuid),'Stale preview must preserve payment');
 begin perform public.pn_delete(command #- '{data,confirmation}');raise exception 'Unconfirmed deletion accepted';exception when raise_exception then if sqlerrm='Unconfirmed deletion accepted' then raise;end if;end;
 version:=(public.pn_sync()->>'version')::bigint;
 preview:=public.pn_delete_preview('payments',(payment->>'id')::uuid);
 command:=jsonb_set(command,'{data,token}',preview->'token');
 result:=public.pn_delete(command);replay:=public.pn_delete(command);
 perform pg_temp.expect(result=replay,'Lost confirmation retry must return the same deletion result');
 perform pg_temp.expect(not exists(select 1 from public.payments where id=(payment->>'id')::uuid),'Payment must be permanently removed');
 begin perform public.pn_execute(old_command);raise exception 'Erased request resurrected payment';exception when raise_exception then if sqlerrm='Erased request resurrected payment' then raise;end if;end;
 perform pg_temp.expect(public.pn_due(tenant)=9500,'Remaining payments must be reallocated');
 perform pg_temp.expect(not exists(select 1 from public.audit_logs where entity_id=(payment->>'id')::uuid),'Deleted payment audit must be removed');
 perform pg_temp.expect(not exists(select 1 from public.pn_requests r where r.result::text like '%'||(payment->>'id')||'%'),'Old retry snapshots must not preserve the deleted payment');
 perform pg_temp.expect(public.pn_sync(version)->'deleted'->'payments' @> jsonb_build_array(payment->>'id'),'Android must receive payment tombstone');
 perform pg_temp.run('deposit_save',jsonb_build_object('tenancy_id',tenant,'type','received','amount_paisa',5000,'transaction_date',month));
 doc:=pg_temp.run('document_save',jsonb_build_object('tenant_id',person,'document_type','Profile photo','storage_path','tenants/'||person||'/photo.png','file_name','photo.png','mime_type','image/png','size_bytes',10));
 preview:=public.pn_delete_preview('tenants',person);
 -- A new related payment must invalidate a tenant cascade preview, even without a tenant revision change.
 perform pg_temp.run('payment_save',jsonb_build_object('tenancy_id',tenant,'amount_paisa',100,'payment_date',month,'through_month',month));
 begin perform public.pn_delete(jsonb_build_object('action','permanent_delete','request_id',gen_random_uuid(),'data',jsonb_build_object('entity_type','tenants','id',person,'token',preview->>'token','confirmation','DELETE')));raise exception 'Changed cascade accepted';exception when raise_exception then if sqlerrm='Changed cascade accepted' then raise;end if;end;
 result:=pg_temp.erase('tenants',person);
 perform pg_temp.expect(not exists(select 1 from public.tenants where id=person) and not exists(select 1 from public.tenancies where tenant_id=person),'Tenant and tenancies must be removed');
 perform pg_temp.expect(not exists(select 1 from public.payments where tenancy_id=tenant) and not exists(select 1 from public.deposit_transactions where tenancy_id=tenant) and not exists(select 1 from public.rent_periods where tenancy_id=tenant),'Tenant cascade must remove financial records');
 perform pg_temp.expect(not exists(select 1 from public.tenant_documents where tenant_id=person),'Tenant documents must be removed');
 perform pg_temp.expect(result->'paths' @> jsonb_build_array('tenants/'||person||'/photo.png'),'Storage cleanup paths must survive confirmation retries');
 perform pg_temp.expect(not exists(select 1 from public.audit_logs where entity_id in(person,tenant)),'Tenant audit history must be removed');
 perform pg_temp.expect(not exists(select 1 from public.pn_requests r where r.result::text like '%'||person||'%' or r.command::text like '%'||tenant||'%'),'Tenant payloads must be purged from old retries');
 perform pg_temp.expect(public.pn_due((other->>'id')::uuid)=20000,'Unrelated tenancy must remain unchanged');
 select id into charge from public.rent_periods where tenancy_id=(other->>'id')::uuid and kind='monthly';
 perform pg_temp.erase('rent_periods',charge);perform public.pn_generate_for((other->>'id')::uuid);
 perform pg_temp.expect(not exists(select 1 from public.rent_periods where tenancy_id=(other->>'id')::uuid),'Deleted monthly charge must not reappear during generation');
 schedule:=pg_temp.run('recurring_save',jsonb_build_object('category','Water','description','Deletion schedule','amount_paisa',100,'next_date',month));
 expense:=pg_temp.run('expense_save',jsonb_build_object('category','Water','description','Deletion expense','amount_paisa',100,'expense_date',month,'recurring_expense_id',schedule->>'id','recurring_revision',schedule->'revision','scheduled_date',month));
 perform pg_temp.erase('recurring_expenses',(schedule->>'id')::uuid);
 perform pg_temp.expect(not exists(select 1 from public.expenses where id=(expense->>'id')::uuid),'Deleting a schedule removes its related expenses as previewed');
 repair:=pg_temp.run('maintenance_save',jsonb_build_object('title','Deletion repair','maintenance_date',month,'amount_paisa',100));
 perform pg_temp.erase('expenses',(repair->>'expense_id')::uuid);
 perform pg_temp.expect(not exists(select 1 from public.maintenance where id=(repair->>'id')::uuid),'Linked expense and maintenance must delete together');
 begin perform public.pn_delete_preview('flats',(select id from public.flats limit 1));raise exception 'Fixed flat deletion accepted';exception when raise_exception then if sqlerrm='Fixed flat deletion accepted' then raise;end if;end;
 perform pg_temp.expect((select count(*) from public.flats)=14,'Fixed building structure must survive');
end $$;
rollback;
