-- PocketMind assistant memory, audit trail and Koog checkpoints.
--
-- The assistant service uses the caller's Supabase JWT. Every exposed table is
-- protected by RLS and every child relation repeats user_id in a composite
-- foreign key, preventing cross-user references even if application code fails.

create extension if not exists pg_cron with schema pg_catalog;

create table public.assistant_conversations (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users (id) on delete cascade,
    title text,
    status text not null default 'active',
    locale text not null default 'es-CO',
    prompt_version text not null,
    tool_schema_version integer not null,
    schema_version integer not null default 1,
    last_message_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (id, user_id),
    constraint assistant_conversations_title_length
        check (title is null or char_length(trim(title)) between 1 and 120),
    constraint assistant_conversations_status
        check (status in ('active', 'archived')),
    constraint assistant_conversations_locale
        check (char_length(trim(locale)) between 2 and 20),
    constraint assistant_conversations_prompt_version
        check (char_length(trim(prompt_version)) between 1 and 80),
    constraint assistant_conversations_tool_schema_version
        check (tool_schema_version > 0),
    constraint assistant_conversations_schema_version
        check (schema_version > 0)
);

create table public.assistant_messages (
    id uuid primary key default gen_random_uuid(),
    conversation_id uuid not null,
    user_id uuid not null references auth.users (id) on delete cascade,
    turn_id uuid not null,
    client_message_id text,
    role text not null,
    content text not null,
    input_modality text not null default 'text',
    prompt_version text,
    model_id text,
    schema_version integer not null default 1,
    created_at timestamptz not null default now(),
    unique (id, user_id),
    foreign key (conversation_id, user_id)
        references public.assistant_conversations (id, user_id)
        on delete cascade,
    constraint assistant_messages_client_id_length
        check (
            client_message_id is null
            or char_length(trim(client_message_id)) between 1 and 160
        ),
    constraint assistant_messages_role
        check (role in ('user', 'assistant', 'system', 'tool')),
    constraint assistant_messages_content_length
        check (char_length(content) between 1 and 20000),
    constraint assistant_messages_input_modality
        check (input_modality in ('text', 'voice_transcript', 'system', 'tool')),
    constraint assistant_messages_prompt_version
        check (
            prompt_version is null
            or char_length(trim(prompt_version)) between 1 and 80
        ),
    constraint assistant_messages_model_id
        check (
            model_id is null
            or char_length(trim(model_id)) between 1 and 120
        ),
    constraint assistant_messages_schema_version
        check (schema_version > 0)
);

create unique index assistant_messages_user_client_id_idx
on public.assistant_messages (user_id, client_message_id)
where client_message_id is not null;

create table public.assistant_command_drafts (
    id uuid primary key default gen_random_uuid(),
    conversation_id uuid not null,
    user_id uuid not null references auth.users (id) on delete cascade,
    command_type text not null,
    command_payload jsonb not null,
    command_schema_version integer not null,
    state text not null default 'proposed',
    idempotency_key text not null,
    payload_hash text not null,
    financial_state_version bigint not null,
    execution_result jsonb,
    error_code text,
    version bigint not null default 1,
    schema_version integer not null default 1,
    expires_at timestamptz not null default (now() + interval '24 hours'),
    confirmed_at timestamptz,
    cancelled_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (id, user_id),
    unique (user_id, idempotency_key),
    foreign key (conversation_id, user_id)
        references public.assistant_conversations (id, user_id)
        on delete cascade,
    constraint assistant_command_drafts_type
        check (char_length(trim(command_type)) between 1 and 120),
    constraint assistant_command_drafts_payload_object
        check (jsonb_typeof(command_payload) = 'object'),
    constraint assistant_command_drafts_command_schema_version
        check (command_schema_version > 0),
    constraint assistant_command_drafts_state
        check (
            state in (
                'proposed',
                'confirmed',
                'cancelled',
                'completed',
                'failed',
                'expired'
            )
        ),
    constraint assistant_command_drafts_idempotency_key
        check (char_length(trim(idempotency_key)) between 16 and 160),
    constraint assistant_command_drafts_payload_hash
        check (payload_hash ~ '^[a-f0-9]{64}$'),
    constraint assistant_command_drafts_financial_state_version
        check (financial_state_version >= 0),
    constraint assistant_command_drafts_execution_result_object
        check (
            execution_result is null
            or jsonb_typeof(execution_result) = 'object'
        ),
    constraint assistant_command_drafts_error_code
        check (
            error_code is null
            or char_length(trim(error_code)) between 1 and 120
        ),
    constraint assistant_command_drafts_version
        check (version > 0),
    constraint assistant_command_drafts_schema_version
        check (schema_version > 0),
    constraint assistant_command_drafts_expiry
        check (expires_at > created_at)
);

