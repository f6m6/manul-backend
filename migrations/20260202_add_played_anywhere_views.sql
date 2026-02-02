-- Unify "played anywhere" across performances, solo practice, and singing lessons.

CREATE OR REPLACE VIEW view_song_play_events AS
SELECT sp.song_id::text AS song_id,
       p.performancedate::date AS played_on
FROM song_performances sp
JOIN performances p ON p.id = sp.performance_id

UNION ALL

SELECT COALESCE(pss.song_id, s.title)::text AS song_id,
       ps.practiced_on::date AS played_on
FROM practice_session_songs pss
JOIN practice_sessions ps ON ps.id = pss.practice_session_id
LEFT JOIN songs s ON s.title = pss.song_title

UNION ALL

SELECT s.title::text AS song_id,
       sl.lesson_date::date AS played_on
FROM singing_lesson_songs sls
JOIN singing_lessons sl ON sl.id = sls.singing_lesson_id
LEFT JOIN songs s ON s.title = sls.song_title;

CREATE OR REPLACE VIEW view_song_play_anywhere_counts AS
SELECT song_id,
       COUNT(*)::bigint AS count
FROM view_song_play_events
GROUP BY song_id;

CREATE OR REPLACE VIEW view_song_last_played_anywhere AS
SELECT song_id,
       MAX(played_on) AS last_played_anywhere
FROM view_song_play_events
GROUP BY song_id;

CREATE OR REPLACE VIEW view_next_songs_to_play_anywhere AS
SELECT s.title AS song_id,
       s.title,
       COALESCE(vsp.count, 0)::bigint AS count,
       vslp.last_played_anywhere,
       s.active,
       s.cover
FROM songs s
LEFT JOIN view_song_play_anywhere_counts vsp ON vsp.song_id = s.title
LEFT JOIN view_song_last_played_anywhere vslp ON vslp.song_id = s.title
WHERE s.active = true
ORDER BY COALESCE(vsp.count, 0), vslp.last_played_anywhere DESC;

DROP VIEW IF EXISTS view_next_songs_to_practise;

CREATE VIEW view_next_songs_to_practise AS
SELECT * FROM view_next_songs_to_play_anywhere;
