-- Add song_id linkage for practice_session_songs with backfill and updated views.

BEGIN;

ALTER TABLE practice_session_songs
  ADD COLUMN IF NOT EXISTS song_id integer REFERENCES songs(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_practice_session_songs_song_id
  ON practice_session_songs(song_id);

UPDATE practice_session_songs pss
SET song_id = s.id
FROM songs s
WHERE pss.song_id IS NULL
  AND s.title = pss.song_title;

CREATE OR REPLACE VIEW view_practice_song_counts AS
SELECT COALESCE(pss.song_id, s.id) AS song_id,
       COUNT(*)::bigint AS count
FROM practice_session_songs pss
LEFT JOIN songs s ON s.title = pss.song_title
GROUP BY COALESCE(pss.song_id, s.id);

CREATE OR REPLACE VIEW view_practice_last_practiced AS
SELECT COALESCE(pss.song_id, s.id) AS song_id,
       MAX(ps.practiced_on) AS last_practiced
FROM practice_session_songs pss
JOIN practice_sessions ps ON ps.id = pss.practice_session_id
LEFT JOIN songs s ON s.title = pss.song_title
GROUP BY COALESCE(pss.song_id, s.id);

CREATE OR REPLACE VIEW view_next_songs_to_practise AS
SELECT s.id AS song_id,
       s.title,
       COALESCE(vpsc.count, 0)::bigint AS count,
       vplp.last_practiced,
       s.active,
       s.cover
FROM songs s
LEFT JOIN view_practice_song_counts vpsc ON vpsc.song_id = s.id
LEFT JOIN view_practice_last_practiced vplp ON vplp.song_id = s.id
WHERE s.active = true
ORDER BY COALESCE(vpsc.count, 0), vplp.last_practiced DESC;

CREATE OR REPLACE VIEW view_song_last_practiced AS
SELECT COALESCE(pss.song_id, s.id) AS song_id,
       MAX(ps.practiced_on) AS last_practiced
FROM practice_session_songs pss
JOIN practice_sessions ps ON ps.id = pss.practice_session_id
LEFT JOIN songs s ON s.title = pss.song_title
GROUP BY COALESCE(pss.song_id, s.id);

CREATE OR REPLACE VIEW view_song_practice_counts AS
SELECT COALESCE(pss.song_id, s.id) AS song_id,
       COUNT(*)::bigint AS practice_count
FROM practice_session_songs pss
LEFT JOIN songs s ON s.title = pss.song_title
GROUP BY COALESCE(pss.song_id, s.id);

COMMIT;
