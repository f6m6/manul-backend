-- Add views for mirror you stats (live + practice).

CREATE OR REPLACE VIEW view_song_last_performed_live AS
SELECT sp.song_id,
       MAX(p.performancedate) AS last_performed_live
FROM song_performances sp
JOIN performances p ON p.id = sp.performance_id
GROUP BY sp.song_id;

CREATE OR REPLACE VIEW view_song_perform_live_counts AS
SELECT sp.song_id,
       COUNT(*)::bigint AS live_count
FROM song_performances sp
GROUP BY sp.song_id;

CREATE OR REPLACE VIEW view_song_last_practiced AS
SELECT pss.song_title,
       MAX(ps.practiced_on) AS last_practiced
FROM practice_session_songs pss
JOIN practice_sessions ps ON ps.id = pss.practice_session_id
GROUP BY pss.song_title;

CREATE OR REPLACE VIEW view_song_practice_counts AS
SELECT pss.song_title,
       COUNT(*)::bigint AS practice_count
FROM practice_session_songs pss
GROUP BY pss.song_title;
