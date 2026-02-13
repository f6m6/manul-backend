-- Add editable weekly solo-practice target for Home-X Farhan Fund.

INSERT INTO home_x_goals (goal_key, target_value)
VALUES ('solo_practice_minutes_weekly', 240)
ON CONFLICT (goal_key) DO NOTHING;
