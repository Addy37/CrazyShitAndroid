create table if not exists public.source_config_versions (
    config_version bigint primary key check (config_version > 0),
    schema_version integer not null check (schema_version > 0),
    updated_at timestamptz not null,
    published_at timestamptz not null default now(),
    based_on_version bigint references public.source_config_versions(config_version),
    action text not null default 'publish' check (action in ('publish', 'rollback')),
    is_active boolean not null default false,
    config jsonb not null check (jsonb_typeof(config) = 'object')
);

create unique index if not exists source_config_one_active_idx
    on public.source_config_versions (is_active) where is_active;

alter table public.source_config_versions enable row level security;
revoke all on table public.source_config_versions from anon, authenticated;

create or replace function public.publish_source_config(
    p_config jsonb,
    p_based_on_version bigint default null,
    p_action text default 'publish'
)
returns bigint
language plpgsql
security definer
set search_path = public
as $$
declare
    requested_version bigint;
    requested_schema integer;
    requested_updated_at timestamptz;
    active_version bigint;
begin
    perform pg_advisory_xact_lock(907013);
    requested_version := (p_config ->> 'configVersion')::bigint;
    requested_schema := (p_config ->> 'schemaVersion')::integer;
    requested_updated_at := (p_config ->> 'updatedAt')::timestamptz;
    select config_version into active_version
      from public.source_config_versions where is_active for update;
    if active_version is not null and requested_version <= active_version then
        raise exception 'configVersion must increase';
    end if;
    if p_action not in ('publish', 'rollback') then raise exception 'invalid action'; end if;
    update public.source_config_versions set is_active = false where is_active;
    insert into public.source_config_versions (
        config_version, schema_version, updated_at, based_on_version, action, is_active, config
    ) values (
        requested_version, requested_schema, requested_updated_at,
        p_based_on_version, p_action, true, p_config
    );
    return requested_version;
end;
$$;

revoke all on function public.publish_source_config(jsonb, bigint, text) from public, anon, authenticated;
