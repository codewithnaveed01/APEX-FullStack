-- The original seven built-in chauffeurs predate the driver approval status.
-- They are production roster records, not driver applications, but an empty status
-- made the admin dashboard report all seven as pending. Only the exact built-in
-- identities are backfilled; admin-created pending drivers remain pending.
WITH built_in(id, name, phone, experience, license) AS (
    VALUES
        (1::BIGINT, 'Ali Raza',       '+92 300 0000101',  8, 'LHR-xxx102'),
        (2::BIGINT, 'Imran Shah',     '+92 300 0000102', 11, 'ISB-xxx209'),
        (3::BIGINT, 'Bilal Ahmed',    '+92 300 0000103',  6, 'KHI-xxx311'),
        (4::BIGINT, 'Farhan Malik',   '+92 300 0000104',  7, 'RWP-xxx412'),
        (5::BIGINT, 'Hamza Khan',     '+92 300 0000105',  5, 'LHR-xxx527'),
        (6::BIGINT, 'Asad Mahmood',   '+92 300 0000106',  9, 'FSD-xxx639'),
        (7::BIGINT, 'Danish Iqbal',   '+92 300 0000107',  6, 'ISB-xxx746')
)
UPDATE drivers d
SET status = 'Approved',
    data = jsonb_set(COALESCE(d.data, '{}'::jsonb), '{status}', '"Approved"'::jsonb, TRUE),
    updated_at = now()
FROM built_in b
WHERE d.id = b.id
  AND d.name = b.name
  AND d.phone = b.phone
  AND d.experience = b.experience
  AND d.license = b.license
  AND COALESCE(d.status, '') = ''
  AND COALESCE(d.data->>'status', '') = '';
