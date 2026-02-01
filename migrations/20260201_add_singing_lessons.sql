-- Add singing lessons with song linkage.

BEGIN;

CREATE TABLE IF NOT EXISTS singing_lessons (
  id serial PRIMARY KEY,
  lesson_date date NOT NULL DEFAULT CURRENT_DATE,
  duration_minutes integer NOT NULL DEFAULT 120
);

CREATE TABLE IF NOT EXISTS singing_lesson_songs (
  singing_lesson_id integer NOT NULL REFERENCES singing_lessons(id) ON DELETE CASCADE,
  song_title character varying(50) NOT NULL REFERENCES songs(title) ON DELETE CASCADE,
  position integer NOT NULL,
  PRIMARY KEY (singing_lesson_id, song_title)
);

COMMIT;
