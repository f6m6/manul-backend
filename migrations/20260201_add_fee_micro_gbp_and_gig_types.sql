-- Add fee in micro-GBP and new gig types
DO $$
BEGIN
  BEGIN
    ALTER TYPE gig_type ADD VALUE 'showcase';
  EXCEPTION
    WHEN duplicate_object THEN NULL;
  END;
  BEGIN
    ALTER TYPE gig_type ADD VALUE 'private_party';
  EXCEPTION
    WHEN duplicate_object THEN NULL;
  END;
  BEGIN
    ALTER TYPE gig_type ADD VALUE 'busking';
  EXCEPTION
    WHEN duplicate_object THEN NULL;
  END;
END $$;

ALTER TABLE performances
  ADD COLUMN IF NOT EXISTS fee_micro_gbp bigint;

UPDATE performances
SET fee_micro_gbp = 0
WHERE fee_micro_gbp IS NULL
  AND (free = true OR openmic = true OR gig_type IN ('busking', 'open_mic'));
