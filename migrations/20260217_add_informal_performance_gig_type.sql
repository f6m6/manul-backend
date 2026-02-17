-- Add informal performance as a first-class gig type.

DO $$
BEGIN
  BEGIN
    ALTER TYPE gig_type ADD VALUE 'informal_performance';
  EXCEPTION
    WHEN duplicate_object THEN NULL;
  END;
END $$;

