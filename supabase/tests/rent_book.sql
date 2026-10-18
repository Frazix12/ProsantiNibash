-- Disposable database only: business assertions roll back, including audits and records.
begin;
create function pg_temp.expect(ok boolean,message text) returns void language plpgsql as $$ begin if ok is distinct from true then raise exception '%',message; end if; end $$;
create function pg_temp.run(action text,data jsonb,request uuid default gen_random_uuid()) returns jsonb language sql as $$select public.pn_execute(jsonb_build_object('action',action,'request_id',request,'data',data))$$;
do $$
declare tenancy jsonb; tenant uuid; second jsonb; payment jsonb; corrected jsonb; receipt jsonb; req uuid:=gen_random_uuid(); command jsonb; r record;
 month date:=date_trunc('month',(now() at time zone 'Asia/Dhaka'))::date; first_month date; count_before integer; revision bigint; maintenance jsonb; expense uuid; recurring jsonb; moved jsonb;
begin
 first_month:=(month-interval '2 months')::date;
 tenancy:=pg_temp.run('tenancy_save',jsonb_build_object('flat_id',(select id from public.flats where flat_number='1A'),'full_name','Owner test tenant','monthly_rent_paisa',10000,'move_in_date',first_month,'ledger_start_date',first_month,'opening_due_paisa',5000));
 tenant:=(tenancy->>'id')::uuid;
 perform public.pn_snapshot(); perform public.pn_snapshot();
 perform pg_temp.expect((select count(*) from public.rent_periods where tenancy_id=tenant)=4,'Exactly three eligible monthly charges and one opening due');
 perform pg_temp.expect(public.pn_due(tenant)=35000,'Opening due not duplicated');
 command:=jsonb_build_object('tenancy_id',tenant,'amount_paisa',7000,'payment_date',first_month+2,'through_month',month,'payment_method','Cash');
 payment:=pg_temp.run('payment_save',command,req); receipt:=payment->'receipt_snapshot';
 perform pg_temp.expect((select amount_paisa from public.payment_allocations where payment_id=(payment->>'id')::uuid and rent_period_id=(select id from public.rent_periods where tenancy_id=tenant and kind='opening_due'))=5000,'Oldest due paid first');
 perform pg_temp.expect(public.pn_due(tenant)=28000,'Partial payment leaves expected due');
 perform pg_temp.expect(pg_temp.run('payment_save',command,req)=payment,'Retry returns exactly same receipt');
 perform pg_temp.expect((select count(*) from public.payments where tenancy_id=tenant)=1,'Retry cannot duplicate money');
 begin perform pg_temp.run('payment_save',command||jsonb_build_object('amount_paisa',1),req); raise exception 'Different retry payload accepted'; exception when raise_exception then if sqlerrm='Different retry payload accepted' then raise; end if; end;
 corrected:=pg_temp.run('payment_save',payment||jsonb_build_object('amount_paisa',9000));
 perform pg_temp.expect(corrected->'receipt_snapshot'=receipt and corrected->>'receipt_number'=payment->>'receipt_number','Receipt snapshot/number immutable on correction');
 perform pg_temp.expect(public.pn_due(tenant)=26000,'Correction rebuilds allocations');
 begin perform pg_temp.run('payment_save',payment||jsonb_build_object('amount_paisa',12000)); raise exception 'Stale payment accepted'; exception when raise_exception then if sqlerrm='Stale payment accepted' then raise; end if; end;
 -- A backdated payment deterministically rebuilds charges, without rewriting issued receipts.
 perform pg_temp.run('payment_save',jsonb_build_object('tenancy_id',tenant,'amount_paisa',1000,'payment_date',first_month+1,'through_month',month));
 perform pg_temp.expect(public.pn_due(tenant)=25000,'Backdated cash recalculated');
 perform pg_temp.expect((select receipt_snapshot from public.payments where id=(payment->>'id')::uuid)=receipt,'Backdated save preserves issued receipt');
 perform pg_temp.run('payment_save',jsonb_build_object('tenancy_id',tenant,'amount_paisa',30000,'payment_date',month,'through_month',month));
 perform pg_temp.expect(public.pn_due(tenant)=0 and public.pn_credit(tenant)=5000,'Advance credit excluded from due');
 perform pg_temp.run('payment_save',jsonb_build_object('tenancy_id',tenant,'amount_paisa',1000,'payment_date',month,'through_month',month,'kind','credit_refund'));
 perform pg_temp.expect(public.pn_credit(tenant)=4000,'Credit refund removes only available credit');
 perform pg_temp.run('rent_charge',jsonb_build_object('tenancy_id',tenant,'amount_paisa',2000,'period_date',month+interval '1 month','kind','manual'));
 perform pg_temp.expect(public.pn_due(tenant)=0 and public.pn_credit(tenant)=2000,'Remaining advance applies to new later charge, refunded money does not');
 count_before:=(select count(*) from public.audit_logs);
 begin perform pg_temp.run('deposit_save',jsonb_build_object('tenancy_id',tenant,'type','refund','amount_paisa',1,'transaction_date',month)); raise exception 'Overrefund accepted'; exception when raise_exception then if sqlerrm='Overrefund accepted' then raise; end if; end;
 perform pg_temp.expect((select count(*) from public.audit_logs)=count_before and public.pn_deposit_balance(tenant)=0,'Failed transaction leaves no partial deposit or audit');
 perform pg_temp.run('deposit_save',jsonb_build_object('tenancy_id',tenant,'type','received','amount_paisa',8000,'transaction_date',month));
 perform pg_temp.run('deposit_save',jsonb_build_object('tenancy_id',tenant,'type','adjustment','amount_paisa',-500,'transaction_date',month));
 perform pg_temp.expect(public.pn_deposit_balance(tenant)=7500,'Signed deposit adjustment');
 maintenance:=pg_temp.run('maintenance_save',jsonb_build_object('title','Pump repair','maintenance_date',month,'amount_paisa',1200)); expense:=(maintenance->>'expense_id')::uuid;
 maintenance:=pg_temp.run('maintenance_save',maintenance||jsonb_build_object('amount_paisa',1500));
 perform pg_temp.expect((select count(*) from public.expenses where id=expense)=1 and (select amount_paisa from public.expenses where id=expense)=1500,'Repair has exactly one cost updated in place');
 begin perform pg_temp.run('expense_void',jsonb_build_object('id',expense,'revision',2)); raise exception 'Linked expense direct edit accepted'; exception when raise_exception then if sqlerrm='Linked expense direct edit accepted' then raise; end if; end;
 perform pg_temp.run('maintenance_void',jsonb_build_object('id',maintenance->>'id','revision',maintenance->'revision'));
 perform pg_temp.expect((select voided from public.expenses where id=expense),'Voiding repair voids one linked cost');
 recurring:=pg_temp.run('recurring_save',jsonb_build_object('category','Utilities','description','Water','amount_paisa',800,'next_date',month,'frequency','monthly'));
 perform pg_temp.run('expense_save',jsonb_build_object('category','Utilities','description','Water','amount_paisa',800,'expense_date',month,'recurring_expense_id',recurring->>'id','recurring_revision',recurring->'revision','scheduled_date',recurring->'next_date'));
 perform pg_temp.expect((select next_date from public.recurring_expenses where id=(recurring->>'id')::uuid)=(month+interval '1 month')::date,'Paid recurring advances next date');
 begin perform pg_temp.run('expense_save',jsonb_build_object('category','Utilities','description','Water','amount_paisa',800,'expense_date',month+1,'recurring_expense_id',recurring->>'id','recurring_revision',recurring->'revision','scheduled_date',recurring->'next_date')); raise exception 'Stale recurring occurrence with different payment date accepted'; exception when raise_exception then if sqlerrm='Stale recurring occurrence with different payment date accepted' then raise; end if; end;
 begin perform pg_temp.run('expense_save',jsonb_build_object('category','Utilities','description','Water','amount_paisa',800,'expense_date',month,'recurring_expense_id',recurring->>'id','recurring_revision',recurring->'revision','scheduled_date',recurring->'next_date')); raise exception 'Recurring same date duplicate accepted'; exception when raise_exception then if sqlerrm='Recurring same date duplicate accepted' then raise; end if; end;
 -- Attachment metadata changes no money and cannot rewrite an issued receipt.
 corrected:=pg_temp.run('attachment_save',jsonb_build_object('entity_type','payments','id',corrected->>'id','revision',corrected->'revision','storage_path','payments/'||(corrected->>'id')||'/receipt.pdf'));
 perform pg_temp.expect(corrected->'receipt_snapshot'=receipt and public.pn_due(tenant)=0,'Attachment save preserves money and original receipt');
 corrected:=pg_temp.run('document_save',jsonb_build_object('tenant_id',tenancy->>'tenant_id','document_type','Profile photo','storage_path','tenants/'||(tenancy->>'tenant_id')||'/photo.png','file_name','photo.png','mime_type','image/png','size_bytes',100));
 perform pg_temp.expect((select profile_image_path from public.tenants where id=(tenancy->>'tenant_id')::uuid)=corrected->>'storage_path','Profile photo metadata updates tenant atomically');
 perform pg_temp.run('document_delete',jsonb_build_object('id',corrected->>'id','revision',corrected->'revision'));
 perform pg_temp.expect((select profile_image_path from public.tenants where id=(tenancy->>'tenant_id')::uuid) is null,'Deleting current profile clears profile reference');
 begin perform pg_temp.run('rent_charge',jsonb_build_object('tenancy_id',tenant,'period_date',first_month-5,'kind','manual','amount_paisa',5000)); raise exception 'Opening history doublecount accepted'; exception when raise_exception then if sqlerrm='Opening history doublecount accepted' then raise; end if; end;
 moved:=pg_temp.run('move_out',tenancy||jsonb_build_object('move_out_date',month,'damage_paisa',3000,'adjustment_paisa',0));
 perform pg_temp.expect(moved->>'status'='moved_out' and public.pn_deposit_balance(tenant)=0 and public.pn_credit(tenant)=0,'Move-out closes and refunds balances atomically');
 perform pg_temp.expect(public.pn_due(tenant)=0,'Deposit covers settlement damage after credit');
 perform pg_temp.expect(not exists(select 1 from public.tenancies where flat_id=(tenancy->>'flat_id')::uuid and status='active'),'Move-out frees flat');
 second:=pg_temp.run('tenancy_save',jsonb_build_object('flat_id',tenancy->>'flat_id','full_name','Replacement tenant','monthly_rent_paisa',20000,'move_in_date',month,'ledger_start_date',month));
 perform pg_temp.expect(public.pn_due((second->>'id')::uuid)=20000,'New tenant has independent debt');
 perform pg_temp.expect((select count(*) from public.rent_periods where tenancy_id not in(tenant,(second->>'id')::uuid))=0,'No rent generated for vacant flats');
 moved:=pg_temp.run('move_out',second||jsonb_build_object('move_out_date',month));
 perform pg_temp.expect(public.pn_due((second->>'id')::uuid)=20000 and (moved->>'remaining_due_paisa')::bigint=20000,'Insufficient settlement retains old tenancy debt');
 second:=pg_temp.run('tenancy_save',jsonb_build_object('flat_id',(select f.id from public.flats f where f.flat_number='1B'),'full_name','Opening credit tenant','monthly_rent_paisa',10000,'move_in_date',month,'ledger_start_date',month,'opening_credit_paisa',5000));
 perform pg_temp.expect(public.pn_due((second->>'id')::uuid)=5000 and public.pn_credit((second->>'id')::uuid)=0,'Brought-forward credit applies once to new charges');
 perform pg_temp.expect((select count(*) from public.payments where tenancy_id=(second->>'id')::uuid and kind='cash')=0,'Opening credit does not invent cash income');
 -- Missing/stale revisions preserve all original records.
 count_before:=(select count(*) from public.tenants);
 begin perform pg_temp.run('tenancy_save',tenancy||jsonb_build_object('revision',0,'full_name','Stale person')); raise exception 'Stale tenancy accepted'; exception when raise_exception then if sqlerrm='Stale tenancy accepted' then raise; end if; end;
 perform pg_temp.expect((select count(*) from public.tenants)=count_before and (select full_name from public.tenants where id=(tenancy->>'tenant_id')::uuid)='Owner test tenant','Stale tenancy update rolls back personal changes');
