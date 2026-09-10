-- Import "through" songs from the Notion Fluency Practice export (generated)
-- Matches existing songs by title, ignoring case and curly vs straight apostrophes.
-- CSV Key -> recorded_key (and legacy key), My Key -> my_live_key; blank CSV values never overwrite.
-- cover = artist is not Farhan Mannan. Albums are created and linked where given.
BEGIN;

CREATE TEMP TABLE import_songs (
  title text NOT NULL,
  artist text,
  recorded_key text,
  my_live_key text,
  album text,
  track_number integer,
  db_title text
) ON COMMIT DROP;

INSERT INTO import_songs (title, artist, recorded_key, my_live_key, album, track_number) VALUES
  ('White Mirror', 'Farhan Mannan', 'B', 'B', 'Mirror You', 1),
  ('Three Marlenas', 'The Wallflowers', 'Eb', 'Eb', 'Bringing Down The Horse', 4),
  ('All My Loving', 'The Beatles', 'E', 'Db', NULL, NULL),
  ('Wetsuit', 'The Vaccines', 'F', 'F', NULL, NULL),
  ('Yellow', 'Coldplay', 'Db', 'Gb', NULL, NULL),
  ('The Difference', 'The Wallflowers', 'Eb', 'B', 'Bringing Down The Horse', 5),
  ('Angel On My Bike', 'The Wallflowers', 'D', 'Db', 'Bringing Down The Horse', 10),
  ('One Headlight', 'The Wallflowers', 'D', 'B', 'Bringing Down The Horse', 1),
  ('Invisible City', 'The Wallflowers', 'E', 'E', 'Bringing Down The Horse', 6),
  ('Bleeders', 'The Wallflowers', 'E', 'Eb', 'Bringing Down The Horse', 3),
  ('Laughing Out Loud', 'The Wallflowers', 'A', 'Ab', 'Bringing Down The Horse', 7),
  ('6th Avenue Heartache', 'The Wallflowers', 'F', 'Eb', 'Bringing Down The Horse', 2),
  ('Josephine', 'The Wallflowers', 'A', 'Ab', 'Bringing Down The Horse', 8),
  ('God Don''t Make Lonely Girls', 'The Wallflowers', 'A', 'Gb', 'Bringing Down The Horse', 9),
  ('I Wish I Felt Nothing', 'The Wallflowers', 'A', 'Ab', 'Bringing Down The Horse', 11),
  ('Strong Like You', 'Farhan Mannan', 'Eb', 'Eb', 'Mirror You', 3),
  ('Quiet Eye', 'Farhan Mannan', 'G', 'G', 'Mirror You', 5),
  ('Ride Or Die', 'Farhan Mannan', 'A', 'Eb', 'Mirror You', 6),
  ('Clean', 'Farhan Mannan', 'Gb', 'Gb', 'Mirror You', 8),
  ('Keep Burning', 'Farhan Mannan', 'F', 'F', 'Mirror You', 10),
  ('Everywhere', 'Greg Trooper with the Flatirons', 'G', 'Gb', NULL, NULL),
  ('Bo Diddley', 'Bo Diddley', 'G', 'G', NULL, NULL),
  ('Don''t Panic', 'Coldplay', 'C', 'C', NULL, NULL),
  ('A Bar Song (Tipsy)', 'Shaboozey', 'A', 'Ab', NULL, NULL),
  ('Atlantic City', 'Bruce Springsteen', 'A', 'Gb', NULL, NULL),
  ('Twist and Shout', 'The Beatles', 'D', 'Ab', NULL, NULL),
  ('Only Children', 'Jason Isbell', 'G', 'Gb', NULL, NULL),
  ('Cobra', 'Geese', 'G', 'G', NULL, NULL),
  ('Little Wing', 'Jimi Hendrix', 'Gb', 'Gb', NULL, NULL),
  ('(Sittin'' On) The Dock Of The Bay', 'Otis Redding', 'D', 'Eb', NULL, NULL),
  ('Nowhere Man', 'The Beatles', 'E', 'E', NULL, NULL),
  ('Happy Birthday', NULL, NULL, 'A', NULL, NULL),
  ('Good Riddance (Time of Your Life)', 'Green Day', 'G', 'G', NULL, NULL),
  ('Knockin'' On Heaven''s Door', 'Bob Dylan', 'G', 'G', NULL, NULL),
  ('Eight Days A Week', 'The Beatles', 'D', 'B', NULL, NULL),
  ('Wrecking Ball', 'Bruce Springsteen', 'F', 'Eb', 'Wrecking Ball', NULL),
  ('When You Were Young', 'The Killers', 'B', 'Gb', NULL, NULL),
  ('Smile Like You Mean It', 'The Killers', 'Gb', 'Gb', NULL, NULL),
  ('Relatively Easy', 'Jason Isbell', 'Gb', 'Gb', NULL, NULL),
  ('Boulevard Of Broken Dreams', 'Green Day', 'Ab', 'Gb', NULL, NULL),
  ('Private Converation', 'Lyle Lovett', 'Ab', 'Ab', NULL, NULL),
  ('The Road To Ensenada', 'Lyle Lovett', 'D', 'D', NULL, NULL),
  ('Fly Away', 'Lenny Kravitz', 'C', 'Gb', NULL, NULL),
  ('Nearly Beloved', 'The Wallflowers', 'G', 'D', NULL, NULL),
  ('Live Forever', 'Oasis', 'G', 'E', NULL, NULL),
  ('Girlfriend In A Coma', 'The Smiths', 'G', 'G', NULL, NULL),
  ('Splendid Isolation', 'Warren Zevon', 'G', 'Gb', NULL, NULL),
  ('Strawberry Swing', 'Coldplay', 'Ab', 'Eb', NULL, NULL),
  ('Violet Hill', 'Coldplay', 'E', 'E', NULL, NULL),
  ('Andrew In Drag', 'The Magnetic Fields', 'C', 'C', NULL, NULL),
  ('Bangladesh', 'Ian McConnell', 'Eb', 'Bb', NULL, NULL),
  ('Empire In My Mind', 'The Wallflowers', 'G', 'G', NULL, NULL),
  ('When You''re On Top', 'The Wallflowers', 'D', 'D', NULL, NULL),
  ('Here In Pleasantville', 'The Wallflowers', 'E', 'E', NULL, NULL),
  ('Dreaming of You', 'The Coral', 'C', 'G', NULL, NULL),
  ('Enjoy The Silence', 'Depeche Mode', 'Eb', 'Eb', NULL, NULL),
  ('I Am A Building', 'The Wallflowers', 'G', 'G', NULL, NULL),
  ('Breakdown', 'Tom Petty', 'C', 'Gb', NULL, NULL),
  ('Look at Miss Ohio', 'Gillian Welch', 'C', 'Gb', NULL, NULL),
  ('Last Great American Whale', 'Lou Reed', 'D', 'D', NULL, NULL),
  ('Fade Into You', 'Mazzy Star', 'A', 'Ab', NULL, NULL),
  ('Born In The USA', 'Bruce Springsteen', 'B', 'B', NULL, NULL),
  ('Murder 101', 'The Wallflowers', 'D', 'D', '(Breach)', NULL),
  ('Sleepwalker', 'The Wallflowers', 'G', 'Gb', '(Breach)', NULL),
  ('Christopher''s River', 'Biffy Clyro', 'G', 'Gb', NULL, NULL),
  ('Naive', 'The Kooks', 'B', 'Gb', NULL, NULL),
  ('Ain''t No Sunshine', 'Bill Withers', 'C', 'B', NULL, NULL),
  ('''74-''75', 'The Connells', 'B', 'Bb', NULL, NULL),
  ('Sugar, We''re Goin Down', 'Fall Out Boy', 'D', 'Ab', NULL, NULL),
  ('Wake Me Up When September Ends', 'Green Day', 'G', 'Gb', NULL, NULL),
  ('The Scientist', 'Coldplay', 'F', 'Eb', NULL, NULL),
  ('Hitch Hikin''', 'Bruce Springsteen', 'G', 'Gb', NULL, NULL),
  ('Secret Garden', 'Bruce Springsteen', 'C', 'C', NULL, NULL),
  ('St. George Wharf Tower', 'Jamie T', 'Ab', 'Gb', NULL, NULL),
  ('Slave To The Wage', 'Placebo', 'Bb', 'Ab', NULL, NULL),
  ('Hound Dog', 'Elvis Presley', 'C', 'B', NULL, NULL),
  ('(What A) Wonderful World', 'Sam Cooke', 'B', 'Gb', NULL, NULL),
  ('Brown Eyed Girl', 'Van Morrison', 'G', 'Gb', NULL, NULL),
  ('Me and Julio Down by the Schoolyard', 'Paul Simon', 'A', 'Ab', NULL, NULL),
  ('Whiskey in the Jar', 'Thin Lizzy', 'G', 'Eb', NULL, NULL),
  ('Werewolves of London', 'Warren Zevon', 'G', 'Gb', NULL, NULL),
  ('Octopus''s Garden', 'The Beatles', 'E', 'Eb', NULL, NULL),
  ('Doctor My Eyes', 'Jackson Browne', 'F', 'Eb', NULL, NULL),
  ('Your Dog', 'Soccer Mommy', 'B', 'D', NULL, NULL),
  ('Lawyers, Guns and Money', 'Warren Zevon', 'E', 'E', NULL, NULL),
  ('Enola Gay', 'Orchestral Manoeuvres In The Dark', 'F', 'F', NULL, NULL),
  ('Back To California', 'The Wallflowers', 'A', 'A', 'Rebel, Sweetheart', NULL),
  ('The Swimming Song', 'Loudon Wainwright III', 'A', 'G', NULL, NULL),
  ('Hungry Heart', 'Bruce Springsteen', 'Db', 'B', NULL, NULL),
  ('Half The World Away', 'Oasis', 'C', 'B', NULL, NULL),
  ('And It''s Gone', 'Caamp', 'Gb', 'Gb', NULL, NULL),
  ('Wildflowers', 'Soccer Mommy', 'A', 'Eb', NULL, NULL),
  ('The Night Before', 'The Beatles', 'D', 'Ab', NULL, NULL),
  ('Doolin-Dalton', 'Eagles', 'A', 'Gb', NULL, NULL),
  ('Lyin'' Eyes', 'Eagles', 'G', 'Gb', NULL, NULL),
  ('The End Of The Day', 'Lucy Kaplansky', 'Bb', 'Ab', NULL, NULL),
  ('He Thinks He''ll Keep Her', 'Mary Chapin Carpenter', 'A', 'Gb', NULL, NULL),
  ('We''re Not Right', 'David Gray', 'E', 'E', NULL, NULL),
  ('Golden Touch', 'Razorlight', 'E', 'B', NULL, NULL),
  ('Lost!', 'Coldplay', 'G', 'Gb', NULL, NULL),
  ('Life In Technicolor II', 'Coldplay', 'A', 'Gb', NULL, NULL),
  ('The Only Exception', 'Paramore', 'B', 'B', NULL, NULL),
  ('Nothing Man', 'Bruce Springsteen', 'Eb', 'Eb', NULL, NULL),
  ('Notion', 'Kings Of Leon', 'E', 'B', NULL, NULL),
  ('Before I Fall To Pieces', 'Razorlight', 'D', 'B', NULL, NULL),
  ('Mexican Wine', 'Fountains Of Wayne', 'Gb', 'Gb', 'Welcome Interstate Managers', NULL),
  ('Waiting for My Man', 'The Velvet Underground', 'D', 'D', NULL, NULL),
  ('Kids', 'MGMT', 'A', 'Ab', NULL, NULL),
  ('The Young People (Edit)', 'Lankum', 'C', 'C', NULL, NULL),
  ('Let It Be', 'The Beatles', 'C', 'Ab', NULL, NULL),
  ('Cry Me A River', 'Justin Timberlake', 'B', 'Eb', NULL, NULL);

