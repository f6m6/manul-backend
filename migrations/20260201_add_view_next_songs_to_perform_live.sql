-- Add naming-consistent view for live performances.

CREATE OR REPLACE VIEW view_next_songs_to_perform_live AS
SELECT * FROM view_next_songs_to_play;
