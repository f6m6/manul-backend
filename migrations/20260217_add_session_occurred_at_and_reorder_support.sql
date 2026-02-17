-- Add canonical event datetime fields and expose them in recent sessions view.
-- Sentinel times are used for existing date-only rows until direct datetime editing lands.

ALTER TABLE performances ADD COLUMN IF NOT EXISTS occurred_at timestamptz;
UPDATE performances
SET occurred_at = COALESCE(occurred_at, performancedate::timestamptz + interval '20 hours');
ALTER TABLE performances ALTER COLUMN occurred_at SET DEFAULT now();
ALTER TABLE performances ALTER COLUMN occurred_at SET NOT NULL;

ALTER TABLE practice_sessions ADD COLUMN IF NOT EXISTS occurred_at timestamptz;
UPDATE practice_sessions
SET occurred_at = COALESCE(occurred_at, practiced_on::timestamptz + interval '10 hours');
ALTER TABLE practice_sessions ALTER COLUMN occurred_at SET DEFAULT now();
ALTER TABLE practice_sessions ALTER COLUMN occurred_at SET NOT NULL;

ALTER TABLE singing_lessons ADD COLUMN IF NOT EXISTS occurred_at timestamptz;
UPDATE singing_lessons
SET occurred_at = COALESCE(occurred_at, lesson_date::timestamptz + interval '15 hours');
ALTER TABLE singing_lessons ALTER COLUMN occurred_at SET DEFAULT now();
ALTER TABLE singing_lessons ALTER COLUMN occurred_at SET NOT NULL;

-- Keep existing column order stable and append session_occurred_at so dependent views stay valid.
CREATE OR REPLACE VIEW view_recent_sessions AS
SELECT
  'performance'::text AS session_type,
  p.id AS session_id,
  p.performancedate::date AS session_date,
  p.created_at AS session_created_at,
  p.venue::text AS session_label,
  COUNT(sp.song_id)::bigint AS song_count,
  COALESCE(CEIL(SUM(COALESCE(EXTRACT(EPOCH FROM s.length), 240)) / 60.0)::int, 0) AS minimum_minutes,
  COALESCE(CEIL(SUM(COALESCE(EXTRACT(EPOCH FROM s.length), 240)) / 60.0)::int, 0) AS actual_minutes,
  COALESCE(CEIL(SUM(COALESCE(EXTRACT(EPOCH FROM s.length), 240)) / 60.0)::int, 0) AS effective_minutes,
  p.occurred_at AS session_occurred_at
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
  COALESCE(SUM(COALESCE(pss.minutes, CEIL((EXTRACT(EPOCH FROM s.length) / 60.0)::numeric), 4))::int, 0) AS minimum_minutes,
  COALESCE(ps.total_minutes,
           SUM(COALESCE(pss.minutes, CEIL((EXTRACT(EPOCH FROM s.length) / 60.0)::numeric), 4))::int,
           0) AS actual_minutes,
  COALESCE(ps.total_minutes,
           SUM(COALESCE(pss.minutes, CEIL((EXTRACT(EPOCH FROM s.length) / 60.0)::numeric), 4))::int,
           0) AS effective_minutes,
  ps.occurred_at AS session_occurred_at
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
  COALESCE(sl.duration_minutes, 120)::int AS actual_minutes,
  COALESCE(sl.duration_minutes, 120)::int AS effective_minutes,
  sl.occurred_at AS session_occurred_at
FROM singing_lessons sl
LEFT JOIN singing_lesson_songs sls ON sls.singing_lesson_id = sl.id
GROUP BY sl.id;
