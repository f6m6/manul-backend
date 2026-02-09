-- Persist editable Home-X thermometer goals.

CREATE TABLE IF NOT EXISTS home_x_goals (
  goal_key text PRIMARY KEY,
  target_value integer NOT NULL CHECK (target_value > 0),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS set_updated_at_on_home_x_goals ON home_x_goals;
CREATE TRIGGER set_updated_at_on_home_x_goals
  BEFORE UPDATE ON home_x_goals
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();

INSERT INTO home_x_goals (goal_key, target_value)
VALUES
  ('gigs_lifetime', 200),
  ('practice_hours_lifetime', 2000),
  ('originals_live_lifetime', 120),
  ('direct_outreach_lifetime', 3000)
ON CONFLICT (goal_key) DO NOTHING;
