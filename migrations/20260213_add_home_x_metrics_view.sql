-- Canonical local Home metrics for Home-X/Home dashboard endpoints.
drop view if exists view_home_x_metrics;

create view view_home_x_metrics as
with year_start as (
  select date_trunc('year', current_date)::date as d
),
week_start as (
  select date_trunc('week', current_date)::date as d
)
select
  (select count(*)
   from performances p
   where p.performancedate >= (select d from year_start))::int as gigs_ytd,
  (select count(*) from performances)::int as gigs_lifetime,
  coalesce((select sum(coalesce(vrs.effective_minutes, 0))
            from view_recent_sessions vrs
            where vrs.session_date >= (select d from week_start)
              and vrs.session_type = 'solo_practice'), 0)::int as solo_practice_minutes_weekly,
  coalesce((select sum(coalesce(vrs.effective_minutes, 0))
            from view_recent_sessions vrs
            where vrs.session_date >= (select d from year_start)
              and vrs.session_type in ('solo_practice', 'singing_lesson')), 0)::int as practice_minutes_ytd,
  coalesce((select sum(coalesce(vrs.effective_minutes, 0))
            from view_recent_sessions vrs
            where vrs.session_type in ('solo_practice', 'singing_lesson')), 0)::int as practice_minutes_lifetime,
  coalesce((select count(sp.song_id)
            from song_performances sp
            join performances p on p.id = sp.performance_id
            join songs s on s.title = sp.song_id
            where p.performancedate >= (select d from year_start)
              and s.artist = 'Farhan Mannan'), 0)::int as songs_performed_live_ytd,
  coalesce((select count(sp.song_id)
            from song_performances sp
            join performances p on p.id = sp.performance_id
            join songs s on s.title = sp.song_id
            where s.artist = 'Farhan Mannan'), 0)::int as songs_performed_live_lifetime,
  coalesce((select count(*)
            from view_recent_sessions vrs
            where vrs.session_date >= (select d from year_start)), 0)::int as sessions_ytd;
