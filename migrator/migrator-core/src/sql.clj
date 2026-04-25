(ns migrator.sql)

(def CHECK_MIGRATIONS_TABLE_EXISTENCE
  "SELECT 1 FROM information_schema.tables WHERE table_name = '_migrations'")

(def CREATE_MIGRATIONS_TABLE
  "CREATE TABLE _migrations (id VARCHAR(50) NOT NULL PRIMARY KEY, timestamp BIGINT NOT NULL)")

(def SELECT_APPLIED_MIGRATIONS
  "SELECT id FROM _migrations")
