-- Add practice sessions and next songs to practise.

BEGIN;

CREATE TABLE IF NOT EXISTS practice_sessions (
  id serial PRIMARY KEY,
  practiced_on date NOT NULL DEFAULT CURRENT_DATE,
  total_minutes integer
);

CREATE TABLE IF NOT EXISTS practice_session_songs (
  practice_session_id integer NOT NULL REFERENCES practice_sessions(id) ON DELETE CASCADE,
  song_title character varying(50) NOT NULL REFERENCES songs(title) ON DELETE CASCADE,
  position integer NOT NULL,
  minutes integer,
  PRIMARY KEY (practice_session_id, song_title)
);

CREATE OR REPLACE VIEW view_practice_song_counts AS
SELECT song_title,
       COUNT(*)::bigint AS count
FROM practice_session_songs
GROUP BY song_title;

CREATE OR REPLACE VIEW view_practice_last_practiced AS
SELECT pss.song_title,
       MAX(ps.practiced_on) AS last_practiced
FROM practice_session_songs pss
JOIN practice_sessions ps ON ps.id = pss.practice_session_id
GROUP BY pss.song_title;

CREATE OR REPLACE VIEW view_next_songs_to_practise AS
SELECT s.title AS song_id,
       COALESCE(vpsc.count, 0)::bigint AS count,
       vplp.last_practiced,
       s.active,
       s.cover
FROM songs s
LEFT JOIN view_practice_song_counts vpsc ON vpsc.song_title = s.title
LEFT JOIN view_practice_last_practiced vplp ON vplp.song_title = s.title
WHERE s.active = true
ORDER BY COALESCE(vpsc.count, 0), vplp.last_practiced DESC;

COMMIT;
