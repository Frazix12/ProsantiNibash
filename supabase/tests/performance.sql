-- Disposable database only; verify sync semantics without writing live records.
begin;
create function pg_temp.expect(ok boolean,message text) returns void language plpgsql as $$ begin if ok is distinct from true then raise exception '%',message; end if; end $$;
do $$
declare first jsonb; delta jsonb; unchanged jsonb; command jsonb; version bigint; tenancy uuid; payment uuid; request uuid:=gen_random_uuid(); today date:=(now() at time zone 'Asia/Dhaka')::date;
begin
 first:=public.pn_sync();version:=(first->>'version')::bigint;
 perform pg_temp.expect((first->>'full')::boolean,'First sync must be full');
 perform pg_temp.expect(jsonb_array_length(first->'book'->'flats')=14,'Full sync retains fixed building');
 unchanged:=public.pn_sync(version);
 perform pg_temp.expect((unchanged->>'version')::bigint=version,'Unchanged reads must not rewrite ledgers');
 perform pg_temp.expect(jsonb_array_length(unchanged->'book'->'rent_periods')=0,'Unchanged sync does not transfer rent history');
 command:=jsonb_build_object('action','tenancy_save','request_id',request,'data',jsonb_build_object('flat_id',(select id from public.flats where flat_number='1A'),'full_name','Sync fixture','monthly_rent_paisa',10000,'move_in_date',date_trunc('month',today)::date,'ledger_start_date',date_trunc('month',today)::date));
 delta:=public.pn_sync(version,command);
 tenancy:=(delta->'book'->'tenancies'->0->>'id')::uuid;
 perform pg_temp.expect(jsonb_array_length(delta->'book'->'rent_periods')=1,'New tenancy receives current rent in same response');
 version:=(delta->>'version')::bigint;
 command:=jsonb_build_object('action','payment_save','request_id',gen_random_uuid(),'data',jsonb_build_object('tenancy_id',tenancy,'amount_paisa',3000,'payment_date',today,'through_month',date_trunc('month',today)::date,'payment_method','Cash'));
 delta:=public.pn_sync(version,command);
 payment:=(delta->'book'->'payments'->0->>'id')::uuid;
 perform pg_temp.expect(jsonb_array_length(delta->'allocation_tenancies')=1,'Payment marks its allocation group');
 perform pg_temp.expect((delta->'book'->'payment_allocations'->0->>'amount_paisa')::bigint=3000,'Delta includes authoritative allocation');
 perform pg_temp.expect(not ((delta->'book'->'payments'->0) ? 'receipt_snapshot'),'Regular sync excludes receipt payload');
 perform pg_temp.expect(public.pn_receipt(payment) ? 'receipt_snapshot','Receipt remains accessible on demand');
 perform pg_temp.expect(public.pn_sync(version,command)->'book'->'payments'=delta->'book'->'payments','Retry retains original financial entry');
 version:=(delta->>'version')::bigint;
 command:=jsonb_build_object('action','payment_void','request_id',gen_random_uuid(),'data',jsonb_build_object('id',payment,'revision',1));
 delta:=public.pn_sync(version,command);
 perform pg_temp.expect(jsonb_array_length(delta->'book'->'payment_allocations')=0,'Void sends empty replacement allocation group');
 perform pg_temp.expect(jsonb_array_length(delta->'allocation_tenancies')=1,'Empty group still invalidates client allocations');
 perform pg_temp.expect(public.pn_due(tenancy)=10000,'Void restores exact debt');
 perform pg_temp.expect(not (public.pn_audit_page()->0 ? 'new_data'),'Audit page omits detail payload');
 perform pg_temp.expect(public.pn_audit_detail((public.pn_audit_page()->0->>'id')::uuid) ? 'new_data','Audit detail remains available');
end $$;
rollback;