end $$;
-- Settlement must reach all surviving manual charges, including a ledger that starts in the future.
do $$
declare tenancy jsonb; moved jsonb; month date:=date_trunc('month',(now() at time zone 'Asia/Dhaka'))::date;
begin
 tenancy:=pg_temp.run('tenancy_save',jsonb_build_object('flat_id',(select id from public.flats where flat_number='7A'),'full_name','Future ledger settlement','monthly_rent_paisa',10000,'move_in_date',month,'ledger_start_date',month+interval '1 month'));
 perform pg_temp.run('rent_charge',jsonb_build_object('tenancy_id',tenancy->>'id','period_date',month+interval '2 months','kind','manual','amount_paisa',20000));
 perform pg_temp.run('deposit_save',jsonb_build_object('tenancy_id',tenancy->>'id','type','received','amount_paisa',20000,'transaction_date',month));
 moved:=pg_temp.run('move_out',tenancy||jsonb_build_object('move_out_date',month));
 perform pg_temp.expect((moved->>'remaining_due_paisa')::bigint=0 and (moved->>'credit_refund_paisa')::bigint=0 and (moved->>'deposit_refund_paisa')::bigint=0,'Settlement covers surviving later manual charge instead of refunding its deposit');
 perform pg_temp.expect((select count(*) from public.rent_periods where tenancy_id=(tenancy->>'id')::uuid and kind='monthly')=0,'Future ledger move-out does not invent monthly rent');
 perform pg_temp.run('rent_charge',jsonb_build_object('tenancy_id',tenancy->>'id','period_date',month-1,'kind','manual','amount_paisa',100));
 perform pg_temp.expect(public.pn_due((tenancy->>'id')::uuid)=100,'Settlement credit does not falsely block detailed historical corrections');
