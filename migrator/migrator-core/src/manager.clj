(ns migrator.manager
  (:require [next.jdbc :as jdbc]
            [migrator.sql :as sql]
            [next.jdbc.result-set :as rs]))

(defn- execute-query!
  "Executes a SQL query into the specified datasource."
  [ds sql & args]
  (jdbc/execute! ds (cons args sql) {:return-keys true
                                     :builder-fn rs/as-unqualified-lower-maps}))

(defn migrations-table-exists?
  "Checks if the _migrations table do exists."
  [ds]
  (try
    (let [exists? (empty? (execute-query! ds sql/CHECK_MIGRATIONS_TABLE_EXISTENCE))]
      {:value exists?})
    (catch Exception e
      {:error? true
       :message "Exception when trying to validate _migrations table existence. Check database connection."})))

(defn create-migrations-table!
  [ds]
  (try
    (execute-query! ds sql/CREATE_MIGRATIONS_TABLE)
    (catch Exception e
      {:error? true
      :message "Exception when trying to create table _migrations."})))

(defn select-applied-migrations
  [ds]
  (try
    {:value (execute-query! ds sql/SELECT_APPLIED_MIGRATIONS)}
    (catch Exception e
      {:error? true
       :message "Exception when trying to select all migrations from _migrations."})))
