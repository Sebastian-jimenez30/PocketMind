-- PocketMind offline-first synchronization envelope.
--
-- Room keeps the normalized device model. The server stores versioned entity
-- envelopes so new local entity types can be added without a destructive
-- remote migration. Analytics projections can be derived from payload later.

create table public.finance_sync_records (
    user_id uuid not null references auth.users (id) on delete cascade,
    entity_type text not null,
    entity_id text not null,
    schema_version integer not null default 1,
    payload jsonb,
    is_deleted boolean not null default false,
    updated_at_epoch_millis bigint not null default (
        floor(extract(epoch from clock_timestamp()) * 1000)::bigint
    ),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (user_id, entity_type, entity_id),
    constraint finance_sync_records_type_not_blank
        check (char_length(trim(entity_type)) between 1 and 60),
    constraint finance_sync_records_id_not_blank
        check (char_length(trim(entity_id)) between 1 and 160),
    constraint finance_sync_records_payload_matches_state
        check (
            (is_deleted and payload is null)
            or (not is_deleted and payload is not null)
        )
);

create index finance_sync_records_user_updated_idx
on public.finance_sync_records (user_id, updated_at_epoch_millis);

create index finance_sync_records_user_type_idx
on public.finance_sync_records (user_id, entity_type);

create function public.set_finance_sync_record_metadata()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at = now();
    new.updated_at_epoch_millis =
        floor(extract(epoch from clock_timestamp()) * 1000)::bigint;
    return new;
end;
$$;

create trigger finance_sync_records_set_metadata
before update on public.finance_sync_records
for each row execute procedure public.set_finance_sync_record_metadata();

revoke execute on function public.set_finance_sync_record_metadata() from public;
revoke execute on function public.set_finance_sync_record_metadata() from anon, authenticated;

grant select, insert, update on public.finance_sync_records to authenticated;

alter table public.finance_sync_records enable row level security;

create policy "Users can view their own finance records"
on public.finance_sync_records for select to authenticated
using ((select auth.uid()) is not null and (select auth.uid()) = user_id);

create policy "Users can create their own finance records"
on public.finance_sync_records for insert to authenticated
with check ((select auth.uid()) is not null and (select auth.uid()) = user_id);

create policy "Users can update their own finance records"
on public.finance_sync_records for update to authenticated
using ((select auth.uid()) is not null and (select auth.uid()) = user_id)
with check ((select auth.uid()) is not null and (select auth.uid()) = user_id);

comment on table public.finance_sync_records is
    'User-scoped, versioned synchronization envelopes for PocketMind Room entities.';
comment on column public.finance_sync_records.payload is
    'Typed JSON payload for active records; null only for deletion tombstones.';
