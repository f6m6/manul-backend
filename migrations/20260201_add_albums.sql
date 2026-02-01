-- Add albums and album_songs for tracking release tracklists.

BEGIN;

CREATE TABLE IF NOT EXISTS albums (
  id serial PRIMARY KEY,
  title character varying(80) NOT NULL UNIQUE,
  artist character varying(80),
  release_date date
);

CREATE TABLE IF NOT EXISTS album_songs (
  album_id integer NOT NULL REFERENCES albums(id) ON DELETE CASCADE,
  song_title character varying(50) NOT NULL REFERENCES songs(title) ON DELETE CASCADE,
  track_number integer NOT NULL,
  PRIMARY KEY (album_id, song_title),
  UNIQUE (album_id, track_number)
);

INSERT INTO albums (title, artist)
VALUES ('Mirror You', 'Farhan Mannan')
ON CONFLICT (title) DO NOTHING;

WITH album AS (
  SELECT id FROM albums WHERE title = 'Mirror You'
),
tracks AS (
  VALUES
    ('White Mirror', 1),
    ('Hide', 2),
    ('Strong Like You', 3),
    ('Sweet Nothing', 4),
    ('Quiet Eye', 5),
    ('Ride Or Die', 6),
    ('Call Me', 7),
    ('Clean', 8),
    ('Speak Your Language', 9),
    ('Keep Burning', 10)
)
INSERT INTO album_songs (album_id, song_title, track_number)
SELECT album.id, tracks.column1, tracks.column2
FROM album
JOIN tracks ON true
JOIN songs ON songs.title = tracks.column1
ON CONFLICT (album_id, song_title) DO NOTHING;

COMMIT;
