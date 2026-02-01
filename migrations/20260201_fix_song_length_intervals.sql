-- Fix interval values stored as hours:minutes instead of minutes:seconds.

BEGIN;

UPDATE songs
SET length = make_interval(mins => EXTRACT(hour from length)::int,
                           secs => EXTRACT(minute from length)::int)
WHERE length IS NOT NULL
  AND EXTRACT(hour from length) > 0
  AND EXTRACT(second from length) = 0
  AND EXTRACT(minute from length) BETWEEN 0 AND 59
  AND EXTRACT(hour from length) <= 15;

COMMIT;