create table public.assistant_command_events (
    id uuid primary key default gen_random_uuid(),
    conversation_id uuid not null,
    draft_id uuid not null,
    user_id uuid not null references auth.users (id) on delete cascade,
    event_type text not null,
    from_state text,
    to_state text not null,
    draft_version bigint not null,
    event_payload jsonb not null default '{}'::jsonb,
    schema_version integer not null default 1,
    created_at timestamptz not null default now(),
    unique (id, user_id),
    foreign key (conversation_id, user_id)
        references public.assistant_conversations (id, user_id)
        on delete cascade,
    foreign key (draft_id, user_id)
        references public.assistant_command_drafts (id, user_id)
        on delete cascade,
    constraint assistant_command_events_type
        check (char_length(trim(event_type)) between 1 and 80),
    constraint assistant_command_events_from_state
        check (
            from_state is null
            or from_state in (
                'proposed',
                'confirmed',
                'cancelled',
                'completed',
                'failed',
                'expired'
            )
        ),
    constraint assistant_command_events_to_state
        check (
            to_state in (
                'proposed',
                'confirmed',
                'cancelled',
                'completed',
                'failed',
                'expired'
            )
        ),
    constraint assistant_command_events_version
        check (draft_version > 0),
    constraint assistant_command_events_payload_object
        check (jsonb_typeof(event_payload) = 'object'),
    constraint assistant_command_events_schema_version
        check (schema_version > 0)
);

create table public.assistant_product_aliases (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users (id) on delete cascade,
    product_id text not null,
    product_type text not null,
    alias text not null,
    normalized_alias text generated always as (lower(trim(alias))) stored,
    schema_version integer not null default 1,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (id, user_id),
    unique (user_id, normalized_alias),
    constraint assistant_product_aliases_product_id
        check (char_length(trim(product_id)) between 1 and 160),
    constraint assistant_product_aliases_product_type
        check (char_length(trim(product_type)) between 1 and 80),
    constraint assistant_product_aliases_alias
        check (char_length(trim(alias)) between 1 and 80),
    constraint assistant_product_aliases_schema_version
        check (schema_version > 0)
);

create table public.assistant_checkpoints (
    id uuid primary key default gen_random_uuid(),
    conversation_id uuid not null,
    user_id uuid not null references auth.users (id) on delete cascade,
    checkpoint_key text not null,
    graph_version text not null,
    checkpoint_version bigint not null,
    state jsonb not null,
    schema_version integer not null default 1,
    checkpoint_created_at timestamptz not null,
    expires_at timestamptz not null default (now() + interval '7 days'),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (id, user_id),
    unique (user_id, conversation_id, checkpoint_key),
    foreign key (conversation_id, user_id)
        references public.assistant_conversations (id, user_id)
        on delete cascade,
    constraint assistant_checkpoints_key
        check (char_length(trim(checkpoint_key)) between 1 and 160),
    constraint assistant_checkpoints_graph_version
        check (char_length(trim(graph_version)) between 1 and 80),
    constraint assistant_checkpoints_checkpoint_version
        check (checkpoint_version >= 0),
    constraint assistant_checkpoints_state_object
        check (jsonb_typeof(state) = 'object'),
    constraint assistant_checkpoints_schema_version
        check (schema_version > 0),
    constraint assistant_checkpoints_expiry
        check (expires_at > checkpoint_created_at)
);

create index assistant_conversations_user_activity_idx
on public.assistant_conversations (
    user_id,
    coalesce(last_message_at, created_at) desc
);

create index assistant_messages_conversation_history_idx
on public.assistant_messages (user_id, conversation_id, created_at, id);

create index assistant_command_drafts_user_state_idx
on public.assistant_command_drafts (user_id, state, updated_at desc);

create index assistant_command_drafts_expiry_idx
on public.assistant_command_drafts (expires_at)
where state in ('proposed', 'confirmed');

create index assistant_command_events_draft_history_idx
on public.assistant_command_events (user_id, draft_id, created_at, id);

create index assistant_product_aliases_product_idx
on public.assistant_product_aliases (user_id, product_id);

create index assistant_checkpoints_session_idx
on public.assistant_checkpoints (
    user_id,
    conversation_id,
    checkpoint_created_at desc
);

create index assistant_checkpoints_expiry_idx
on public.assistant_checkpoints (expires_at);

create function public.set_assistant_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create trigger assistant_conversations_set_updated_at
before update on public.assistant_conversations
for each row execute procedure public.set_assistant_updated_at();

create trigger assistant_product_aliases_set_updated_at
before update on public.assistant_product_aliases
for each row execute procedure public.set_assistant_updated_at();

