-- Mark Viva La Vida as a cover and merge duplicate song rows.
-- Now We’re Right (curly, from the busking import) duplicated Now We're Right, which holds the performances.
-- Sittin' on the Dock of the Bay duplicated (Sittin' On) The Dock Of The Bay from the Fluency Practice import.
BEGIN;

UPDATE songs SET cover = true WHERE title = 'Viva La Vida' AND cover IS DISTINCT FROM true;

-- Now We're Right: keep the straight-apostrophe row, take the length from the duplicate.
UPDATE songs s
SET length = COALESCE(s.length, d.length)
FROM songs d
WHERE s.title = 'Now We''re Right' AND d.title = 'Now We’re Right' AND s.length IS NULL;

UPDATE song_performances SET song_id = 'Now We''re Right' WHERE song_id = 'Now We’re Right';
DELETE FROM songs WHERE title = 'Now We’re Right';

-- Dock of the Bay: keep the Notion title (which has the keys) and move the performance history onto it.
UPDATE songs s
SET length = COALESCE(s.length, d.length),
    bpm = COALESCE(s.bpm, d.bpm),
    capo = COALESCE(s.capo, d.capo)
FROM songs d
WHERE s.title = '(Sittin'' On) The Dock Of The Bay' AND d.title = 'Sittin'' on the Dock of the Bay';

UPDATE song_performances SET song_id = '(Sittin'' On) The Dock Of The Bay' WHERE song_id = 'Sittin'' on the Dock of the Bay';
DELETE FROM songs WHERE title = 'Sittin'' on the Dock of the Bay';

COMMIT;
