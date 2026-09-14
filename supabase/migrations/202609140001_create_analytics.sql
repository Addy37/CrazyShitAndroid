create table if not exists public.analytics_counters (
  period_type text not null check (period_type in ('day','week','month')),
  period_start date not null,
  metric text not null check (char_length(metric) between 1 and 64),
  value text not null check (char_length(value) between 1 and 160),
  event_count bigint not null default 0 check (event_count >= 0),
  primary key (period_type, period_start, metric, value)
);

create table if not exists public.analytics_uniques (
  period_type text not null check (period_type in ('day','week','month')),
  period_start date not null,
  metric text not null check (char_length(metric) between 1 and 64),
  value text not null check (char_length(value) between 1 and 160),
  anon_key text not null check (anon_key ~ '^[0-9a-f]{64}$'),
  primary key (period_type, period_start, metric, value, anon_key)
);

create index if not exists analytics_counters_lookup_idx
  on public.analytics_counters (metric, period_type, period_start, event_count desc);
create index if not exists analytics_uniques_lookup_idx
  on public.analytics_uniques (metric, period_type, period_start, value);

alter table public.analytics_counters enable row level security;
alter table public.analytics_uniques enable row level security;
revoke all on public.analytics_counters from anon, authenticated;
revoke all on public.analytics_uniques from anon, authenticated;

create or replace function public.record_analytics_metric(
  p_metric text,
  p_value text,
  p_day_key text,
  p_week_key text,
  p_month_key text
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_day date := (timezone('UTC', now()))::date;
  v_week date := date_trunc('week', timezone('UTC', now()))::date;
  v_month date := date_trunc('month', timezone('UTC', now()))::date;
begin
  if p_metric not in ('app_open','app_version','section','source','creator') then
    raise exception 'Unsupported analytics metric';
  end if;
  if p_value is null or char_length(btrim(p_value)) < 1 or char_length(p_value) > 160 then
    raise exception 'Invalid analytics value';
  end if;
  if p_day_key !~ '^[0-9a-f]{64}$' or p_week_key !~ '^[0-9a-f]{64}$' or p_month_key !~ '^[0-9a-f]{64}$' then
    raise exception 'Invalid analytics key';
  end if;

  insert into public.analytics_counters(period_type, period_start, metric, value, event_count)
  values
    ('day', v_day, p_metric, p_value, 1),
    ('week', v_week, p_metric, p_value, 1),
    ('month', v_month, p_metric, p_value, 1)
  on conflict (period_type, period_start, metric, value)
  do update set event_count = public.analytics_counters.event_count + 1;

  insert into public.analytics_uniques(period_type, period_start, metric, value, anon_key)
  values
    ('day', v_day, p_metric, p_value, p_day_key),
    ('week', v_week, p_metric, p_value, p_week_key),
    ('month', v_month, p_metric, p_value, p_month_key)
  on conflict do nothing;
end;
$$;

revoke all on function public.record_analytics_metric(text,text,text,text,text) from public, anon, authenticated;
grant execute on function public.record_analytics_metric(text,text,text,text,text) to service_role;

create or replace function public.analytics_dashboard()
returns jsonb
language sql
security definer
set search_path = public
as $$
with periods as (
  select
    (timezone('UTC', now()))::date as day_start,
    date_trunc('week', timezone('UTC', now()))::date as week_start,
    date_trunc('month', timezone('UTC', now()))::date as month_start
),
active as (
  select
    (select count(*) from public.analytics_uniques u, periods p
      where u.period_type='day' and u.period_start=p.day_start and u.metric='app_open' and u.value='all') as daily,
    (select count(*) from public.analytics_uniques u, periods p
      where u.period_type='week' and u.period_start=p.week_start and u.metric='app_open' and u.value='all') as weekly,
    (select count(*) from public.analytics_uniques u, periods p
      where u.period_type='month' and u.period_start=p.month_start and u.metric='app_open' and u.value='all') as monthly
),
section_rows as (
  select c.value, c.event_count,
         (select count(*) from public.analytics_uniques u
           where u.period_type=c.period_type and u.period_start=c.period_start
             and u.metric=c.metric and u.value=c.value) as unique_users
  from public.analytics_counters c, periods p
  where c.period_type='week' and c.period_start=p.week_start and c.metric='section'
  order by unique_users desc, c.event_count desc, c.value
  limit 12
),
source_rows as (
  select c.value, c.event_count,
         (select count(*) from public.analytics_uniques u
           where u.period_type=c.period_type and u.period_start=c.period_start
             and u.metric=c.metric and u.value=c.value) as unique_users
  from public.analytics_counters c, periods p
  where c.period_type='week' and c.period_start=p.week_start and c.metric='source'
  order by unique_users desc, c.event_count desc, c.value
  limit 12
),
creator_rows as (
  select c.value, c.event_count,
         (select count(*) from public.analytics_uniques u
           where u.period_type=c.period_type and u.period_start=c.period_start
             and u.metric=c.metric and u.value=c.value) as unique_users,
         (select count(*) from public.analytics_uniques d, periods p2
           where d.period_type='day' and d.period_start=p2.day_start
             and d.metric='creator' and d.value=c.value) as users_today
  from public.analytics_counters c, periods p
  where c.period_type='week' and c.period_start=p.week_start and c.metric='creator'
  order by unique_users desc, c.event_count desc, c.value
  limit 25
),
version_rows as (
  select c.value, c.event_count,
         (select count(*) from public.analytics_uniques u
           where u.period_type=c.period_type and u.period_start=c.period_start
             and u.metric=c.metric and u.value=c.value) as unique_users
  from public.analytics_counters c, periods p
  where c.period_type='week' and c.period_start=p.week_start and c.metric='app_version'
  order by unique_users desc, c.event_count desc, c.value
  limit 12
)
select jsonb_build_object(
  'generated_at', timezone('UTC', now()),
  'active_users', jsonb_build_object(
    'daily', (select daily from active),
    'weekly', (select weekly from active),
    'monthly', (select monthly from active)
  ),
  'sections', coalesce((select jsonb_agg(to_jsonb(section_rows)) from section_rows), '[]'::jsonb),
  'sources', coalesce((select jsonb_agg(to_jsonb(source_rows)) from source_rows), '[]'::jsonb),
  'creators', coalesce((select jsonb_agg(to_jsonb(creator_rows)) from creator_rows), '[]'::jsonb),
  'versions', coalesce((select jsonb_agg(to_jsonb(version_rows)) from version_rows), '[]'::jsonb)
);
$$;

revoke all on function public.analytics_dashboard() from public, anon, authenticated;
grant execute on function public.analytics_dashboard() to service_role;
