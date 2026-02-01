-- Update track numbers + lengths for existing Wallflowers songs
WITH wallflowers AS (
  SELECT id FROM albums WHERE title = 'Bringing Down The Horse' LIMIT 1
), tracks AS (
  SELECT * FROM (VALUES
    (1, 'One Headlight', '5:13'),
    (2, '6th Avenue Heartache', '5:37'),
    (3, 'Bleeders', '3:41'),
    (4, 'Three Marlenas', '4:59'),
    (5, 'The Difference', '3:50'),
    (6, 'Invisible City', '4:48'),
    (7, 'Laughing Out Loud', '3:39'),
    (8, 'Josephine', '5:09'),
    (9, 'God Don''t Make Lonely Girls', '4:49'),
    (10, 'Angel on My Bike', '4:22'),
    (11, 'I Wish I Felt Nothing', '5:04')
  ) AS t(track_number, title, length_mmss)
)
UPDATE album_songs asg
SET track_number = NULL
FROM wallflowers, tracks, songs
WHERE asg.album_id = wallflowers.id
  AND asg.song_title = songs.title
  AND lower(songs.title) = lower(tracks.title);

WITH wallflowers AS (
  SELECT id FROM albums WHERE title = 'Bringing Down The Horse' LIMIT 1
), tracks AS (
  SELECT * FROM (VALUES
    (1, 'One Headlight', '5:13'),
    (2, '6th Avenue Heartache', '5:37'),
    (3, 'Bleeders', '3:41'),
    (4, 'Three Marlenas', '4:59'),
    (5, 'The Difference', '3:50'),
    (6, 'Invisible City', '4:48'),
    (7, 'Laughing Out Loud', '3:39'),
    (8, 'Josephine', '5:09'),
    (9, 'God Don''t Make Lonely Girls', '4:49'),
    (10, 'Angel on My Bike', '4:22'),
    (11, 'I Wish I Felt Nothing', '5:04')
  ) AS t(track_number, title, length_mmss)
)
UPDATE album_songs asg
SET track_number = tracks.track_number
FROM wallflowers, tracks, songs
WHERE asg.album_id = wallflowers.id
  AND asg.song_title = songs.title
  AND lower(songs.title) = lower(tracks.title);

WITH wallflowers AS (
  SELECT id FROM albums WHERE title = 'Bringing Down The Horse' LIMIT 1
), tracks AS (
  SELECT * FROM (VALUES
    (1, 'One Headlight', '5:13'),
    (2, '6th Avenue Heartache', '5:37'),
    (3, 'Bleeders', '3:41'),
    (4, 'Three Marlenas', '4:59'),
    (5, 'The Difference', '3:50'),
    (6, 'Invisible City', '4:48'),
    (7, 'Laughing Out Loud', '3:39'),
    (8, 'Josephine', '5:09'),
    (9, 'God Don''t Make Lonely Girls', '4:49'),
    (10, 'Angel on My Bike', '4:22'),
    (11, 'I Wish I Felt Nothing', '5:04')
  ) AS t(track_number, title, length_mmss)
)
UPDATE album_songs asg
SET track_number = tracks.track_number
FROM wallflowers, tracks, songs
WHERE asg.album_id = wallflowers.id
  AND asg.song_title = songs.title
  AND lower(songs.title) = lower(tracks.title);

WITH tracks AS (
  SELECT * FROM (VALUES
    (1, 'One Headlight', '5:13'),
    (2, '6th Avenue Heartache', '5:37'),
    (3, 'Bleeders', '3:41'),
    (4, 'Three Marlenas', '4:59'),
    (5, 'The Difference', '3:50'),
    (6, 'Invisible City', '4:48'),
    (7, 'Laughing Out Loud', '3:39'),
    (8, 'Josephine', '5:09'),
    (9, 'God Don''t Make Lonely Girls', '4:49'),
    (10, 'Angel on My Bike', '4:22'),
    (11, 'I Wish I Felt Nothing', '5:04')
  ) AS t(track_number, title, length_mmss)
)
UPDATE songs
SET length = tracks.length_mmss::interval
FROM tracks
WHERE lower(songs.title) = lower(tracks.title);
