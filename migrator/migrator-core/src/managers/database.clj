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

(defn migrations-table-exists?
  "Executes a query against the database that verifies if the '_migrations' table do exists in the specified datasource."
  [ds]
  (execute-query! ds sql/CHECK_MIGRATIONS_TABLE_EXISTENCE))

(defn create-migrations-table!
  "Executes a query against the database that creates the '_migrations' table."
  [ds]
  (execute-query! ds sql/CREATE_MIGRATIONS_TABLE))

(defn select-applied-migrations
  "Executes a query against the database that selects all existent records from the '_migrations' table."
  [ds]
  (execute-query! ds sql/SELECT_APPLIED_MIGRATIONS))

(defn apply-statements!
  "Applies multiple SQL statements into a database.
  
  It imediately stops the execution if any of the statements fail."
  [ds statements]
  (reduce
    (fn [error [statement-name statement-query]]
      (let [{:keys [message error?] :as result} (execute-query! ds statement-query)]
        (if error?
          (reduced (assoc result :metadata {:failed-statement statement-name}))
          result)))
    nil
    statements))
