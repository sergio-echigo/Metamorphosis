(ns migrator.core
  (:require [migrator.utils :as utils]
            [next.jdbc.specs :as db-specs]
            [migrator.managers.database :as database]
            [migrator.managers.migrations :as migrations]
            [clojure.spec.alpha :as s]
            [next.jdbc :as jdbc]
            [cheshire.core :as json]))

(defn- appliance-type->undo
  [appliance-type]
  (if (= appliance-type :apply)
    :rollback
    :apply))

(defn- retrieve-migration-statements
  "Loads the EDN content from the provided file path and retrieve its statements.

  The exec and undo statements are defined based on the 'appliance-type' param.

  - Exec -> What is going the be applied;
  - Undo -> What needs to be applied to rollback the exec statement.
  They don't necessarily represent an :apply and :rollback statements, respectively.

  Returns a map containing the ':exec-statements' and ':undo-statements' keywords."
  [migration-id migration-file-path appliance-type]
  (let [statements (-> migration-file-path
                       utils/load-edn
                       utils/migration-edn->statements)
        exec-appliance-type (if (= appliance-type :apply) :apply :rollback)
        undo-appliance-type (appliance-type->undo exec-appliance-type)
        
        exec-statements (map (fn [[statement-name {query exec-appliance-type}]] {:name statement-name 
                                                                                 :query query 
                                                                                 :migration-id migration-id}) statements)

        undo-statements (map (fn [[statement-name {query undo-appliance-type}]] {:name statement-name 
                                                                                 :query query 
                                                                                 :migration-id migration-id}) statements)]
    {:exec-statements exec-statements
     :undo-statements undo-statements}))

(defn- apply-migrations-into-database [ds migrations appliance-type]
  "Iterates over a collection of migrations and execute their statements.

  The collection iteration is imediately stopped if any statement fail.

  The 'appliance-type' param. defines whether the statement :apply or :rollback is executed.

  Returns a map containing the following information:
  - :undo-statements-history -> A map of statements that should be executed to rollback the executed statements;
  - :failed-migration -> A map containing information about a failed migration -- or nil;
  - :successfully-executed-migrations -> A vector containing all migrations that could be successfully executed."
  (update
    (reduce
      (fn [result {:keys [id file-path]}]
        (let [{:keys [exec-statements undo-statements]} (retrieve-migration-statements id file-path appliance-type)
              {:keys [error? message], {:keys [failed-statement]} :metadata} (database/apply-statements! ds exec-statements (= appliance-type :rollback))]
          (if error?
            (reduced (-> result
                         (update :undo-statements-history into (take-while (fn [{:keys [name]}] (not= failed-statement name)) (reverse undo-statements)))
                         (assoc-in [:failed-migration :id] id)
                         (assoc-in [:failed-migration :failed-statement :name] failed-statement)
                         (assoc-in [:failed-migration :failed-statement :reason] message)))
            (-> result
                (update :undo-statements-history into (reverse undo-statements))
                (update :successfully-executed-migrations into [id])))))
      {:appliance-order (map :id migrations)
       :undo-statements-history []
       :successfully-executed-migrations []}
      migrations)
    :undo-statements-history
    reverse))

(defn- apply-and-rollback-migrations [ds migrations appliance-type]
  "Applies a collection of migrations using the `apply-migrations-into-database` function. It uses the returned `:undo-statements-history` map to rollback
  all executed statements if any of them fail.

  If any undo statement execution fail the rest of the statements are not executed.

  Returns the returned value of the `apply-migrations-into-database` function but it appends the `:failed-undo-statement` attribute to return information about
  any 'undo' statement that have failed."
  (let [{:keys [undo-statements-history failed-migration failed-statement successfully-executed-migrations] :as r} (apply-migrations-into-database ds migrations appliance-type)]
    (if (and
          (not (empty? undo-statements-history))
          (:id failed-migration))
      (let [undo-statements-history (filter (fn [{:keys [query]}] (not (nil? query))) undo-statements-history)
            {:keys [error? message metadata]} (database/apply-statements! ds undo-statements-history (= appliance-type :apply))]
        (if error?
          (assoc r :failed-undo-statement {:name (:failed-statement metadata) :reason message})
          r))
      r)))

(defn- standardized-response
  "Returns a standardized response."
  ([error? reason value]
   (json/generate-string {:error? error?
                          :reason reason
                          :value value}))
  ([error? reason]
   (standardized-response error? reason nil)))

(defn- get-migrations-report
  "Retrieves a report of potential migrations in the provided directory.

  The parameters 'migration-id' and 'apply-previous?' control the migrations to be returned.

  It returns a collection of maps in which each map contains:
  - File path of the file (:file-path);
  - Id of migration (:id) or nil;
  - Timestamp of migration (:timestamp) or nil.

  Important details:
  - The migrations are not sortered;
  - Migrations that MUST NOT be applied may be returned (it is necessary to filter them later).

  The second detail is based on the fact that migration files may not be ordered based on their file creation."
  [migrations-dir-path migration-id apply-previous?]
  (-> migrations-dir-path
      utils/retrieve-edn-files!
      utils/map-edns-content
      (migrations/edn-files->migrations-report migration-id apply-previous?)))

