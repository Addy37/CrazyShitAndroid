create extension if not exists pgcrypto;

create table if not exists public.app_feedback (
    id uuid primary key default gen_random_uuid(),
    installation_hash text not null,
    type text not null check (type in ('feature_request', 'bug_report', 'general_feedback')),
    message text not null check (char_length(message) between 5 and 2000),
    rating smallint check (rating between 1 and 5),
    status text not null default 'submitted'
        check (status in ('submitted', 'reviewing', 'planned', 'in_progress', 'completed', 'declined')),
    developer_reply text,
    app_version text not null,
    android_version text,
    device text,
    section text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists app_feedback_installation_created_idx
    on public.app_feedback (installation_hash, created_at desc);

alter table public.app_feedback enable row level security;

-- The mobile app only reaches this table through the feedback Edge Function.
-- Keep direct access closed to anon and authenticated API users.
revoke all on table public.app_feedback from anon, authenticated;

create or replace function public.set_feedback_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists set_feedback_updated_at on public.app_feedback;
create trigger set_feedback_updated_at
before update on public.app_feedback
for each row execute function public.set_feedback_updated_at();

