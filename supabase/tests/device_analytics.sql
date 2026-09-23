begin;

do $$
declare
  key_a text := repeat('a', 64);
  key_b text := repeat('b', 64);
  result jsonb;
begin
  perform public.record_analytics_metric('app_open', 'all', key_a, key_a, key_a);
  perform public.record_analytics_metric('app_version', '3.1.1', key_a, key_a, key_a);
  perform public.record_analytics_metric('device_model', 'OnePlus CPH2655', key_a, key_a, key_a);
  perform public.record_analytics_metric('device_model', 'OnePlus CPH2655', key_a, key_a, key_a);
  perform public.record_analytics_metric('device_model', 'OnePlus CPH2655', key_b, key_b, key_b);
  perform public.record_analytics_metric('android_version', '16', key_a, key_a, key_a);
  perform public.record_analytics_metric('android_version', '16', key_b, key_b, key_b);
  perform public.record_analytics_metric('device_manufacturer', 'OnePlus', key_a, key_a, key_a);
  perform public.record_analytics_metric('device_manufacturer', 'OnePlus', key_b, key_b, key_b);

  begin
    perform public.record_analytics_metric('device_model', 'OnePlus' || chr(10) || 'CPH2655', key_a, key_a, key_a);
    raise exception 'Control character was accepted';
  exception when check_violation then raise;
    when others then
      if sqlerrm = 'Control character was accepted' then raise; end if;
  end;
  begin
    perform public.record_analytics_metric('android_version', '16;id', key_a, key_a, key_a);
    raise exception 'Invalid Android version was accepted';
  exception when others then
    if sqlerrm = 'Invalid Android version was accepted' then raise; end if;
  end;
  begin
    perform public.record_analytics_metric('device_model', repeat('x', 81), key_a, key_a, key_a);
    raise exception 'Oversize model was accepted';
  exception when others then
    if sqlerrm = 'Oversize model was accepted' then raise; end if;
  end;

  result := public.analytics_dashboard();
  if result #>> '{active_users,daily}' <> '1'
    or result #>> '{device_models,0,value}' <> 'OnePlus CPH2655'
    or result #>> '{device_models,0,unique_users}' <> '2'
    or result #>> '{device_models,0,users_today}' <> '2'
    or result #>> '{device_models,0,event_count}' <> '3'
    or result #>> '{android_versions,0,value}' <> '16'
    or result #>> '{android_versions,0,unique_users}' <> '2'
    or result #>> '{device_manufacturers,0,value}' <> 'OnePlus'
    or result #>> '{device_manufacturers,0,unique_users}' <> '2'
    or result #>> '{versions,0,users_today}' <> '1'
    or has_function_privilege('anon', 'public.analytics_dashboard()', 'EXECUTE')
  then
    raise exception 'Analytics dashboard or privileges changed unexpectedly: %', result;
  end if;
end;
$$;

rollback;
