(ns migrator.core)

(defn migrate!
  "Applies pending migrations to the database.

  - (migrate! migrations-dir-path db-conn-conf)
    Applies all pending migrations.

  - (migrate! migrations-dir-path migration-name db-conn-conf)
    Applies migrations up to and including `migration-name`.

  - (migrate! migrations-dir-path migration-name apply-previous? db-conn-conf)
    If `apply-previous?` is true, applies all migrations up to and including
    `migration-name`. Otherwise, applies only the specified migration."
  ([migrations-dir-path migration-name apply-previous? db-conn-conf])
  ([migrations-dir-path migration-name db-conn-conf])
  ([migrations-dir-path db-conn-conf]))

(defn rollback!
  "Rolls back applied migrations to the database.

  - (rollback! migrations-dir-path db-conn-conf)
    Rolls back the last successfully applied migration.

  - (rollback! migrations-dir-path migration-name db-conn-conf)
    Rolls back all migrations up to and including `migration-name`."
  ([migrations-dir-path db-conn-conf])
  ([migrations-dir-path migration-name db-conn-conf]))

(defn retrieve-migrations
  "Retrieves all existent migrations.

  - (retrieve-migrations migrations-dir-path)
    Retrieves all migrations without checking their status.

  - (retrieve-migrations migrations-dir-path return-appliance-status? db-conn-conf)
    If `return-appliance-status?` is true, returns all migrations with their appliance status."
  ([migrations-dir-path])
  ([migrations-dir-path return-appliance-status? db-conn-conf]))
