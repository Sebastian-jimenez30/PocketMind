-- Assistant movements are saved automatically but remain reversible.
-- A completed draft may be reopened for an explicit user edit or cancelled
-- because its deterministic local effects can be removed idempotently.
create index if not exists assistant_command_drafts_user_command_id_idx
    on public.assistant_command_drafts (
        user_id,
        (command_payload ->> 'command_id')
    );

create or replace function public.guard_assistant_command_draft()
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
            or (
                old.state = 'completed'
                and new.state in ('proposed', 'cancelled')
            )
            or (
                old.state = 'failed'
                and new.state in ('proposed', 'cancelled')
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
        ) and not (
            (old.state = 'proposed' and new.state = 'proposed')
            or (old.state = 'completed' and new.state = 'proposed')
            or (old.state = 'failed' and new.state = 'proposed')
        ) then
            raise exception 'Only proposed or explicitly reopened drafts can change their command'
                using errcode = '23514';
        end if;

        if old.state in ('completed', 'failed') and new.state = 'proposed' then
            new.execution_result = null;
            new.error_code = null;
            new.confirmed_at = null;
            new.cancelled_at = null;
            new.completed_at = null;
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

comment on function public.guard_assistant_command_draft() is
'Guards optimistic assistant draft transitions, including reversible auto-saved movements.';
