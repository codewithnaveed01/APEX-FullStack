-- Older admin fleet editing stored full data:image/... base64 strings inside cars.data.
-- That made every public fleet/bootstrap response several megabytes and caused
-- slow or failed loads on other computers and mobile networks. Move those bytes
-- into PostgreSQL documents.content and retain only numeric references on cars.

WITH source AS (
  SELECT id,
         data->>'customImage' AS data_url,
         split_part(split_part(data->>'customImage', ';', 1), ':', 2) AS content_type,
         split_part(data->>'customImage', ',', 2) AS payload
  FROM cars
  WHERE COALESCE(main_image_id, 0) = 0
    AND data->>'customImage' LIKE 'data:image/%;base64,%'
), inserted AS (
  INSERT INTO documents(owner_type, owner_id, kind, path, content_type, content, status)
  SELECT 'car', id::text, 'photo', 'legacy-car-' || id || '-exterior', content_type,
         decode(payload, 'base64'), 'Verified'
  FROM source
  WHERE content_type IN ('image/jpeg', 'image/png', 'image/webp', 'image/gif')
    AND payload ~ '^[A-Za-z0-9+/]*={0,2}$'
    AND length(payload) % 4 = 0
  RETURNING id, owner_id
)
UPDATE cars c
SET main_image_id = inserted.id, updated_at = now()
FROM inserted
WHERE c.id::text = inserted.owner_id;

WITH source AS (
  SELECT id,
         COALESCE(
           NULLIF(data->'customImages'->>(image || '-interior'), ''),
           NULLIF(data->'customImages'->>'interior', '')
         ) AS data_url
  FROM cars
  WHERE COALESCE(interior_image_id, 0) = 0
), decoded AS (
  SELECT id, data_url,
         split_part(split_part(data_url, ';', 1), ':', 2) AS content_type,
         split_part(data_url, ',', 2) AS payload
  FROM source
  WHERE data_url LIKE 'data:image/%;base64,%'
), inserted AS (
  INSERT INTO documents(owner_type, owner_id, kind, path, content_type, content, status)
  SELECT 'car', id::text, 'photo', 'legacy-car-' || id || '-interior', content_type,
         decode(payload, 'base64'), 'Verified'
  FROM decoded
  WHERE content_type IN ('image/jpeg', 'image/png', 'image/webp', 'image/gif')
    AND payload ~ '^[A-Za-z0-9+/]*={0,2}$'
    AND length(payload) % 4 = 0
  RETURNING id, owner_id
)
UPDATE cars c
SET interior_image_id = inserted.id, updated_at = now()
FROM inserted
WHERE c.id::text = inserted.owner_id;

WITH source AS (
  SELECT id,
         COALESCE(
           NULLIF(data->'customImages'->>(image || '-detail'), ''),
           NULLIF(data->'customImages'->>(image || '-engine'), ''),
           NULLIF(data->'customImages'->>'detail', ''),
           NULLIF(data->'customImages'->>'engine', '')
         ) AS data_url
  FROM cars
  WHERE COALESCE(detail_image_id, 0) = 0
), decoded AS (
  SELECT id, data_url,
         split_part(split_part(data_url, ';', 1), ':', 2) AS content_type,
         split_part(data_url, ',', 2) AS payload
  FROM source
  WHERE data_url LIKE 'data:image/%;base64,%'
), inserted AS (
  INSERT INTO documents(owner_type, owner_id, kind, path, content_type, content, status)
  SELECT 'car', id::text, 'photo', 'legacy-car-' || id || '-detail', content_type,
         decode(payload, 'base64'), 'Verified'
  FROM decoded
  WHERE content_type IN ('image/jpeg', 'image/png', 'image/webp', 'image/gif')
    AND payload ~ '^[A-Za-z0-9+/]*={0,2}$'
    AND length(payload) % 4 = 0
  RETURNING id, owner_id
)
UPDATE cars c
SET detail_image_id = inserted.id, updated_at = now()
FROM inserted
WHERE c.id::text = inserted.owner_id;

-- Never return or re-save inline image payloads. CarRepo reconstructs compact
-- /api/vehicle-images/{id} URLs from the normalized image-id columns.
UPDATE cars
SET data = data - 'customImage' - 'customImages' - 'images',
    updated_at = now()
WHERE data ? 'customImage' OR data ? 'customImages' OR data ? 'images';
