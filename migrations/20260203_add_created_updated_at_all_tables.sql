-- Add created_at and updated_at timestamps to all tables and keep updated_at current.

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Helper pattern applied per table.
-- Albums
ALTER TABLE albums ADD COLUMN IF NOT EXISTS created_at timestamptz;
ALTER TABLE albums ADD COLUMN IF NOT EXISTS updated_at timestamptz;
UPDATE albums SET created_at = COALESCE(created_at, now()),
                 updated_at = COALESCE(updated_at, created_at, now());
ALTER TABLE albums ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE albums ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE albums ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE albums ALTER COLUMN updated_at SET NOT NULL;
DROP TRIGGER IF EXISTS set_updated_at_on_albums ON albums;
CREATE TRIGGER set_updated_at_on_albums
  BEFORE UPDATE ON albums
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();

-- Album songs
ALTER TABLE album_songs ADD COLUMN IF NOT EXISTS created_at timestamptz;
ALTER TABLE album_songs ADD COLUMN IF NOT EXISTS updated_at timestamptz;
UPDATE album_songs SET created_at = COALESCE(created_at, now()),
                      updated_at = COALESCE(updated_at, created_at, now());
ALTER TABLE album_songs ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE album_songs ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE album_songs ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE album_songs ALTER COLUMN updated_at SET NOT NULL;
DROP TRIGGER IF EXISTS set_updated_at_on_album_songs ON album_songs;
CREATE TRIGGER set_updated_at_on_album_songs
  BEFORE UPDATE ON album_songs
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();

-- Songs
ALTER TABLE songs ADD COLUMN IF NOT EXISTS created_at timestamptz;
ALTER TABLE songs ADD COLUMN IF NOT EXISTS updated_at timestamptz;
UPDATE songs SET created_at = COALESCE(created_at, now()),
                updated_at = COALESCE(updated_at, created_at, now());
ALTER TABLE songs ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE songs ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE songs ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE songs ALTER COLUMN updated_at SET NOT NULL;
DROP TRIGGER IF EXISTS set_updated_at_on_songs ON songs;
CREATE TRIGGER set_updated_at_on_songs
  BEFORE UPDATE ON songs
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();

-- Venues
ALTER TABLE venues ADD COLUMN IF NOT EXISTS created_at timestamptz;
ALTER TABLE venues ADD COLUMN IF NOT EXISTS updated_at timestamptz;
UPDATE venues SET created_at = COALESCE(created_at, now()),
                 updated_at = COALESCE(updated_at, created_at, now());
ALTER TABLE venues ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE venues ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE venues ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE venues ALTER COLUMN updated_at SET NOT NULL;
DROP TRIGGER IF EXISTS set_updated_at_on_venues ON venues;
CREATE TRIGGER set_updated_at_on_venues
  BEFORE UPDATE ON venues
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();

-- Performances
ALTER TABLE performances ADD COLUMN IF NOT EXISTS created_at timestamptz;
ALTER TABLE performances ADD COLUMN IF NOT EXISTS updated_at timestamptz;
UPDATE performances SET created_at = COALESCE(created_at, performancedate::timestamptz, now()),
                       updated_at = COALESCE(updated_at, created_at, now());
ALTER TABLE performances ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE performances ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE performances ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE performances ALTER COLUMN updated_at SET NOT NULL;
DROP TRIGGER IF EXISTS set_updated_at_on_performances ON performances;
CREATE TRIGGER set_updated_at_on_performances
  BEFORE UPDATE ON performances
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();

-- Song performances
ALTER TABLE song_performances ADD COLUMN IF NOT EXISTS created_at timestamptz;
ALTER TABLE song_performances ADD COLUMN IF NOT EXISTS updated_at timestamptz;
UPDATE song_performances SET created_at = COALESCE(created_at, now()),
                            updated_at = COALESCE(updated_at, created_at, now());
ALTER TABLE song_performances ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE song_performances ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE song_performances ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE song_performances ALTER COLUMN updated_at SET NOT NULL;
DROP TRIGGER IF EXISTS set_updated_at_on_song_performances ON song_performances;
CREATE TRIGGER set_updated_at_on_song_performances
  BEFORE UPDATE ON song_performances
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();

-- Practice sessions
ALTER TABLE practice_sessions ADD COLUMN IF NOT EXISTS created_at timestamptz;
ALTER TABLE practice_sessions ADD COLUMN IF NOT EXISTS updated_at timestamptz;
UPDATE practice_sessions SET created_at = COALESCE(created_at, practiced_on::timestamptz, now()),
                            updated_at = COALESCE(updated_at, created_at, now());
