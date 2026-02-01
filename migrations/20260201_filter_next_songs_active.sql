-- Ensure next songs view only returns active songs.

CREATE OR REPLACE VIEW view_next_songs_to_play AS
SELECT next_song.song_id,
       next_song.count,
       next_song.last_played,
       next_song.active,
       next_song.cover
FROM next_song
WHERE next_song.active = true;