(defn- migrations-report->valid-sorted-migrations
  "Transforms a report of migrations into a collection of valid, sorted migrations.

  The parameter 'appliance-type' controls its sort order (:apply for ascending, any other value for descending).

  Returns the report but filtered for only valid migrations that must be applied."
  [migrations-report appliance-type migration-id apply-previous?]
  (let [specific-migration (filter (fn [{:keys [id, valid-migration?]}] (and (= migration-id id) valid-migration?)) migrations-report)]
    (if (and migration-id
            (not apply-previous?))
      specific-migration

      ;; Here, all migrations "until" migration-id (including it) need to be returned:
      (let [c (if (= appliance-type :apply) < >)
            sorted-valid-migrations (->> migrations-report
                                         (filter :valid-migration?)
                                         (sort-by :timestamp c)
                                         (take-while (fn [{id :id}] (not= migration-id id))))]

        (concat sorted-valid-migrations specific-migration)))))

(defn- handle-migration-execution
  [migrations-dir-path db-conn-conf appliance-type {:keys [apply-previous?
                                                           ignore-invalid-edns?
                                                           migration-id
                                                           verify-internal-migrations?]

                                                    :or {apply-previous? true
                                                         ignore-invalid-edns? false
                                                         migration-id nil
                                                         verify-internal-migrations true}}]

  ;; Validating db-conn-conf
  (if-not (s/valid? :next.jdbc.specs/db-spec db-conn-conf)
     (standardized-response true "`db-conn-conf` is not in the required model. Please, use the :next.jdbc.specs/db-spec model.")

     ;; Obtaining migrations from the provided directory, filtering by valid migrations and sorting them by their timestamp.
     (let [migrations-report (get-migrations-report migrations-dir-path migration-id apply-previous?)
           migrations-report-count (count migrations-report)

           valid-migrations (migrations-report->valid-sorted-migrations migrations-report appliance-type migration-id apply-previous?)
           valid-migrations-count (count valid-migrations)

           ds (jdbc/get-datasource db-conn-conf)
           
           internal-migrations-table-exists? (database/migrations-table-exists? ds)
           applied-migrations (if (and verify-internal-migrations? internal-migrations-table-exists?) (set (database/select-applied-migrations ds)) #{})

           target-filtering-function (if (= appliance-type :rollback) identity complement)
           valid-migrations (if verify-internal-migrations? 
                              (filter (-> applied-migrations
                                          target-filtering-function
                                          (comp :id))
                                      valid-migrations)
                              valid-migrations)]

       ;; Creating the _migrations table if it does not exists:
       (when-not internal-migrations-table-exists?
         (database/create-migrations-table! ds))

       ;; Doing some validations and continuing the normal flow:
       (cond

         ;; No migration to be applied:
         (empty? valid-migrations)
         (if (and verify-internal-migrations? (not= 0 valid-migrations-count))
           (standardized-response false "No valid migration needs to be applied.")
           (standardized-response true "No valid migration was found in the provided directory."))

         ;; Invalid EDNs were found:
         (and (not ignore-invalid-edns?)
              (not= migrations-report-count valid-migrations-count))
         (standardized-response true (str "Invalid migrations have been found in the provided directory: " (seq (filter (complement :valid-migration?) migrations-report))))

         ;; Critical identifiers are duplicated:
         (migrations/duplicated-identifiers? migrations-report migrations-report-count)
         (standardized-response true "There is duplicated, critical informations across all migrations!")

         ;; A specific migration needs to be applied and it is not valid:
         (and migration-id
              (nil? (some (fn [{id :id}] (= migration-id id)) valid-migrations)))
         (standardized-response true "The requested migration is not valid.")

         :else
         (let [{:keys [failed-migration failed-undo-statement successfully-executed-migrations] :as r} (apply-and-rollback-migrations ds valid-migrations appliance-type)
               error? (not (nil? failed-migration))]
           (standardized-response error? (if error? "A migration was not successfully executed. See the :failed-migration attribute to understand its root cause." "") r))))))

(defn migrate!
  "Purpose:
  - Applies migrations into the database.

  Returns:
  - A dictionary containing possible error information."
  ([migrations-dir-path db-conn-conf options]
   (handle-migration-execution migrations-dir-path db-conn-conf :apply options))

  ([migrations-dir-path db-conn-conf]
   (handle-migration-execution migrations-dir-path db-conn-conf :apply)))

(defn rollback!
  "Rolls back applied migrations to the database.

  - (rollback! migrations-dir-path db-conn-conf)
  Rolls back the last successfully applied migration.

  - (rollback! migrations-dir-path migration-name db-conn-conf)
  Rolls back all migrations up to and including `migration-id`."
  ([migrations-dir-path db-conn-conf options]
   (handle-migration-execution migrations-dir-path db-conn-conf :rollback options))

  ([migrations-dir-path db-conn-conf]
   (handle-migration-execution! migrations-dir-path db-conn-conf :rollback)))

(defn retrieve-migrations
  "Retrieves all existent migrations.

  - (retrieve-migrations migrations-dir-path)
  Retrieves all migrations without checking their status.

  - (retrieve-migrations migrations-dir-path return-appliance-status? db-conn-conf)
  If `return-appliance-status?` is true, returns all migrations with their appliance status."
  ([migrations-dir-path])
  ([migrations-dir-path return-appliance-status? db-conn-conf]))
