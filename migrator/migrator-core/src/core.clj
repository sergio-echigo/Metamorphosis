(ns migrator.core
  (:require [migrator.utils :as utils]
            [next.jdbc.specs :as db-specs]
            [migrator.managers.database :as database]
            [clojure.spec.alpha :as s]
            [next.jdbc :as jdbc]
            [cheshire.core :as json]))

(defn- appliance-type->undo-type [appliance-type]
  (if (= appliance-type :apply)
    :rollback
    :apply))

(defn- handle-db-op! [op & args]
  (let [{:keys [value error? message]} (apply op args)]
    (if error?
      (do
        (println "Error when interacting with database.")
        (println (str "Message: " message))
        (System/exit 1))
      value)))

(defn- apply-migrations [ds migrations appliance-type]
  (let [undoo-type (if (= appliance-type :apply) :rollback :apply)]
    (reduce
      (fn [{:keys [applied-migrations failed-migration undo-statements-history] :as result} [filepath {:keys [id timestamp]}]]
        (let [{:keys [statements]} (utils/load-migration-edn filepath)
              apply-statements (reduce (fn [r [name queries]] (assoc r name (appliance-type queries))) {} statements)
              undoo-statements (reduce (fn [r [name queries]] (let [undo-statement (undoo-type queries)] (if (and (not (nil? undo-statement)) (apply-statements name)) (assoc r name (undoo-type queries)) r))) {} statements)]
          (if-let [failed-statement (-> (database/apply-statements! ds apply-statements)
                                        (get-in [:metadata :failed-statement]))]
            (reduced (-> result
                         (update :undo-statements-history into (reduce (fn [r [n q]]
                                                                         (if (= n failed-statement)
                                                                           (reduced r)
                                                                           (assoc r n q))) {} undoo-statements))
                         (assoc :failed-migration id)
                         (assoc :error failed-statement)))
            (-> result
                (update :undo-statements-history into undoo-statements)
                (update :successful-migrations into [id]))))))
    {:undo-statements-history '()
     :failed-migration nil
     :error nil
     :successful-migrations []}
    migrations))

(defn- apply-and-rollback-migrations [ds migrations appliance-type]
  (let [{:keys [undo-statements-history failed-migration error successful-migrations] :as r} (apply-migrations ds migrations appliance-type)]
    (println r)
    (if failed-migration
      (let [error (database/apply-statements! ds undo-statements-history)]
        (println (str "ERRORRRRRRRR: " error))
        (if (:failed-statement error)
          (println error)
          (println error))))))

(defn- standardized-response
  [error? reason value])

(defn migrate!
  "Purpose:
  - Applies migrations into the database.

  Returns:
  - A dictionary containing possible error information."
  ([migrations-dir-path db-conn-conf ignore-invalid-edns? migration-id apply-previous?]

   ;; Validating if database connection configuration is in the correct model:
   (if-not (s/valid? :next.jdbc.specs/db-spec db-conn-conf)
     (standardized-response true "`db-conn-conf` is not in the required model. Please, use the :next.jdbc.specs/db-spec model.")

     ;; Obtaining migrations from the provided directory, filtering by valid migrations and sorting them by their timestamp.
     (let [{:keys [migrations-count migrations]} (-> migrations-dir-path
                                                     (utils/retrieve-edn-files!)
                                                     (utils/edn-files->migrations migration-id apply-previous?))
           migrations (into {} (sort-by utils/report->timestamp (filter utils/valid-migration? migrations)))]

       ;; Taking a lot of validations:
       (cond
         (empty? migrations)
         (utils/die! "No migration has been found in the provided directory.")

         (and (not ignore-invalid-edns?)
              (not= migrations-count (count migrations)))
         (utils/die! (str "Invalid migrations have been found in the provided directory: " (seq (filter #(not (utils/valid-migration? %)) migrations))))

         (and migration-id
              (not apply-previous?)
              (utils/duplicated-ids? migrations))
         (utils/die! "There is duplicated, critical informations across all migrations!")

         :else
         nil)

       ;; Obtaining datasource from db-conn-conf
       (let [ds (jdbc/get-datasource db-conn-conf)
             exists? (handle-db-op! database/migrations-table-exists? ds)
             applied-migrations (if exists? (handle-db-op! database/select-applied-migrations ds) [])
             migrations (into {} (filter (fn [[_ {:keys [id timestamp]}]] (nil? (some #(= (:id %) id) applied-migrations))) migrations))]

         ;; Creating internal _migrations table if it not exists:
         (when-not exists?
           (handle-db-op! database/create-migrations-table! ds)
           (println "_migrations table has been created."))

         ;; Applying every migration
         (println (json/generate-string (apply-and-rollback-migrations ds migrations :apply))))))
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
