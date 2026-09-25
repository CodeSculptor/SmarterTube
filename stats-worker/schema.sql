-- SmarterTube opt-in anonymous stats. Counters only — no ids, no IPs.

-- One row per (UTC day, app version, Android SDK). Each opted-in install adds at most 1 to
-- `pings` per day, so SUM(pings) for a day = daily active installs; SUM(new_week) over a
-- week = weekly actives; SUM(new_month) over a month = monthly actives; SUM(first) = new installs.
CREATE TABLE IF NOT EXISTS pings (
  day       TEXT    NOT NULL,
  version   TEXT    NOT NULL,
  sdk       INTEGER NOT NULL,
  pings     INTEGER NOT NULL DEFAULT 0,
  new_week  INTEGER NOT NULL DEFAULT 0,
  new_month INTEGER NOT NULL DEFAULT 0,
  first     INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (day, version, sdk)
);

-- Crash groups, keyed by a hash of the (message-free) stack trace and the app version.
CREATE TABLE IF NOT EXISTS crashes (
  sig       TEXT    NOT NULL,
  version   TEXT    NOT NULL,
  count     INTEGER NOT NULL DEFAULT 0,
  first_day TEXT    NOT NULL,
  last_day  TEXT    NOT NULL,
  sdk       INTEGER,
  device    TEXT,
  trace     TEXT    NOT NULL,
  PRIMARY KEY (sig, version)
);