UPDATE import_songs i
SET db_title = s.title
FROM songs s
WHERE lower(replace(replace(trim(s.title), '’', ''''), '‘', '''')) = lower(i.title);

UPDATE songs s
SET artist = COALESCE(i.artist, s.artist),
    recorded_key = COALESCE(i.recorded_key, s.recorded_key),
    key = COALESCE(i.recorded_key, s.key),
    my_live_key = COALESCE(i.my_live_key, s.my_live_key),
    cover = COALESCE(i.artist, s.artist) IS DISTINCT FROM 'Farhan Mannan'
FROM import_songs i
WHERE s.title = i.db_title
  AND (s.artist IS DISTINCT FROM COALESCE(i.artist, s.artist)
    OR s.recorded_key IS DISTINCT FROM COALESCE(i.recorded_key, s.recorded_key)
    OR s.key IS DISTINCT FROM COALESCE(i.recorded_key, s.key)
    OR s.my_live_key IS DISTINCT FROM COALESCE(i.my_live_key, s.my_live_key)
    OR s.cover IS DISTINCT FROM (COALESCE(i.artist, s.artist) IS DISTINCT FROM 'Farhan Mannan'));

INSERT INTO songs (title, artist, key, recorded_key, my_live_key, cover)
SELECT title, artist, recorded_key, recorded_key, my_live_key, artist IS DISTINCT FROM 'Farhan Mannan'
FROM import_songs
WHERE db_title IS NULL;

UPDATE import_songs SET db_title = title WHERE db_title IS NULL;

INSERT INTO albums (title, artist)
SELECT DISTINCT album, artist FROM import_songs WHERE album IS NOT NULL
ON CONFLICT (title) DO NOTHING;

INSERT INTO album_songs (album_id, song_title, track_number)
SELECT a.id, i.db_title, i.track_number
FROM import_songs i
JOIN albums a ON a.title = i.album
ON CONFLICT (album_id, song_title) DO UPDATE
SET track_number = COALESCE(EXCLUDED.track_number, album_songs.track_number)
WHERE album_songs.track_number IS DISTINCT FROM COALESCE(EXCLUDED.track_number, album_songs.track_number);

COMMIT;
