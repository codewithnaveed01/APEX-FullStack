-- Align only untouched default drivers with the four operating branch cities.
-- Keep the seven-driver roster and never rewrite an admin-edited record.
UPDATE drivers
SET city = 'Dera Ghazi Khan',
    data = jsonb_set(data, '{city}', '"Dera Ghazi Khan"'::jsonb),
    updated_at = now()
WHERE id = 4 AND city = 'Rawalpindi' AND data->>'city' = 'Rawalpindi'
  AND name = 'Farhan Malik' AND phone = '+92 300 0000104'
  AND experience = 7 AND license = 'RWP-xxx412' AND active = TRUE
  AND status IS NULL AND updated_at = created_at;

UPDATE drivers
SET city = 'Karachi',
    data = jsonb_set(data, '{city}', '"Karachi"'::jsonb),
    updated_at = now()
WHERE id = 6 AND city = 'Faisalabad' AND data->>'city' = 'Faisalabad'
  AND name = 'Asad Mahmood' AND phone = '+92 300 0000106'
  AND experience = 9 AND license = 'FSD-xxx639' AND active = TRUE
  AND status IS NULL AND updated_at = created_at;