ALTER TABLE practice_sessions ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE practice_sessions ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE practice_sessions ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE practice_sessions ALTER COLUMN updated_at SET NOT NULL;
DROP TRIGGER IF EXISTS set_updated_at_on_practice_sessions ON practice_sessions;
CREATE TRIGGER set_updated_at_on_practice_sessions
  BEFORE UPDATE ON practice_sessions
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();

-- Practice session songs
ALTER TABLE practice_session_songs ADD COLUMN IF NOT EXISTS created_at timestamptz;
ALTER TABLE practice_session_songs ADD COLUMN IF NOT EXISTS updated_at timestamptz;
UPDATE practice_session_songs SET created_at = COALESCE(created_at, now()),
                                 updated_at = COALESCE(updated_at, created_at, now());
ALTER TABLE practice_session_songs ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE practice_session_songs ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE practice_session_songs ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE practice_session_songs ALTER COLUMN updated_at SET NOT NULL;
DROP TRIGGER IF EXISTS set_updated_at_on_practice_session_songs ON practice_session_songs;
CREATE TRIGGER set_updated_at_on_practice_session_songs
  BEFORE UPDATE ON practice_session_songs
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();

-- Singing lessons
ALTER TABLE singing_lessons ADD COLUMN IF NOT EXISTS created_at timestamptz;
ALTER TABLE singing_lessons ADD COLUMN IF NOT EXISTS updated_at timestamptz;
UPDATE singing_lessons SET created_at = COALESCE(created_at, lesson_date::timestamptz, now()),
                          updated_at = COALESCE(updated_at, created_at, now());
ALTER TABLE singing_lessons ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE singing_lessons ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE singing_lessons ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE singing_lessons ALTER COLUMN updated_at SET NOT NULL;
DROP TRIGGER IF EXISTS set_updated_at_on_singing_lessons ON singing_lessons;
CREATE TRIGGER set_updated_at_on_singing_lessons
  BEFORE UPDATE ON singing_lessons
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();

-- Singing lesson songs
ALTER TABLE singing_lesson_songs ADD COLUMN IF NOT EXISTS created_at timestamptz;
ALTER TABLE singing_lesson_songs ADD COLUMN IF NOT EXISTS updated_at timestamptz;
UPDATE singing_lesson_songs SET created_at = COALESCE(created_at, now()),
                               updated_at = COALESCE(updated_at, created_at, now());
ALTER TABLE singing_lesson_songs ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE singing_lesson_songs ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE singing_lesson_songs ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE singing_lesson_songs ALTER COLUMN updated_at SET NOT NULL;
DROP TRIGGER IF EXISTS set_updated_at_on_singing_lesson_songs ON singing_lesson_songs;
CREATE TRIGGER set_updated_at_on_singing_lesson_songs
  BEFORE UPDATE ON singing_lesson_songs
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();

-- Session types
ALTER TABLE session_types ADD COLUMN IF NOT EXISTS created_at timestamptz;
ALTER TABLE session_types ADD COLUMN IF NOT EXISTS updated_at timestamptz;
UPDATE session_types SET created_at = COALESCE(created_at, now()),
                        updated_at = COALESCE(updated_at, created_at, now());
ALTER TABLE session_types ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE session_types ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE session_types ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE session_types ALTER COLUMN updated_at SET NOT NULL;
DROP TRIGGER IF EXISTS set_updated_at_on_session_types ON session_types;
CREATE TRIGGER set_updated_at_on_session_types
  BEFORE UPDATE ON session_types
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();

-- Sessions
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS created_at timestamptz;
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS updated_at timestamptz;
UPDATE sessions SET created_at = COALESCE(created_at, now()),
                   updated_at = COALESCE(updated_at, created_at, now());
ALTER TABLE sessions ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE sessions ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE sessions ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE sessions ALTER COLUMN updated_at SET NOT NULL;
DROP TRIGGER IF EXISTS set_updated_at_on_sessions ON sessions;
CREATE TRIGGER set_updated_at_on_sessions
  BEFORE UPDATE ON sessions
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();

-- Manual fix for 2026-02-03 session ordering (local time)
UPDATE practice_sessions
SET created_at = '2026-02-03 10:30:00'::timestamptz,
    updated_at = GREATEST(updated_at, '2026-02-03 10:30:00'::timestamptz)
WHERE id = 2 AND practiced_on = '2026-02-03';

UPDATE singing_lessons
SET created_at = '2026-02-03 14:30:00'::timestamptz,
    updated_at = GREATEST(updated_at, '2026-02-03 14:30:00'::timestamptz)
WHERE id = 2 AND lesson_date = '2026-02-03';
