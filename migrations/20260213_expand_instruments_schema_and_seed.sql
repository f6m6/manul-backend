-- Expand instruments schema and seed from Gear export.

BEGIN;

ALTER TABLE instruments
  ADD COLUMN IF NOT EXISTS manufacturer text,
  ADD COLUMN IF NOT EXISTS model text,
  ADD COLUMN IF NOT EXISTS family text,
  ADD COLUMN IF NOT EXISTS arrange_category text,
  ADD COLUMN IF NOT EXISTS competence text,
  ADD COLUMN IF NOT EXISTS next_step text;

INSERT INTO instruments (
  name, manufacturer, model, family, arrange_category, competence, next_step
)
VALUES
  ('Kayuna Tablas', 'Kayuna', NULL, 'Tablas', 'Drums', '0 - Very Low', 'Learn one basic rhythm (forgot what they are called)'),
  ('Roland V-Drums', 'Roland', 'V-Drums', 'Electronic drums', 'Drums', '0 - Very Low', 'Learn a basic pattern'),
  ('Hofner Bass guitar', 'Hofner', NULL, 'Bass guitar', 'Bass', '1 - Low', 'Practise some grooves from Bass for Dummies'),
  ('teenage engineering OP-1', 'teenage engineering', 'OP-1', 'Synth, sampler, sequencer', 'Drums, Synth, Sequencer, DAW', '1 - Low', 'Record a whole song on there'),
  ('Radel digi-100', 'Radel', 'digi-100', 'Drum Machine', 'Drums', '1 - Low', 'Program it instead of using a preset beat'),
  ('Yamaha NP-11', 'Yamaha', 'NP-11', 'Keyboard', 'Keyboard', '1 - Low', 'Get fluent in chords of some common keys like G, D, A, E'),
  ('Esteve 1.4ST-E', 'Esteve', '1.4ST-E', 'Classical guitar', 'Guitar', '2 - Medium', NULL),
  ('Fender Telecaster Elite', 'Fender', 'Telecaster Elite', 'Electric guitar', 'Guitar', '3 - High', NULL),
  ('Takamine EF-261', 'Takamine', 'EF-261', 'Electro-acoustic guitar', 'Guitar', '3 - High', NULL)
ON CONFLICT (name) DO UPDATE
SET manufacturer = EXCLUDED.manufacturer,
    model = EXCLUDED.model,
    family = EXCLUDED.family,
    arrange_category = EXCLUDED.arrange_category,
    competence = EXCLUDED.competence,
    next_step = EXCLUDED.next_step;

COMMIT;
