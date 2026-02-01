-- Set active songs to Mirror You tracks + selected covers.

BEGIN;

-- Ensure cover songs exist.
INSERT INTO songs (title, cover, active, key, length, instrumental)
VALUES
  ('6th Avenue Heartache', true, true, NULL, NULL, false),
  ('Bleeders', true, true, NULL, NULL, false),
  ('Three Marlenas', true, true, NULL, NULL, false),
  ('Invisible City', true, true, NULL, NULL, false),
  ('Take On Me', true, true, NULL, NULL, false)
ON CONFLICT (title) DO NOTHING;

-- Mark everything inactive first.
UPDATE songs SET active = false;

-- Reactivate Mirror You album tracks.
UPDATE songs
SET active = true
WHERE title IN (
  SELECT s.title
  FROM albums a
  JOIN album_songs asg ON asg.album_id = a.id
  JOIN songs s ON s.title = asg.song_title
  WHERE a.title = 'Mirror You'
);

-- Reactivate selected covers.
UPDATE songs
SET active = true
WHERE title IN (
  '6th Avenue Heartache',
  'Bleeders',
  'Three Marlenas',
  'Invisible City',
  'Take On Me'
);

COMMIT;
