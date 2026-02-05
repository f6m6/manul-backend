-- Add actual/minimum minutes to recent sessions view for consistent reporting.

DROP VIEW IF EXISTS view_recent_sessions;
CREATE VIEW view_recent_sessions AS
SELECT
  'performance'::text AS session_type,
  p.id AS session_id,
  p.performancedate::date AS session_date,
  p.created_at AS session_created_at,
  p.venue::text AS session_label,
  COUNT(sp.song_id)::bigint AS song_count,
  COALESCE(CEIL(SUM(COALESCE(EXTRACT(EPOCH FROM s.length), 240)) / 60.0)::int, 0) AS minimum_minutes,
  NULL::int AS actual_minutes,
  COALESCE(CEIL(SUM(COALESCE(EXTRACT(EPOCH FROM s.length), 240)) / 60.0)::int, 0) AS effective_minutes
FROM performances p
JOIN song_performances sp ON sp.performance_id = p.id
LEFT JOIN songs s ON s.title = sp.song_id
GROUP BY p.id

UNION ALL

SELECT
  'solo_practice'::text AS session_type,
  ps.id AS session_id,
  ps.practiced_on::date AS session_date,
  ps.created_at AS session_created_at,
  NULL::text AS session_label,
  COUNT(pss.song_id)::bigint AS song_count,
  COALESCE(CEIL(SUM(COALESCE(pss.minutes,
                             (EXTRACT(EPOCH FROM s.length) / 60.0),
                             4)) )::int, 0) AS minimum_minutes,
  ps.total_minutes::int AS actual_minutes,
  COALESCE(ps.total_minutes::int,
           COALESCE(CEIL(SUM(COALESCE(pss.minutes,
                                      (EXTRACT(EPOCH FROM s.length) / 60.0),
                                      4)) )::int, 0)) AS effective_minutes
FROM practice_sessions ps
JOIN practice_session_songs pss ON pss.practice_session_id = ps.id
LEFT JOIN songs s ON s.title = COALESCE(pss.song_id, pss.song_title)
GROUP BY ps.id

UNION ALL

SELECT
  'singing_lesson'::text AS session_type,
  sl.id AS session_id,
  sl.lesson_date::date AS session_date,
  sl.created_at AS session_created_at,
  NULL::text AS session_label,
  COUNT(sls.song_title)::bigint AS song_count,
  COALESCE(sl.duration_minutes, 120)::int AS minimum_minutes,
  sl.duration_minutes::int AS actual_minutes,
  COALESCE(sl.duration_minutes, 120)::int AS effective_minutes
FROM singing_lessons sl
LEFT JOIN singing_lesson_songs sls ON sls.singing_lesson_id = sl.id
GROUP BY sl.id;
