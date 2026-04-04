(ns migrator.specs
  (:require [clojure.spec.alpha :as s])
  (:import java.time.Instant))

;; Utilitary functions for specifications.
(defn- unix? [timestamp]
  (try
    (Instant/ofEpochMilli timestamp)
    (catch Exception _
      false)))

;; Migration specification
(s/def :statement/apply #(not (clojure.string/blank? %)))
(s/def :statement/rollback #(not (clojure.string/blank? %)))

(s/def :statement/key #(not (clojure.string/blank? %)))
(s/def :statement/value (s/keys :req-un [:statement/apply]
                                :opt-un [:statement/rollback]))

(s/def :migration/id #(uuid? (parse-uuid %)))
(s/def :migration/timestamp (s/and int? pos? unix?))
(s/def :migration/description string?)
(s/def :migration/statements (s/map-of :statement/key :statement/value))

(s/def :migration/migration (s/keys :req-un [:migration/id :migration/timestamp :migration/statements]
                                    :req-opt [:migration/description]))
