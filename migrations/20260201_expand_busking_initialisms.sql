-- Expand remaining busking setlist initialisms to full titles.

BEGIN;

INSERT INTO songs (title, cover, active, key, length, instrumental)
VALUES
  ('Babylon', true, true, NULL, NULL, false),
  ('Brown Eyed Girl', true, true, NULL, NULL, false),
  ('Blinding Lights', true, true, NULL, NULL, false),
  ('I Still Haven''t Found What I''m Looking For', true, true, NULL, NULL, false),
  ('Sitting on the Dock of the Bay', true, true, NULL, NULL, false),
  ('Why Does It Always Rain on Me', true, true, NULL, NULL, false),
  ('Torn', true, true, NULL, NULL, false)
ON CONFLICT (title) DO NOTHING;

UPDATE song_performances SET song_id = 'Babylon' WHERE song_id = 'B';
UPDATE song_performances SET song_id = 'Babylon' WHERE song_id = 'B x2';
UPDATE song_performances SET song_id = 'Brown Eyed Girl' WHERE song_id = 'BEG';
UPDATE song_performances SET song_id = 'Brown Eyed Girl' WHERE song_id = 'BEG x2';
UPDATE song_performances SET song_id = 'Blinding Lights' WHERE song_id = 'BL';
UPDATE song_performances SET song_id = 'I Still Haven''t Found What I''m Looking For' WHERE song_id = 'ISHFWILF';
UPDATE song_performances SET song_id = 'Sitting on the Dock of the Bay' WHERE song_id = 'SOTDOTB';
UPDATE song_performances SET song_id = 'Why Does It Always Rain on Me' WHERE song_id = 'WDIAROM';
UPDATE song_performances SET song_id = 'River' WHERE song_id = 'R';
UPDATE song_performances SET song_id = 'Torn' WHERE song_id = 'T';

DELETE FROM songs
WHERE title IN ('B', 'B x2', 'BEG', 'BEG x2', 'BL', 'ISHFWILF', 'SOTDOTB', 'WDIAROM', 'R', 'T')
  AND NOT EXISTS (
    SELECT 1
    FROM song_performances sp
    WHERE sp.song_id = songs.title
  );

COMMIT;
