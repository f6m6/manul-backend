-- Ensure albums exist
INSERT INTO albums (title, artist)
SELECT 'Mirror You', 'Farhan Mannan'
WHERE NOT EXISTS (SELECT 1 FROM albums WHERE title = 'Mirror You');

INSERT INTO albums (title, artist)
SELECT 'Bringing Down The Horse', 'The Wallflowers'
WHERE NOT EXISTS (SELECT 1 FROM albums WHERE title = 'Bringing Down The Horse');

-- Associate Mirror You tracks
WITH mirror AS (
  SELECT id FROM albums WHERE title = 'Mirror You' LIMIT 1
)
INSERT INTO album_songs (album_id, song_title, track_number)
SELECT mirror.id, songs.title, tracks.track_number
FROM mirror
JOIN (
  VALUES
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
) AS tracks(track_number, title)
JOIN songs ON lower(songs.title) = lower(tracks.title)
WHERE NOT EXISTS (
  SELECT 1
  FROM album_songs existing
  WHERE existing.album_id = mirror.id
    AND existing.song_title = songs.title
);

-- Associate Wallflowers album songs (track numbers optional/unknown)
WITH wallflowers AS (
  SELECT id FROM albums WHERE title = 'Bringing Down The Horse' LIMIT 1
)
INSERT INTO album_songs (album_id, song_title)
SELECT wallflowers.id, songs.title
FROM wallflowers
JOIN songs ON lower(songs.title) IN (
  lower('6th Avenue Heartache'),
  lower('Bleeders'),
  lower('Three Marlenas'),
  lower('Invisible City')
)
WHERE NOT EXISTS (
  SELECT 1
  FROM album_songs existing
  WHERE existing.album_id = wallflowers.id
    AND existing.song_title = songs.title
);
