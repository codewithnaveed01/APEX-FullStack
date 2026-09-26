-- Add four new default drivers once on already-seeded installations.
-- Keep existing/admin-edited records untouched; fresh installs seed all seven from seed.json.
INSERT INTO drivers (id, name, phone, city, experience, license, active, status, data)
VALUES
  (4, 'Farhan Malik', '+92 300 0000104', 'Rawalpindi', 7, 'RWP-xxx412', TRUE, NULL, '{"id": 4, "name": "Farhan Malik", "phone": "+92 300 0000104", "city": "Rawalpindi", "experience": 7, "license": "RWP-xxx412", "active": true}'::jsonb),
  (5, 'Hamza Khan', '+92 300 0000105', 'Lahore', 5, 'LHR-xxx527', TRUE, NULL, '{"id": 5, "name": "Hamza Khan", "phone": "+92 300 0000105", "city": "Lahore", "experience": 5, "license": "LHR-xxx527", "active": true}'::jsonb),
  (6, 'Asad Mahmood', '+92 300 0000106', 'Faisalabad', 9, 'FSD-xxx639', TRUE, NULL, '{"id": 6, "name": "Asad Mahmood", "phone": "+92 300 0000106", "city": "Faisalabad", "experience": 9, "license": "FSD-xxx639", "active": true}'::jsonb),
  (7, 'Danish Iqbal', '+92 300 0000107', 'Islamabad', 6, 'ISB-xxx746', TRUE, NULL, '{"id": 7, "name": "Danish Iqbal", "phone": "+92 300 0000107", "city": "Islamabad", "experience": 6, "license": "ISB-xxx746", "active": true}'::jsonb)
ON CONFLICT (id) DO NOTHING;
