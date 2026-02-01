-- Fix song titles to match repertoire and set artists (handle FK via delete+insert).

BEGIN;

-- Sittin' on the Dock of the Bay
INSERT INTO songs (title, cover, active, key, length, instrumental, artist, bpm, original_key, my_key, capo)
SELECT
  'Sittin'' on the Dock of the Bay', cover, active, key, length, instrumental, artist, bpm, original_key, my_key, capo
FROM songs
WHERE title = 'Sitting on the Dock of the Bay'
ON CONFLICT (title) DO NOTHING;

UPDATE song_performances
SET song_id = 'Sittin'' on the Dock of the Bay'
WHERE song_id = 'Sitting on the Dock of the Bay';

DELETE FROM songs WHERE title = 'Sitting on the Dock of the Bay';

-- Why Does It Always Rain On Me?
INSERT INTO songs (title, cover, active, key, length, instrumental, artist, bpm, original_key, my_key, capo)
SELECT
  'Why Does It Always Rain On Me?', cover, active, key, length, instrumental, artist, bpm, original_key, my_key, capo
FROM songs
WHERE title = 'Why Does It Always Rain on Me'
ON CONFLICT (title) DO NOTHING;

UPDATE song_performances
SET song_id = 'Why Does It Always Rain On Me?'
WHERE song_id = 'Why Does It Always Rain on Me';

DELETE FROM songs WHERE title = 'Why Does It Always Rain on Me';

-- Set artists for corrected titles.
UPDATE songs SET artist = 'Otis Redding'
WHERE title = 'Sittin'' on the Dock of the Bay';

UPDATE songs SET artist = 'Travis'
WHERE title = 'Why Does It Always Rain On Me?';

UPDATE songs SET artist = 'Sade'
WHERE title = 'Smooth Operator'
  AND (artist IS NULL OR artist = '' OR artist = '¤');

COMMIT;
