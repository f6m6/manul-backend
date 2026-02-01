-- Ensure song_title FKs update on song rename.

BEGIN;

ALTER TABLE album_songs
  DROP CONSTRAINT IF EXISTS album_songs_song_title_fkey;

ALTER TABLE album_songs
  ADD CONSTRAINT album_songs_song_title_fkey
  FOREIGN KEY (song_title)
  REFERENCES songs(title)
  ON DELETE CASCADE
  ON UPDATE CASCADE;

ALTER TABLE practice_session_songs
  DROP CONSTRAINT IF EXISTS practice_session_songs_song_title_fkey;

ALTER TABLE practice_session_songs
  ADD CONSTRAINT practice_session_songs_song_title_fkey
  FOREIGN KEY (song_title)
  REFERENCES songs(title)
  ON DELETE CASCADE
  ON UPDATE CASCADE;

COMMIT;
