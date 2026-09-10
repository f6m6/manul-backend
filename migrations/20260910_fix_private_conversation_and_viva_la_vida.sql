-- Fix title typo from the Fluency Practice import and set Viva La Vida's artist
BEGIN;

UPDATE songs SET title = 'Private Conversation' WHERE title = 'Private Converation';
UPDATE songs SET artist = 'Coldplay' WHERE title = 'Viva La Vida' AND artist IS DISTINCT FROM 'Coldplay';

COMMIT;