create trigger assistant_checkpoints_set_updated_at
before update on public.assistant_checkpoints
for each row execute procedure public.set_assistant_updated_at();

create function public.touch_assistant_conversation_from_message()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    update public.assistant_conversations
    set last_message_at = greatest(
        coalesce(last_message_at, new.created_at),
        new.created_at
    )
    where id = new.conversation_id
      and user_id = new.user_id;
    return new;
end;
$$;

create trigger assistant_messages_touch_conversation
after insert on public.assistant_messages
for each row execute procedure public.touch_assistant_conversation_from_message();

create function public.guard_assistant_command_draft()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if tg_op = 'INSERT' then
        if new.state <> 'proposed' then
            raise exception 'Assistant drafts must start in proposed state'
                using errcode = '23514';
        end if;

        new.version = 1;
        new.execution_result = null;
        new.error_code = null;
        new.confirmed_at = null;
        new.cancelled_at = null;
        new.completed_at = null;
        new.created_at = now();
        new.updated_at = now();
    else
        if new.id <> old.id
            or new.user_id <> old.user_id
            or new.conversation_id <> old.conversation_id
            or new.idempotency_key <> old.idempotency_key
            or new.created_at <> old.created_at
        then
            raise exception 'Immutable assistant draft fields cannot be changed'
                using errcode = '23514';
        end if;

        if new.state <> old.state and not (
            (old.state = 'proposed' and new.state in ('confirmed', 'cancelled', 'expired'))
            or (
                old.state = 'confirmed'
                and new.state in ('completed', 'failed', 'cancelled', 'expired')
            )
        ) then
            raise exception 'Invalid assistant draft state transition: % -> %',
                old.state,
                new.state
                using errcode = '23514';
        end if;

        if (
            new.command_type is distinct from old.command_type
            or new.command_payload is distinct from old.command_payload
            or new.command_schema_version is distinct from old.command_schema_version
            or new.payload_hash is distinct from old.payload_hash
            or new.financial_state_version is distinct from old.financial_state_version
        ) and not (old.state = 'proposed' and new.state = 'proposed') then
            raise exception 'Only proposed drafts can change their command'
                using errcode = '23514';
        end if;

        new.version = old.version + 1;
        new.updated_at = now();
    end if;

    if new.state = 'confirmed' and new.confirmed_at is null then
        new.confirmed_at = now();
    elsif new.state = 'cancelled' and new.cancelled_at is null then
        new.cancelled_at = now();
    elsif new.state = 'completed' and new.completed_at is null then
        new.completed_at = now();
    end if;

    if new.state = 'completed' and new.execution_result is null then
        raise exception 'Completed assistant drafts require an execution result'
            using errcode = '23514';
    end if;

    if new.state = 'failed' and nullif(trim(new.error_code), '') is null then
        raise exception 'Failed assistant drafts require an error code'
            using errcode = '23514';
    end if;

    return new;
end;
$$;

create trigger assistant_command_drafts_guard
before insert or update on public.assistant_command_drafts
for each row execute procedure public.guard_assistant_command_draft();

create function public.audit_assistant_command_draft()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    resolved_event_type text;
begin
    if tg_op = 'INSERT' then
        resolved_event_type = 'draft_created';
    elsif new.state is not distinct from old.state then
        resolved_event_type = 'draft_edited';
    else
        resolved_event_type = 'draft_' || new.state;
    end if;

    insert into public.assistant_command_events (
        conversation_id,
        draft_id,
        user_id,
        event_type,
        from_state,
        to_state,
        draft_version
    )
    values (
        new.conversation_id,
        new.id,
        new.user_id,
        resolved_event_type,
        case when tg_op = 'INSERT' then null else old.state end,
        new.state,
        new.version
    );

    return new;
end;
$$;

create trigger assistant_command_drafts_audit
after insert or update on public.assistant_command_drafts
for each row execute procedure public.audit_assistant_command_draft();

create function public.purge_expired_assistant_memory()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    expired_draft_count integer := 0;
    deleted_checkpoint_count integer := 0;
    deleted_terminal_draft_count integer := 0;
begin
    update public.assistant_command_drafts
    set state = 'expired'
    where state in ('proposed', 'confirmed')
      and expires_at <= now();
    get diagnostics expired_draft_count = row_count;

    delete from public.assistant_checkpoints
    where expires_at <= now();
    get diagnostics deleted_checkpoint_count = row_count;

    delete from public.assistant_command_drafts
    where state in ('cancelled', 'completed', 'failed', 'expired')
      and updated_at < now() - interval '90 days';
    get diagnostics deleted_terminal_draft_count = row_count;

    return jsonb_build_object(
        'expiredDrafts', expired_draft_count,
        'deletedCheckpoints', deleted_checkpoint_count,
        'deletedTerminalDrafts', deleted_terminal_draft_count
    );
