-- Include cover flag and all songs (even without plays) in next_song views.
-- Apply with: psql -U postgres -d manul -f migrations/20260130_next_song_views_include_cover_and_all_songs.sql

CREATE OR REPLACE VIEW next_song AS
SELECT
  s.title::text AS song_id,
  COALESCE(vsp.count, 0) AS count,
  vslp.last_played,
  s.active,
  s.cover
FROM songs s
LEFT JOIN view_song_plays vsp
  ON vsp.song_id = s.title::text
LEFT JOIN view_song_last_played vslp
  ON vslp.song_id = s.title::text
ORDER BY COALESCE(vsp.count, 0), vslp.last_played DESC;

CREATE OR REPLACE VIEW view_next_songs_to_play AS
SELECT
  next_song.song_id,
  next_song.count,
  next_song.last_played,
  next_song.active,
  next_song.cover
FROM next_song;
