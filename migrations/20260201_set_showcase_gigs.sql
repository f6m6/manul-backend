-- Mark showcase performances explicitly
UPDATE performances
SET gig_type = 'showcase',
    free = false,
    openmic = false
WHERE venue IN ('The Joint', 'University of South Wales');