end;
$$;

revoke execute on function public.set_assistant_updated_at() from public;
revoke execute on function public.set_assistant_updated_at() from anon, authenticated;
revoke execute on function public.touch_assistant_conversation_from_message() from public;
revoke execute on function public.touch_assistant_conversation_from_message()
from anon, authenticated;
revoke execute on function public.guard_assistant_command_draft() from public;
revoke execute on function public.guard_assistant_command_draft() from anon, authenticated;
revoke execute on function public.audit_assistant_command_draft() from public;
revoke execute on function public.audit_assistant_command_draft() from anon, authenticated;
revoke execute on function public.purge_expired_assistant_memory() from public;
revoke execute on function public.purge_expired_assistant_memory()
from anon, authenticated;

revoke all on table public.assistant_conversations from anon;
revoke all on table public.assistant_messages from anon;
revoke all on table public.assistant_command_drafts from anon;
revoke all on table public.assistant_command_events from anon;
revoke all on table public.assistant_product_aliases from anon;
revoke all on table public.assistant_checkpoints from anon;

grant select, insert, update, delete
on public.assistant_conversations to authenticated;
grant select, insert
on public.assistant_messages to authenticated;
grant select, insert, update
on public.assistant_command_drafts to authenticated;
grant select
on public.assistant_command_events to authenticated;
grant select, insert, update, delete
on public.assistant_product_aliases to authenticated;
grant select, insert, update, delete
on public.assistant_checkpoints to authenticated;

alter table public.assistant_conversations enable row level security;
alter table public.assistant_messages enable row level security;
alter table public.assistant_command_drafts enable row level security;
alter table public.assistant_command_events enable row level security;
alter table public.assistant_product_aliases enable row level security;
alter table public.assistant_checkpoints enable row level security;

create policy "Users manage their own assistant conversations"
on public.assistant_conversations
for all to authenticated
using ((select auth.uid()) is not null and (select auth.uid()) = user_id)
with check ((select auth.uid()) is not null and (select auth.uid()) = user_id);

create policy "Users view their own assistant messages"
on public.assistant_messages
for select to authenticated
using ((select auth.uid()) is not null and (select auth.uid()) = user_id);

create policy "Users append their own assistant messages"
on public.assistant_messages
for insert to authenticated
with check ((select auth.uid()) is not null and (select auth.uid()) = user_id);

create policy "Users view their own assistant drafts"
on public.assistant_command_drafts
for select to authenticated
using ((select auth.uid()) is not null and (select auth.uid()) = user_id);

create policy "Users create their own assistant drafts"
on public.assistant_command_drafts
for insert to authenticated
with check ((select auth.uid()) is not null and (select auth.uid()) = user_id);

create policy "Users update their own assistant drafts"
on public.assistant_command_drafts
for update to authenticated
using ((select auth.uid()) is not null and (select auth.uid()) = user_id)
with check ((select auth.uid()) is not null and (select auth.uid()) = user_id);

create policy "Users view their own assistant command events"
on public.assistant_command_events
for select to authenticated
using ((select auth.uid()) is not null and (select auth.uid()) = user_id);

create policy "Users manage their own assistant product aliases"
on public.assistant_product_aliases
for all to authenticated
using ((select auth.uid()) is not null and (select auth.uid()) = user_id)
with check ((select auth.uid()) is not null and (select auth.uid()) = user_id);

create policy "Users manage their own assistant checkpoints"
on public.assistant_checkpoints
for all to authenticated
using ((select auth.uid()) is not null and (select auth.uid()) = user_id)
with check ((select auth.uid()) is not null and (select auth.uid()) = user_id);

select cron.schedule(
    'pocketmind-assistant-memory-retention',
    '17 * * * *',
    'select public.purge_expired_assistant_memory();'
);

comment on table public.assistant_conversations is
    'User-owned assistant conversation headers. Hard delete cascades all assistant memory.';
comment on table public.assistant_messages is
    'Append-only conversation messages; content is never copied to application logs.';
comment on table public.assistant_command_drafts is
    'Confirmable financial commands. PostgreSQL enforces allowed state transitions.';
comment on table public.assistant_command_events is
    'Append-only draft audit events generated by database triggers.';
comment on table public.assistant_product_aliases is
    'User-confirmed aliases only; temporary agent inferences are never persisted here.';
comment on table public.assistant_checkpoints is
    'Serializable Koog checkpoints retained for seven days by default.';
comment on function public.purge_expired_assistant_memory() is
    'Hourly retention task: expires active drafts, removes expired checkpoints, and purges terminal drafts after 90 days.';
