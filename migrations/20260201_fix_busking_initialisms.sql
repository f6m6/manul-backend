-- Replace busking setlist initialisms with full song titles when uniquely matched.

BEGIN;

UPDATE song_performances
SET song_id = 'Dancing In The Dark'
WHERE song_id = 'DITD';

UPDATE song_performances
SET song_id = 'Don’t Look Back In Anger'
WHERE song_id = 'DLBIA';

UPDATE song_performances
SET song_id = 'Fly Away'
WHERE song_id = 'FA';

UPDATE song_performances
SET song_id = '(What A) Wonderful World'
WHERE song_id = 'WAWW';

UPDATE song_performances
SET song_id = 'White Mirror'
WHERE song_id = 'WM';

UPDATE song_performances
SET song_id = 'Your Song'
WHERE song_id = 'YS';

-- Remove orphaned acronym song rows if nothing references them anymore.
DELETE FROM songs
WHERE title IN ('DITD', 'DLBIA', 'FA', 'WAWW', 'WM', 'YS')
  AND NOT EXISTS (
    SELECT 1
    FROM song_performances sp
    WHERE sp.song_id = songs.title
  );

COMMIT;
