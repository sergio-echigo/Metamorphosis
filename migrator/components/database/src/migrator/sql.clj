(ns migrator.database.sql)

(def CHECK_MIGRATIONS_TABLE_EXISTENCE
  "SELECT 1 FROM information_schema.tables WHERE table_name = '_migrations'")

(def CREATE_INTERNAL_MIGRATIONS_TABLE
  "CREATE TABLE _migrations (id VARCHAR(50) NOT NULL PRIMARY KEY, inserted_at BIGINT NOT NULL)")

(def SELECT_APPLIED_MIGRATIONS
  "SELECT id FROM _migrations")

(def REGISTER_APPLIED_MIGRATION
  "INSERT INTO _migrations VALUES (?, ?)")

(def UNREGISTER_ROLLBACKED_MIGRATION
  "DELETE FROM _migrations WHERE id = ?")
