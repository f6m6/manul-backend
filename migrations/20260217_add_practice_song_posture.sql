-- Add optional practice posture to per-song solo practice metadata.

BEGIN;

ALTER TABLE practice_song_details
  ADD COLUMN IF NOT EXISTS practice_posture text;

ALTER TABLE practice_song_details
  DROP CONSTRAINT IF EXISTS chk_practice_song_details_posture;

ALTER TABLE practice_song_details
  ADD CONSTRAINT chk_practice_song_details_posture
  CHECK (
    practice_posture IS NULL
    OR practice_posture IN ('sitting', 'standing')
  );

COMMIT;

