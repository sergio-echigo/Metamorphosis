(ns migrator.core
  (:require [migrator.utils :as utils]
            [next.jdbc.specs :as db-specs]
            [migrator.manager :as manager]))

(defn migrate!
  "Applies pending migrations to the database.

  - (migrate! migrations-dir-path db-conn-conf)
    Applies all pending migrations.

  - (migrate! migrations-dir-path migration-name db-conn-conf)
    Applies migrations up to and including `migration-id`.

  - (migrate! migrations-dir-path migration-name apply-previous? db-conn-conf)
    If `apply-previous?` is true, applies all migrations up to and including
    `migration-id`. Otherwise, applies only the specified migration."
  ([migrations-dir-path db-conn-conf ignore-invalid-edns? migration-id apply-previous?]

    ;; Validating if database connection configuration is in the correct model:
    (if (not (s/valid? :next.jdbc.specs/db-spec db-conn-conf))
      (println "`db-conn-conf` is not in the required model. Please, use the :next.jdbc.specs/db-spec model.")
      (let [{:keys [count migrations-report]} (-> migrations-dir-path
                                                  (utils/retrieve-edn-files! )
                                                  (utils/edn-files->migrations-report migration-id apply-previous?))]

        ;; Verifying if at least one migration is found:
        (if (empty? migrations-report)
          (println "No migration has been found to be applied.")

          ;; Exiting if any migration is invalid and if invalid migrations are not expected:
          (if (and (not ignore-invalid-edns?)
                   (some #(not (utils/valid-migration? %)) migrations-report))
            (println (str "Invalid migrations have been found in the provided directory: " (seq (filter #(not (utils/valid-migration? %)) migrations-report))))

            ;; Obtaining only valid migrations and sorting them by timestamp
            (let [valid-migrations (sort-by report->timestamp (filter utils/valid-migration? migrations-report))]

              ;; Checking if there's any critical information that is duplicated across other migrations.
              (if (and migration-id
                       (not apply-previous?)
                       (duplicated-migrations? valid-migrations))
                (println "There is duplicated, critical informations across all migrations!")

                ;; Obtaining datasource from db-conn-conf
                (let [ds (jdbc/get-datasource db-conn-conf)
                      migrations-table-exists? (manager/migrations-table-exists? ds)
                      applied-migrations (if migrations-table-exists? [] (:value (manager/select-applied-migrations)))
                      valid-migrations (filter (fn))]

                  ;; Creating internal _migrations table if it not exists:
                  (when-not migrations-table-exists?
                    (manager/create-migrations-table! ds))))))))))





          ;; Verifying if there is any repeated id:
          ;; Verifying if there is any repeated timestamp:
          ;; validate migration-id
          ;; validate db-conn-conf
  ([migrations-dir-path db-conn-conf ignore-invalid-edns? migration-id]
    (migrate! migrations-dir-path db-conn-conf ignore-invalid-edns? migration-id true))
  ([migrations-dir-path db-conn-conf ignore-invalid-edns?]
    (migrate! migrations-dir-path db-conn-conf ignore-invalid-edns? nil true))
  ([migrations-dir-path db-conn-conf]
    (migrate! migrations-dir-path db-conn-conf false nil true)))

(defn rollback!
  "Rolls back applied migrations to the database.

  - (rollback! migrations-dir-path db-conn-conf)
    Rolls back the last successfully applied migration.

  - (rollback! migrations-dir-path migration-name db-conn-conf)
    Rolls back all migrations up to and including `migration-id`."
  ([migrations-dir-path db-conn-conf])
  ([migrations-dir-path migration-id db-conn-conf]))

(defn retrieve-migrations
  "Retrieves all existent migrations.

  - (retrieve-migrations migrations-dir-path)
    Retrieves all migrations without checking their status.

  - (retrieve-migrations migrations-dir-path return-appliance-status? db-conn-conf)
    If `return-appliance-status?` is true, returns all migrations with their appliance status."
  ([migrations-dir-path])
  ([migrations-dir-path return-appliance-status? db-conn-conf]))
