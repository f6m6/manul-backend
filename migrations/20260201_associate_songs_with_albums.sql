-- Ensure albums exist
INSERT INTO albums (title, artist)
SELECT 'Mirror You', 'Farhan Mannan'
WHERE NOT EXISTS (SELECT 1 FROM albums WHERE title = 'Mirror You');

INSERT INTO albums (title, artist)
SELECT 'Bringing Down The Horse', 'The Wallflowers'
WHERE NOT EXISTS (SELECT 1 FROM albums WHERE title = 'Bringing Down The Horse');

-- Associate Mirror You tracks
WITH tracks AS (
  SELECT * FROM (VALUES
    (1, 'White Mirror'),
    (2, 'Hide'),
    (3, 'Strong Like You'),
    (4, 'Sweet Nothing'),
    (5, 'Quiet Eye'),
    (6, 'Ride Or Die'),
    (7, 'Call Me'),
    (8, 'Clean'),
    (9, 'Speak Your Language'),
    (10, 'Keep Burning')
  ) AS t(track_number, title)
)
INSERT INTO album_songs (album_id, song_title, track_number)
SELECT a.id, s.title, t.track_number
FROM albums a
JOIN tracks t ON true
JOIN songs s ON lower(s.title) = lower(t.title)
WHERE a.title = 'Mirror You'
  AND NOT EXISTS (
    SELECT 1
    FROM album_songs existing
    WHERE existing.album_id = a.id
      AND existing.song_title = s.title
  );

-- Associate Wallflowers album songs
WITH tracks AS (
  SELECT * FROM (VALUES
    (1, '6th Avenue Heartache'),
    (2, 'Bleeders'),
    (3, 'Three Marlenas'),
    (4, 'Invisible City')
  ) AS t(track_number, title)
)
INSERT INTO album_songs (album_id, song_title, track_number)
SELECT a.id, s.title, t.track_number
FROM albums a
JOIN tracks t ON true
JOIN songs s ON lower(s.title) = lower(t.title)
WHERE a.title = 'Bringing Down The Horse'
  AND NOT EXISTS (
    SELECT 1
    FROM album_songs existing
    WHERE existing.album_id = a.id
      AND existing.song_title = s.title
  );