end $$;
-- Accepted no-auth RPC boundary does not grant table writes or internal helpers.
do $$ begin
 perform pg_temp.expect(has_function_privilege('anon','public.pn_snapshot()','execute'),'Snapshot RPC exposed');
 perform pg_temp.expect(has_function_privilege('anon','public.pn_execute(jsonb)','execute'),'Mutation RPC exposed');
 perform pg_temp.expect(not has_function_privilege('anon','public.pn_save(text,jsonb)','execute'),'Internal save helper denied');
 perform pg_temp.expect(not has_table_privilege('anon','public.payments','insert'),'Direct payment writes denied');
 perform pg_temp.expect(not has_table_privilege('anon','public.tenants','select'),'Direct personal data reads denied');
end $$;
set local role anon;
select pg_temp.expect(jsonb_array_length(public.pn_snapshot()->'flats')=14,'Anonymous RPC returns seeded flats');
select pg_temp.expect(public.pn_execute(jsonb_build_object('action','building_save','request_id',gen_random_uuid(),'data',jsonb_build_object('name','Anon RPC test','revision',1)))->>'name'='Anon RPC test','Anonymous can execute accepted mutation boundary');
do $$ begin
 begin insert into public.payments default values; raise exception 'Direct anonymous write accepted'; exception when insufficient_privilege then null; end;
end $$;
reset role;
-- Storage check uses the minimal Supabase storage stub in the disposable test database.
do $$ begin
 if to_regclass('storage.objects') is not null then
  perform pg_temp.expect((select not public and file_size_limit=20971520 from storage.buckets where id='pn-files'),'Private20MB storage bucket');
 end if;
end $$;

do $$ begin
 if to_regclass('storage.objects') is not null then
  execute 'set local role anon';
  insert into storage.objects(bucket_id,name) values('pn-files','tenants/11111111-1111-1111-1111-111111111111/check.pdf');
  perform pg_temp.expect((select count(*) from storage.objects where name like '%/check.pdf')=1,'Allowed private path readable through accepted anon policy');
  begin insert into storage.objects(bucket_id,name) values('pn-files','../outside.pdf'); raise exception 'Invalid storage path accepted'; exception when insufficient_privilege then null; end;
  begin insert into storage.objects(bucket_id,name) values('other-bucket','tenants/11111111-1111-1111-1111-111111111111/check.pdf'); raise exception 'Wrong bucket accepted'; exception when insufficient_privilege then null; end;
  execute 'reset role';
 end if;
end $$;

rollback;
