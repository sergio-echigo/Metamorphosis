(ns migrator.manager
  (:require [next.jdbc :as jdbc]
            [migrator.sql :as sql]
            [next.jdbc.result-set :as rs]))

(defn- execute-query!
  "Executes a SQL query into the specified datasource."
  [ds sql & args]
  (try
    (println (str "Executing the following SQL query: " sql))
    {:value (jdbc/execute! ds (cons sql (or args [])) {:return-keys true
                                                       :builder-fn rs/as-unqualified-lower-maps})}

    (catch Exception e
      {:error? true
       :message (str "Exception when trying to execute SQL query " sql ": " e)})))

(defn migrations-table-exists?
  "Checks if the _migrations table do exists."
  [ds]
  (let [{:keys [value error? message]} (execute-query! ds sql/CHECK_MIGRATIONS_TABLE_EXISTENCE)]
    (if error?
      {:error? true :message message}
      {:value (not (empty? value))})))

(defn create-migrations-table!
  [ds]
  (let [{:keys [value error? message]} (execute-query! ds sql/CREATE_MIGRATIONS_TABLE)]
    (if error?
      {:error? true :message message}
      value)))

(defn select-applied-migrations
  [ds]
  (let [{:keys [value error? message]} (execute-query! ds sql/SELECT_APPLIED_MIGRATIONS)]
    (if error?
      {:error? true :message message}
      value)))

(defn apply-statements!
  [ds statements]
  (reduce
    (fn [{:keys [success? reason failed-statement] :as error} [name query]]
      (let [{:keys [error? message]} (execute-query! ds query)]
        (if error?
          (reduced {:failed-statement name
                    :reason message})
          error)))
    {:failed-statement nil
     :reason ""}
    statements))
