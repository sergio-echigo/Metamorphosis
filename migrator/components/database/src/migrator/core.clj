(ns migrator.database.core
  (:require [migrator.database.db :as db]))

(defn- handle-migration-execution [migrations-directory-path db-connection-configuration appliance-type options]





(defn migrate!
  [migrations-directory-path db-connection-configuration options])

(defn rollback!
  [migrations-directory-path db-connection-configuration options])




