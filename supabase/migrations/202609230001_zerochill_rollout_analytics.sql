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
             and u.metric=c.metric and u.value=c.value) as unique_users,
         (select count(*) from public.analytics_uniques d, periods p2
           where d.period_type='day' and d.period_start=p2.day_start
             and d.metric='app_version' and d.value=c.value) as users_today
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
