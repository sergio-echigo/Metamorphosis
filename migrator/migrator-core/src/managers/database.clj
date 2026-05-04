(ns migrator.managers.database
  (:require [next.jdbc :as jdbc]
            [migrator.sql :as sql]
            [next.jdbc.result-set :as rs]))

(defn- execute-query!
  "Executes a SQL query into the specified datasource."
  [ds sql & args]
  (try
    {:value (jdbc/execute! ds (cons sql (or args [])) {:return-keys true
                                                       :builder-fn rs/as-unqualified-lower-maps})}

    (catch Exception e
      {:error? true
       :message (str "Exception when trying to execute SQL query " sql ": " e)})))

(defn- insert-migration!
  [ds migration-id]
  (execute-query! ds sql/INSERT_MIGRATION migration-id (str (.toEpochMilli (java.time.Instant/now)))))

(defn- delete-migration!
  [ds migration-id]
  (execute-query! ds sql/DELETE_MIGRATION migration-id))

(defn migrations-table-exists?
  "Executes a query against the database that verifies if the '_migrations' table do exists in the specified datasource."
  [ds]
  (not (empty? (:value (execute-query! ds sql/CHECK_MIGRATIONS_TABLE_EXISTENCE)))))

(defn create-migrations-table!
  "Executes a query against the database that creates the '_migrations' table."
  [ds]
  (execute-query! ds sql/CREATE_MIGRATIONS_TABLE))

(defn select-applied-migrations
  "Executes a query against the database that selects all existent records from the '_migrations' table."
  [ds]
  (->> sql/SELECT_APPLIED_MIGRATIONS
       (execute-query! ds)
       :value
       (map :id)))

(defn apply-statements!
  "Applies multiple SQL statements into a database.
  
  It imediately stops the execution if any of the statements fail."
  [ds statements rollback?]
  (let [internal-migrations-operation (if rollback? delete-migration! insert-migration!)]

    ;; Looping through each statement
    (loop [[{:keys [migration-id name query]} & remaining-statements] statements]

      ;; Executing the statement query
      (let [{:keys [message error?] :as result} (execute-query! ds query)]

        ;; If any error happens, returning immediately.
        (if error?
          (assoc result :metadata {:failed-statement name})

          ;; If no statement needs to be applied anymore, then altering the internal migrations table.
          ;; Otherwise, continuing the normal flow.
          (if (empty? remaining-statements)
            (internal-migrations-operation ds migration-id)
            (recur remaining-statements)))))))
