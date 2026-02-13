-- Add normalized optional per-song metadata for solo practice sessions.
-- Designed as composable tables so the pattern can extend to other session types later.

BEGIN;

CREATE TABLE IF NOT EXISTS instruments (
  id serial PRIMARY KEY,
  name text NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS practice_vocal_modes (
  id serial PRIMARY KEY,
  code text NOT NULL UNIQUE,
  label text NOT NULL UNIQUE
);

INSERT INTO practice_vocal_modes (code, label)
VALUES
  ('instrumental_only', 'Instrumental only'),
  ('sing_and_play', 'Sing and play simultaneously'),
  ('sing_over_instrumental', 'Sing over instrumental backing')
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS practice_song_details (
  practice_session_id integer NOT NULL,
  song_title character varying(50) NOT NULL,
  instrument_id integer REFERENCES instruments(id) ON DELETE SET NULL,
  vocal_mode_id integer REFERENCES practice_vocal_modes(id) ON DELETE SET NULL,
  capo_position integer,
  used_metronome boolean,
  notes text,
  PRIMARY KEY (practice_session_id, song_title),
  CONSTRAINT fk_practice_song_details_song
    FOREIGN KEY (practice_session_id, song_title)
    REFERENCES practice_session_songs(practice_session_id, song_title)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT chk_practice_song_details_capo
    CHECK (capo_position IS NULL OR (capo_position >= 0 AND capo_position <= 24))
);

CREATE TABLE IF NOT EXISTS practice_song_tempos (
  practice_session_id integer NOT NULL,
  song_title character varying(50) NOT NULL,
  ordinal integer NOT NULL,
  bpm integer NOT NULL,
  PRIMARY KEY (practice_session_id, song_title, ordinal),
  CONSTRAINT fk_practice_song_tempos_details
    FOREIGN KEY (practice_session_id, song_title)
    REFERENCES practice_song_details(practice_session_id, song_title)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT chk_practice_song_tempos_ordinal
    CHECK (ordinal > 0),
  CONSTRAINT chk_practice_song_tempos_bpm
    CHECK (bpm > 0 AND bpm <= 400)
);

CREATE TABLE IF NOT EXISTS practice_song_keys (
  practice_session_id integer NOT NULL,
  song_title character varying(50) NOT NULL,
  ordinal integer NOT NULL,
  key_name text NOT NULL,
  PRIMARY KEY (practice_session_id, song_title, ordinal),
  CONSTRAINT fk_practice_song_keys_details
    FOREIGN KEY (practice_session_id, song_title)
    REFERENCES practice_song_details(practice_session_id, song_title)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT chk_practice_song_keys_ordinal
    CHECK (ordinal > 0),
  CONSTRAINT chk_practice_song_keys_key_name
    CHECK (length(trim(key_name)) > 0)
);

COMMIT;
