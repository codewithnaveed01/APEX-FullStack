-- =============================================================
-- APEX - Driven Beyond Ordinary : PostgreSQL schema (v1)
-- Applied automatically on startup by com.apex.db.Migrate.
-- Idempotent: safe to run on an existing database.
-- =============================================================

CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  username TEXT NOT NULL UNIQUE,
  email TEXT NOT NULL UNIQUE,
  phone TEXT,
  name TEXT NOT NULL,
  cnic TEXT,
  role TEXT NOT NULL DEFAULT 'customer' CHECK (role IN ('customer', 'admin')),
  password_hash TEXT,
  data JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sessions (
  token TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  username TEXT NOT NULL,
  role TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_expires ON sessions(expires_at);

CREATE TABLE IF NOT EXISTS cars (
  id BIGINT PRIMARY KEY,
  name TEXT NOT NULL,
  brand TEXT,
  category TEXT,
  origin TEXT,
  image TEXT,
  year INT,
  rate BIGINT,
  hourly_rate BIGINT,
  deposit BIGINT,
  seats INT,
  engine TEXT,
  power TEXT,
  fuel TEXT,
  km TEXT,
  color TEXT,
  plate TEXT,
  car_condition TEXT,
  status TEXT NOT NULL DEFAULT 'Active',
  market_note TEXT,
  owner_id TEXT,
  data JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_cars_status ON cars(status);

CREATE TABLE IF NOT EXISTS drivers (
  id BIGINT PRIMARY KEY,
  name TEXT NOT NULL,
  phone TEXT,
  city TEXT,
  experience INT,
  license TEXT,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  status TEXT,
  data JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS bookings (
  id TEXT PRIMARY KEY,
  user_id TEXT,
  customer_name TEXT,
  email TEXT,
  phone TEXT,
  destination TEXT,
  pickup_mode TEXT,
  home_address TEXT,
  office_address TEXT,
  identity_type TEXT,
  identity TEXT,
  identity_masked TEXT,
  identity_status TEXT,
  payment TEXT,
  status TEXT NOT NULL DEFAULT 'Confirmed',
  payment_status TEXT,
  totals JSONB NOT NULL DEFAULT '{}'::jsonb,
  rental BIGINT,
  deposit BIGINT,
  home_delivery BIGINT,
  total BIGINT,
  paid BIGINT NOT NULL DEFAULT 0,
  deposit_paid BIGINT NOT NULL DEFAULT 0,
  cancellation_fee BIGINT NOT NULL DEFAULT 0,
  start_dt TEXT,
  end_dt TEXT,
  pickup_at TEXT,
  actual_return TEXT,
  extra_hours INT,
  extra_charges BIGINT,
  final_amount BIGINT,
  tid TEXT,
  receipt_doc BIGINT,
  owner_payout_done BOOLEAN NOT NULL DEFAULT FALSE,
  data JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_bookings_user ON bookings(user_id);
CREATE INDEX IF NOT EXISTS idx_bookings_status ON bookings(status);

CREATE TABLE IF NOT EXISTS booking_items (
  id BIGSERIAL PRIMARY KEY,
  booking_id TEXT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
  pos INT NOT NULL DEFAULT 0,
  car_id BIGINT NOT NULL,
  start_date TEXT,
  end_date TEXT,
  start_dt TEXT,
  end_dt TEXT,
  city TEXT,
  service TEXT,
  driver_rate BIGINT,
  assigned_driver BIGINT,
  price JSONB,
  self_driver JSONB,
  data JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_booking_items ON booking_items(booking_id, pos);
CREATE INDEX IF NOT EXISTS idx_items_car ON booking_items(car_id);
CREATE INDEX IF NOT EXISTS idx_items_window ON booking_items(car_id, start_dt, end_dt);

CREATE TABLE IF NOT EXISTS owner_applications (
  id TEXT PRIMARY KEY,
  user_id TEXT,
  owner TEXT,
  phone TEXT,
  email TEXT,
  brand TEXT,
  model TEXT,
  year INT,
  registration TEXT,
  cnic TEXT,
  license TEXT,
  city TEXT,
  mileage INT,
  car_condition TEXT,
  rate BIGINT,
  preference TEXT,
  available_from TEXT,
  photo_count INT,
  notes TEXT,
  status TEXT NOT NULL DEFAULT 'Submitted',
  verification TEXT,
  data JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_apps_user ON owner_applications(user_id);

CREATE TABLE IF NOT EXISTS notifications (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  title TEXT NOT NULL,
  msg TEXT,
  link TEXT,
  admin_tab TEXT,
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_notifs_user ON notifications(user_id, is_read);

CREATE TABLE IF NOT EXISTS chat_threads (
  user_id TEXT PRIMARY KEY,
  user_name TEXT,
  last_time TIMESTAMPTZ,
  unread_admin INT NOT NULL DEFAULT 0,
  unread_user INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS chat_messages (
  id BIGSERIAL PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES chat_threads(user_id) ON DELETE CASCADE,
  sender TEXT NOT NULL,
  body TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_chat_msgs ON chat_messages(user_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_chat_msgs ON chat_messages(user_id, sender, md5(body), created_at);

CREATE TABLE IF NOT EXISTS banned_cnic (
  cnic TEXT PRIMARY KEY,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS wallet (
  id INT PRIMARY KEY CHECK (id = 1),
  balance BIGINT NOT NULL DEFAULT 0
);
INSERT INTO wallet(id, balance) VALUES (1, 0) ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS owner_wallets (
  owner_id TEXT PRIMARY KEY,
  balance BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS wallet_transactions (
  id BIGSERIAL PRIMARY KEY,
  type TEXT NOT NULL,
  amount BIGINT NOT NULL,
  account TEXT,
  note TEXT,
  status TEXT NOT NULL DEFAULT 'Paid',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_wallet_tx_status ON wallet_transactions(status);

CREATE TABLE IF NOT EXISTS documents (
  id BIGSERIAL PRIMARY KEY,
  owner_type TEXT NOT NULL,
  owner_id TEXT NOT NULL,
  kind TEXT NOT NULL,
  path TEXT NOT NULL,
  content_type TEXT NOT NULL DEFAULT 'image/jpeg',
  content BYTEA NOT NULL,
  status TEXT NOT NULL DEFAULT 'Pending',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_docs_owner ON documents(owner_type, owner_id);

CREATE TABLE IF NOT EXISTS payments (
  id BIGSERIAL PRIMARY KEY,
  booking_id TEXT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
  method TEXT,
  amount BIGINT NOT NULL,
  tid TEXT,
  receipt_doc BIGINT,
  status TEXT NOT NULL DEFAULT 'Pending Verification',
  submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  reviewed_at TIMESTAMPTZ,
  reviewed_by TEXT,
  note TEXT
);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);
CREATE INDEX IF NOT EXISTS idx_payments_booking ON payments(booking_id);

CREATE TABLE IF NOT EXISTS reviews (
  id BIGSERIAL PRIMARY KEY,
  booking_id TEXT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
  car_id BIGINT NOT NULL,
  user_id TEXT NOT NULL,
  rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
  body TEXT,
  status TEXT NOT NULL DEFAULT 'Pending',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (booking_id)
);
CREATE INDEX IF NOT EXISTS idx_reviews_car ON reviews(car_id, status);

CREATE TABLE IF NOT EXISTS app_settings (
  setting_key TEXT PRIMARY KEY,
  value JSONB NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
