-- Add gig_type enum to performances and backfill from free/openmic.

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'gig_type') THEN
    CREATE TYPE gig_type AS ENUM ('practice', 'open_mic', 'busking', 'booked', 'gig');
  END IF;
END$$;

ALTER TABLE performances
  ADD COLUMN IF NOT EXISTS gig_type gig_type;

CREATE OR REPLACE FUNCTION set_gig_type_from_flags()
RETURNS trigger AS $$
BEGIN
  IF NEW.gig_type IS NULL THEN
    NEW.gig_type := CASE
      WHEN NEW.openmic IS TRUE THEN 'open_mic'::gig_type
      WHEN NEW.free IS TRUE THEN 'busking'::gig_type
      ELSE 'booked'::gig_type
    END;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS performances_gig_type_default ON performances;
CREATE TRIGGER performances_gig_type_default
  BEFORE INSERT OR UPDATE ON performances
  FOR EACH ROW
  EXECUTE FUNCTION set_gig_type_from_flags();

UPDATE performances
SET gig_type = CASE
  WHEN openmic IS TRUE THEN 'open_mic'::gig_type
  WHEN free IS TRUE THEN 'busking'::gig_type
  ELSE 'booked'::gig_type
END
WHERE gig_type IS NULL;

ALTER TABLE performances
  ALTER COLUMN gig_type SET NOT NULL;
