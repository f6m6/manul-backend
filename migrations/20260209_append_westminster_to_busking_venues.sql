-- Append Westminster, London to busking venue names for geocoding clarity.
-- FK-safe order: create new venue rows, repoint performances, then remove old rows.
-- Idempotent: safe to run multiple times.

WITH mapping(old_name, new_name) AS (
  VALUES
    ('China Town', 'China Town, Westminster, London'),
    ('Eros Statue', 'Eros Statue, Westminster, London'),
    ('Glasshouse Street', 'Glasshouse Street, Westminster, London'),
    ('King Charles Statue', 'King Charles Statue, Westminster, London'),
    ('Leicester Square (North West)', 'Leicester Square (North West), Westminster, London'),
    ('Market Square', 'Market Square, Westminster, London'),
    ('Northumberland Avenue', 'Northumberland Avenue, Westminster, London'),
    ('St Martin’s Church', 'St Martin’s Church, Westminster, London'),
    ('Trafalgar Square (North Terrace - Charing Cross Road)', 'Trafalgar Square (North Terrace - Charing Cross Road), Westminster, London')
)
INSERT INTO venues (venuename, postcode)
SELECT m.new_name, v.postcode
FROM mapping m
JOIN venues v ON v.venuename = m.old_name
LEFT JOIN venues existing ON existing.venuename = m.new_name
WHERE existing.venuename IS NULL;

WITH mapping(old_name, new_name) AS (
  VALUES
    ('China Town', 'China Town, Westminster, London'),
    ('Eros Statue', 'Eros Statue, Westminster, London'),
    ('Glasshouse Street', 'Glasshouse Street, Westminster, London'),
    ('King Charles Statue', 'King Charles Statue, Westminster, London'),
    ('Leicester Square (North West)', 'Leicester Square (North West), Westminster, London'),
    ('Market Square', 'Market Square, Westminster, London'),
    ('Northumberland Avenue', 'Northumberland Avenue, Westminster, London'),
    ('St Martin’s Church', 'St Martin’s Church, Westminster, London'),
    ('Trafalgar Square (North Terrace - Charing Cross Road)', 'Trafalgar Square (North Terrace - Charing Cross Road), Westminster, London')
)
UPDATE performances p
SET venue = m.new_name
FROM mapping m
WHERE p.venue = m.old_name;

WITH mapping(old_name, new_name) AS (
  VALUES
    ('China Town', 'China Town, Westminster, London'),
    ('Eros Statue', 'Eros Statue, Westminster, London'),
    ('Glasshouse Street', 'Glasshouse Street, Westminster, London'),
    ('King Charles Statue', 'King Charles Statue, Westminster, London'),
    ('Leicester Square (North West)', 'Leicester Square (North West), Westminster, London'),
    ('Market Square', 'Market Square, Westminster, London'),
    ('Northumberland Avenue', 'Northumberland Avenue, Westminster, London'),
    ('St Martin’s Church', 'St Martin’s Church, Westminster, London'),
    ('Trafalgar Square (North Terrace - Charing Cross Road)', 'Trafalgar Square (North Terrace - Charing Cross Road), Westminster, London')
)
DELETE FROM venues v
USING mapping m
WHERE v.venuename = m.old_name
  AND NOT EXISTS (SELECT 1 FROM performances p WHERE p.venue = v.venuename);
