(ns migrator.database.interface
  (:require [migrator.database.core :as database]))

(defn migrate!
  "Applies a set of migrations into the database.

  Parameters:
  - `migrations-directory-path`: the path of a directory containing multiple migrations files;
  - `db-connection-configuration`: a valid next.jdbc datasource with information about the database connection;
  - `options`: a map containing some options.

  Available options (`options` parameter):
  - `:apply-previous?`, false by default: controls whether previous migrations should be applied;
  - `:ignore-invalid-edns`, false by default: whether this function should ignore invalid migration EDNs or return an error;
  - `:migration-id`, nil by default: a specific migration identifier to be the unique/last being applied;
  - `:verify-internal-migrations-table`, true by default: whether this function should ignore already applied migrations or not.

  This function returns a JSON string containing the following keys:
  - `error?`: whether this operation was successfully or not;
  - `reason`: the reason for the returned error (if any);
  - `value`: metadata about the error or the executed migrations."
  [migrations-directory-path db-connection-configuration options]
  (database/migrate! migrations-directory-path db-connection-configuration options))

(defn rollback!
  "Rollbacks a set of migrations from the database.

  Parameters:
  - `migrations-directory-path`: the path of a directory containing multiple migrations files;
  - `db-connection-configuration`: a valid next.jdbc datasource with information about the database connection;
  - `options`: a map containing some options.

  Available options (`options` parameter):
  - `:apply-previous?`, false by default: controls whether next migrations from the one specified should be rollbacked;
  - `:ignore-invalid-edns`, false by default: whether this function should ignore invalid migration EDNs or return an error;
  - `:migration-id`, nil by default: a specific migration identifier to be the unique/last being rollbacked;
  - `:verify-internal-migrations-table`, true by default: whether this function should ignore non applied migrations or not.

  This function returns a JSON string containing the following keys:
  - `error?`: whether this operation was successfully or not;
  - `reason`: the reason for the returned error (if any);
  - `value`: metadata about the error or the rollbacked migrations."
  [migrations-directory-path db-connection-configuration options]
  (database/rollback! migrations-directory-path db-connection-configuration options))



