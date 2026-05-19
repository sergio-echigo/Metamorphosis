(ns migrator.database.db
  (:require [migrator.database.sql :as sql]))

(defn execute!
  "Executes a SQL query into the database.
  
  Returns a map containing the following keys:
  - :value -> the result of the executed SQL query;
  - :error? -> a boolean value that indicates if the query was successful or not;
  - :message -> a string containing the message error (if any)."
  [ds sql & args]
  (try
    {:value (jdbc/execute! ds (cons sql (or args [])) {:return-keys true
                                                       :builder-fn rs/as-unqualified-lower-maps})}
    (catch Exception e
      {:error? true
       :message (str "Exception when trying to execute the SQL query '" sql "': '" e)})))

(defn register-applied-migration! 
  "Inserts a new migration entry into the internal migrations table.
  
  It expects the following parametes:
  - ds: a valid next.jdbc datasource map;
  - migration-id: a valid Migration identifier."
  [ds migration-id]
  (execute! ds sql/REGISTER_APPLIED_MIGRATION migration-id (str (.toEpochMilli (java.time.Instant/now)))))

(defn unregister-rollbacked-migration! 
  "Deletes a migration entry from the internal migrations table.
  
  It expects the following parametes:
  - ds: a valid next.jdbc datasource map;
  - migration-id: a valid Migration identifier."
  [ds migration-id]
  (execute! ds sql/UNREGISTER_ROLLBACKED_MIGRATION migration-id))

(defn internal-migrations-table-exists?
  "Verifies if the internal migrations table exists.
  
  It expects a valid next.jdbc datasource.
  
  Returns a boolean value indicating whether the table exists or not."
  [ds]
  (not (empty? (:value (execute! ds sql/CHECK_MIGRATIONS_TABLE_EXISTENCE)))))

(defn create-internal-migrations-table!
  "Creates the internal migrations table.
  
  It expects a valid next.jdbc datasource."
  [ds]
  (execute! ds sql/CREATE_MIGRATIONS_TABLE))

(defn retrieve-registered-migrations
  "Retrieves all registered (applied) migrations.

  It expects a valid next.jdbc datasource.

  Returns a sequence of identifiers of migrations."
  [ds]
  (->> sql/SELECT_APPLIED_MIGRATIONS
       (execute! ds)
       :value
       (map :id)))

(defn apply-migration-statements!
  "Executes multiple statements from a single migration.

  It expects a valid next.jdbc datasource, the statements map and a boolean value indicating whether this is a rollback operation. 
  It imediately stops the execution if any of the statements fail.
  
  Returns a map containing information about the execution."
  [ds statements rollback?]
  (let [internal-migrations-operation (if rollback? unregister-rollbacked-migration! register-applied-migration!)]

    ;; Looping through each statement
    (loop [[{:keys [migration-id name query]} & remaining-statements] statements]

      ;; Executing the statement query
      (let [{:keys [message error?] :as result} (execute! ds query)]

        ;; If any error happens, returning immediately.
        (if error?
          (assoc result :metadata {:failed-statement name})

          ;; If no statement needs to be applied anymore, then altering the internal migrations table.
          ;; Otherwise, continuing the normal flow.
          (if (empty? remaining-statements)
            (internal-migrations-operation ds migration-id)
            (recur remaining-statements)))))))
