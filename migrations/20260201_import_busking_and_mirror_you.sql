-- Import busking gigs and Mirror You songs (generated)
BEGIN;

INSERT INTO venues (venuename, postcode) VALUES ('China Town', NULL) ON CONFLICT (venuename) DO NOTHING;
INSERT INTO venues (venuename, postcode) VALUES ('Eros Statue', NULL) ON CONFLICT (venuename) DO NOTHING;
INSERT INTO venues (venuename, postcode) VALUES ('Glasshouse Street', NULL) ON CONFLICT (venuename) DO NOTHING;
INSERT INTO venues (venuename, postcode) VALUES ('King Charles Statue', NULL) ON CONFLICT (venuename) DO NOTHING;
INSERT INTO venues (venuename, postcode) VALUES ('Leicester Square (North West)', NULL) ON CONFLICT (venuename) DO NOTHING;
INSERT INTO venues (venuename, postcode) VALUES ('Market Square', NULL) ON CONFLICT (venuename) DO NOTHING;
INSERT INTO venues (venuename, postcode) VALUES ('Northumberland Avenue', NULL) ON CONFLICT (venuename) DO NOTHING;
INSERT INTO venues (venuename, postcode) VALUES ('St Martin’s Church', NULL) ON CONFLICT (venuename) DO NOTHING;
INSERT INTO venues (venuename, postcode) VALUES ('Trafalgar Square (North Terrace - Charing Cross Road)', NULL) ON CONFLICT (venuename) DO NOTHING;

INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('(What A) Wonderful World', true, true, 'B', interval '2 minutes 6 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('A Roads', false, true, NULL, interval '3 minutes 46 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Activation', false, true, 'G', interval '3 minutes 45 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Always The One', false, true, NULL, interval '4 minutes 11 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('B', true, true, NULL, NULL, false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('B x2', true, true, NULL, NULL, false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('BEG', true, true, NULL, NULL, false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('BEG x2', true, true, NULL, NULL, false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('BL', true, true, NULL, NULL, false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Back In Time', false, true, NULL, interval '5 minutes 26 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Call Me', false, true, NULL, NULL, false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Chaos', false, true, NULL, interval '3 minutes 49 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Clean', false, true, NULL, NULL, false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Clean Getaway', false, true, NULL, interval '3 minutes 10 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('DITD', true, true, NULL, NULL, false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('DLBIA', true, true, NULL, NULL, false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Dancing In The Dark', true, true, 'B', interval '4 minutes 1 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Don’t Look Back In Anger', true, true, '~C', interval '4 minutes 50 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('FA', true, true, NULL, NULL, false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Far Away From Home', false, true, NULL, interval '4 minutes 2 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Featherlight', false, true, NULL, interval '3 minutes 39 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Fly Away', true, true, 'A', interval '3 minutes 41 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Get Lost', false, true, NULL, interval '3 minutes 3 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('God Bless', false, true, NULL, interval '3 minutes 47 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Hide', false, true, NULL, NULL, false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('I Let Him In', false, true, NULL, interval '2 minutes 26 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('ISHFWILF', true, true, NULL, NULL, false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Keep Burning', false, true, NULL, NULL, false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Lies', false, true, NULL, interval '2 minutes 40 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Make A Wish', false, true, NULL, interval '3 minutes 49 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Map Of My Whole World', false, true, NULL, interval '3 minutes 39 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Mask', false, true, NULL, interval '3 minutes 20 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Monday Morning', false, true, NULL, interval '3 minutes 51 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Multipacks', false, true, NULL, interval '1 minutes 38 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Now We’re Right', false, true, NULL, interval '2 minutes 48 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Prisoner Of Depths', false, true, NULL, interval '2 minutes 26 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Quiet Eye', false, true, 'G', interval '4 minutes 11 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('R', true, true, NULL, NULL, false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Rain', false, true, NULL, interval '4 minutes 0 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Remember Yesterday', false, true, NULL, interval '4 minutes 5 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Ride Or Die', false, true, NULL, NULL, false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('River', true, true, 'Ab', interval '4 minutes 0 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('SOTDOTB', true, true, NULL, NULL, false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Speak Your Language', false, true, 'D', interval '4 minutes 18 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Strong Like You', false, true, NULL, NULL, false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Sweet Nothing', false, true, NULL, NULL, false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('T', true, true, NULL, NULL, false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('True', false, true, NULL, interval '3 minutes 28 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Twisted', false, true, 'D', interval '3 minutes 29 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Valentine', false, true, 'C', interval '3 minutes 25 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('WAWW', true, true, NULL, NULL, false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('WDIAROM', true, true, NULL, NULL, false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('WM', true, true, NULL, NULL, false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('When The Wild Wind Blows', false, true, NULL, interval '2 minutes 49 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('White Mirror', false, true, 'B', interval '3 minutes 30 seconds', false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('YS', true, true, NULL, NULL, false) ON CONFLICT (title) DO NOTHING;
INSERT INTO songs (title, cover, active, key, length, instrumental) VALUES ('Your Song', true, true, 'Eb', interval '4 minutes 2 seconds', false) ON CONFLICT (title) DO NOTHING;

WITH perf AS (
  INSERT INTO performances (performancedate, venue, free, openmic)
  VALUES (DATE '2021-06-14', 'Eros Statue', true, false)
  RETURNING id
)
INSERT INTO song_performances (song_id, performance_id, setlistposition) VALUES
  ('Fly Away', (SELECT id FROM perf), 1),
  ('River', (SELECT id FROM perf), 2),
  ('Your Song', (SELECT id FROM perf), 3),
  ('(What A) Wonderful World', (SELECT id FROM perf), 4),
  ('Dancing In The Dark', (SELECT id FROM perf), 5),
  ('White Mirror', (SELECT id FROM perf), 6);

WITH perf AS (
  INSERT INTO performances (performancedate, venue, free, openmic)
  VALUES (DATE '2021-06-14', 'Leicester Square (North West)', true, false)
  RETURNING id
)
INSERT INTO song_performances (song_id, performance_id, setlistposition) VALUES
  ('Fly Away', (SELECT id FROM perf), 1),
  ('River', (SELECT id FROM perf), 2),
  ('Your Song', (SELECT id FROM perf), 3),
  ('Dancing In The Dark', (SELECT id FROM perf), 4),
  ('Don’t Look Back In Anger', (SELECT id FROM perf), 5);

WITH perf AS (
  INSERT INTO performances (performancedate, venue, free, openmic)
  VALUES (DATE '2021-06-23', 'Leicester Square (North West)', true, false)
  RETURNING id
)
INSERT INTO song_performances (song_id, performance_id, setlistposition) VALUES
  ('River', (SELECT id FROM perf), 1),
  ('Fly Away', (SELECT id FROM perf), 2),
  ('Dancing In The Dark', (SELECT id FROM perf), 3),
  ('White Mirror', (SELECT id FROM perf), 4),
  ('Your Song', (SELECT id FROM perf), 5);

WITH perf AS (
  INSERT INTO performances (performancedate, venue, free, openmic)
  VALUES (DATE '2021-07-20', 'Leicester Square (North West)', true, false)
  RETURNING id
)
INSERT INTO song_performances (song_id, performance_id, setlistposition) VALUES
  ('BEG', (SELECT id FROM perf), 1),
  ('ISHFWILF', (SELECT id FROM perf), 2),
  ('SOTDOTB', (SELECT id FROM perf), 3),
  ('DITD', (SELECT id FROM perf), 4),
  ('FA', (SELECT id FROM perf), 5),
  ('YS', (SELECT id FROM perf), 6),
  ('R', (SELECT id FROM perf), 7),
  ('WAWW', (SELECT id FROM perf), 8);

WITH perf AS (
  INSERT INTO performances (performancedate, venue, free, openmic)
  VALUES (DATE '2021-07-21', 'China Town', true, false)
  RETURNING id
)
INSERT INTO song_performances (song_id, performance_id, setlistposition) VALUES
  ('BEG', (SELECT id FROM perf), 1),
  ('ISHFWILF', (SELECT id FROM perf), 2),
  ('DITD', (SELECT id FROM perf), 3),
  ('FA', (SELECT id FROM perf), 4),
  ('YS', (SELECT id FROM perf), 5),
  ('R', (SELECT id FROM perf), 6),
  ('WAWW', (SELECT id FROM perf), 7);

WITH perf AS (
  INSERT INTO performances (performancedate, venue, free, openmic)
  VALUES (DATE '2021-07-29', 'Northumberland Avenue', true, false)
  RETURNING id
)
INSERT INTO song_performances (song_id, performance_id, setlistposition) VALUES
  ('R', (SELECT id FROM perf), 1),
  ('YS', (SELECT id FROM perf), 2),
  ('WAWW', (SELECT id FROM perf), 3),
  ('DITD', (SELECT id FROM perf), 4),
  ('BEG', (SELECT id FROM perf), 5),
  ('FA', (SELECT id FROM perf), 6),
  ('SOTDOTB', (SELECT id FROM perf), 7),
  ('ISHFWILF', (SELECT id FROM perf), 8);

WITH perf AS (
  INSERT INTO performances (performancedate, venue, free, openmic)
  VALUES (DATE '2021-08-26', 'Glasshouse Street', true, false)
  RETURNING id
)
INSERT INTO song_performances (song_id, performance_id, setlistposition) VALUES
  ('ISHFWILF', (SELECT id FROM perf), 1),
  ('BEG', (SELECT id FROM perf), 2),
  ('DITD', (SELECT id FROM perf), 3),
  ('DLBIA', (SELECT id FROM perf), 4),
  ('SOTDOTB', (SELECT id FROM perf), 5),
  ('FA', (SELECT id FROM perf), 6),
  ('R', (SELECT id FROM perf), 7),
  ('YS', (SELECT id FROM perf), 8),
  ('WAWW', (SELECT id FROM perf), 9);

WITH perf AS (
  INSERT INTO performances (performancedate, venue, free, openmic)
  VALUES (DATE '2021-08-28', 'St Martin’s Church', true, false)
  RETURNING id
)
INSERT INTO song_performances (song_id, performance_id, setlistposition) VALUES
  ('WDIAROM', (SELECT id FROM perf), 1),
  ('BEG x2', (SELECT id FROM perf), 2),
  ('ISHFWILF', (SELECT id FROM perf), 3),
  ('BL', (SELECT id FROM perf), 4),
  ('YS', (SELECT id FROM perf), 5),
  ('B', (SELECT id FROM perf), 6),
  ('WAWW', (SELECT id FROM perf), 7),
  ('DITD', (SELECT id FROM perf), 8),
  ('DLBIA', (SELECT id FROM perf), 9),
  ('SOTDOTB', (SELECT id FROM perf), 10),
  ('FA', (SELECT id FROM perf), 11);

WITH perf AS (
  INSERT INTO performances (performancedate, venue, free, openmic)
  VALUES (DATE '2022-01-05', 'Market Square', true, false)
  RETURNING id
)
INSERT INTO song_performances (song_id, performance_id, setlistposition) VALUES
  ('B x2', (SELECT id FROM perf), 1),
  ('YS', (SELECT id FROM perf), 2),
  ('WDIAROM', (SELECT id FROM perf), 3),
  ('R', (SELECT id FROM perf), 4),
  ('WAWW', (SELECT id FROM perf), 5),
  ('BL', (SELECT id FROM perf), 6),
  ('BEG', (SELECT id FROM perf), 7),
  ('DITD', (SELECT id FROM perf), 8),
  ('ISHFWILF', (SELECT id FROM perf), 9),
  ('SOTDOTB', (SELECT id FROM perf), 10),
  ('DLBIA', (SELECT id FROM perf), 11),
  ('FA', (SELECT id FROM perf), 12),
  ('T', (SELECT id FROM perf), 13);

WITH perf AS (
  INSERT INTO performances (performancedate, venue, free, openmic)
  VALUES (DATE '2022-01-16', 'King Charles Statue', true, false)
  RETURNING id
)
INSERT INTO song_performances (song_id, performance_id, setlistposition) VALUES
  ('B', (SELECT id FROM perf), 1),
  ('YS', (SELECT id FROM perf), 2),
  ('WDIAROM', (SELECT id FROM perf), 3),
  ('BL', (SELECT id FROM perf), 4),
  ('BEG', (SELECT id FROM perf), 5),
  ('DITD', (SELECT id FROM perf), 6),
  ('ISHFWILF', (SELECT id FROM perf), 7),
  ('FA', (SELECT id FROM perf), 8),
  ('R', (SELECT id FROM perf), 9),
  ('T', (SELECT id FROM perf), 10),
  ('WM', (SELECT id FROM perf), 11);

WITH perf AS (
  INSERT INTO performances (performancedate, venue, free, openmic)
  VALUES (DATE '2022-01-16', 'Trafalgar Square (North Terrace - Charing Cross Road)', true, false)
  RETURNING id
)
INSERT INTO song_performances (song_id, performance_id, setlistposition) VALUES
  ('B', (SELECT id FROM perf), 1),
  ('YS', (SELECT id FROM perf), 2),
  ('WDIAROM', (SELECT id FROM perf), 3),
  ('BL', (SELECT id FROM perf), 4),
  ('SOTDOTB', (SELECT id FROM perf), 5);

COMMIT;
