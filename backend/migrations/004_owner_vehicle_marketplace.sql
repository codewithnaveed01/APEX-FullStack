-- Production owner/partner vehicle marketplace.
-- Images remain in PostgreSQL documents.content (BYTEA); this migration stores
-- durable, ordered references and makes application approval idempotent.

ALTER TABLE cars ADD COLUMN IF NOT EXISTS application_id TEXT;
ALTER TABLE cars ADD COLUMN IF NOT EXISTS main_image_id BIGINT;
ALTER TABLE cars ADD COLUMN IF NOT EXISTS interior_image_id BIGINT;
ALTER TABLE cars ADD COLUMN IF NOT EXISTS detail_image_id BIGINT;
CREATE UNIQUE INDEX IF NOT EXISTS uq_cars_application_id
  ON cars(application_id) WHERE application_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_cars_owner ON cars(owner_id);

ALTER TABLE owner_applications ADD COLUMN IF NOT EXISTS live_car_id BIGINT;
ALTER TABLE owner_applications ADD COLUMN IF NOT EXISTS revision INT NOT NULL DEFAULT 1;
ALTER TABLE owner_applications ADD COLUMN IF NOT EXISTS edit_status TEXT NOT NULL DEFAULT 'Pending approval';
ALTER TABLE owner_applications ADD COLUMN IF NOT EXISTS approved_by TEXT;
ALTER TABLE owner_applications ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ;
ALTER TABLE owner_applications ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_apps_status ON owner_applications(status);

CREATE TABLE IF NOT EXISTS vehicle_application_images (
  application_id TEXT NOT NULL REFERENCES owner_applications(id) ON DELETE CASCADE,
  position SMALLINT NOT NULL CHECK (position BETWEEN 1 AND 3),
  document_id BIGINT NOT NULL REFERENCES documents(id),
  owner_id TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (application_id, position),
  UNIQUE (document_id)
);
CREATE INDEX IF NOT EXISTS idx_vehicle_images_document ON vehicle_application_images(document_id);

CREATE TABLE IF NOT EXISTS owner_application_history (
  id BIGSERIAL PRIMARY KEY,
  application_id TEXT NOT NULL REFERENCES owner_applications(id) ON DELETE CASCADE,
  revision INT NOT NULL,
  action TEXT NOT NULL,
  actor_id TEXT,
  actor_role TEXT NOT NULL,
  status TEXT NOT NULL,
  snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_app_history_application
  ON owner_application_history(application_id, id DESC);

-- Sensitive payout values are AES-GCM encrypted by the Java service before
-- they enter these *_enc columns. Only non-sensitive routing metadata and a
-- last-four display hint are stored in plaintext.
CREATE TABLE IF NOT EXISTS owner_payout_accounts (
  owner_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  method TEXT NOT NULL CHECK (method IN ('Bank account / IBAN', 'JazzCash', 'Easypaisa')),
  account_title_enc TEXT NOT NULL,
  account_number_enc TEXT,
  iban_enc TEXT,
  phone_enc TEXT,
  display_last4 TEXT NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS owner_payout_ledger (
  id BIGSERIAL PRIMARY KEY,
  owner_id TEXT NOT NULL,
  booking_id TEXT NOT NULL REFERENCES bookings(id),
  car_id BIGINT NOT NULL,
  car_name TEXT NOT NULL DEFAULT '',
  application_id TEXT,
  gross_rental BIGINT NOT NULL,
  company_share BIGINT NOT NULL,
  owner_share BIGINT NOT NULL,
  status TEXT NOT NULL DEFAULT 'Pending',
  note TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  settled_at TIMESTAMPTZ,
  UNIQUE (booking_id, car_id)
);
CREATE INDEX IF NOT EXISTS idx_owner_payout_ledger_owner
  ON owner_payout_ledger(owner_id, id DESC);
CREATE INDEX IF NOT EXISTS idx_owner_payout_ledger_status
  ON owner_payout_ledger(status);

CREATE TABLE IF NOT EXISTS owner_car_deletions (
  id BIGSERIAL PRIMARY KEY,
  car_id BIGINT NOT NULL,
  application_id TEXT,
  owner_id TEXT NOT NULL,
  snapshot JSONB NOT NULL,
  deleted_by TEXT NOT NULL,
  deleted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_owner_car_deletions_owner
  ON owner_car_deletions(owner_id, deleted_at DESC);

-- Backfill durable links when older JSON rows already carry them.
UPDATE cars
SET application_id = NULLIF(data->>'applicationId', '')
WHERE application_id IS NULL AND NULLIF(data->>'applicationId', '') IS NOT NULL;

UPDATE owner_applications a
SET live_car_id = c.id,
    edit_status = CASE WHEN a.status = 'Approved for onboarding' THEN 'Published' ELSE a.edit_status END
FROM cars c
WHERE c.application_id = a.id AND a.live_car_id IS NULL;

-- Migrate valid legacy photoIds arrays without making a deployment fail on
-- malformed historical browser data.
INSERT INTO vehicle_application_images(application_id, position, document_id, owner_id)
SELECT a.id, x.ord::smallint, (x.value #>> '{}')::bigint, a.user_id
FROM owner_applications a
CROSS JOIN LATERAL jsonb_array_elements(CASE
  WHEN jsonb_typeof(a.data->'photoIds') = 'array' THEN a.data->'photoIds'
  ELSE '[]'::jsonb END) WITH ORDINALITY AS x(value, ord)
JOIN documents d ON d.id = CASE WHEN jsonb_typeof(x.value) = 'number'
  THEN (x.value #>> '{}')::bigint ELSE NULL END
WHERE jsonb_typeof(a.data->'photoIds') = 'array'
  AND jsonb_typeof(x.value) = 'number'
  AND x.ord BETWEEN 1 AND 3
  AND d.owner_id = a.user_id
ON CONFLICT DO NOTHING;
