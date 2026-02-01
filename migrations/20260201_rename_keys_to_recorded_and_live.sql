-- Rename key fields and merge legacy key into recorded_key
ALTER TABLE songs RENAME COLUMN original_key TO recorded_key;
ALTER TABLE songs RENAME COLUMN my_key TO my_live_key;

UPDATE songs
SET recorded_key = COALESCE(recorded_key, "key");

UPDATE songs
SET "key" = recorded_key
WHERE recorded_key IS NOT NULL;
