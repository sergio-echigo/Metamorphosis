(ns migrator.utils
  (:require [migrator.specs :as specs]
            [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clojure.edn :as edn])
  (:import (java.io File)))

;; Taken from https://clojuredocs.org/clojure.edn/read
(defn load-edn
  "Load edn from an io/reader source (filename or io/resource)."
  [source]
  (try
    (with-open [r (io/reader source)]
      (edn/read (java.io.PushbackReader. r)))

    (catch java.io.IOException e
      (println e))
    (catch RuntimeException e
      (println e))))

(defn retrieve-edn-files!
  "Returns a sequence of .edn files relative paths."
  [dir-path]
  (try
    (let [java-dir (File. dir-path)]
      (when (.isDirectory java-dir)
        (->> (.listFiles java-dir)
            (seq)
            (filter #(and (.isFile %)
                          (.endsWith (.getName %) ".edn")))
            (map #(.getPath %)))))
    (catch Exception e
      [])))

(defn edn-files->migrations
  [edn-files migration-id apply-previous?]
  (let [file-count (count edn-files)]
    (reduce (fn [r f] (let [loaded-edn (load-edn f)
                            id (:id loaded-edn)
                            migration-report {:valid? (s/valid? :migration/migration loaded-edn)
                                              :id id
                                              :timestamp (:timestamp loaded-edn)}
                            full-report (assoc-in r [:migrations f] migration-report)]
                        (cond

                          ;; All previous migrations MUST BE applied.
                          (or apply-previous?
                              (not migration-id))
                          full-report

                          ;; A specific migration MUST BE applied and it has been found:
                          (= migration-id id)
                          (reduced {:count 1 :migrations (assoc {} f migration-report)})

                          ;; A specific migration MUST BE applied and it has not been found yet:
                          :else r))) {:count file-count :migrations {}} edn-files)))

(defn load-migration-edn [filepath]
  "Reads a EDN file and returns migration-specific data."
  (let [{:keys [description, statements]} (load-edn filepath)]
    {:description description
     :statements statements}))

(defn valid-migration?
  "Verifies whether a "
  [[file-path {:keys [valid?]}]]
  valid?)

(defn report->timestamp [[_ {:keys [timestamp]}]]
  timestamp)

(defn duplicated-ids?
  "Verifies if there is any migration with duplicated critical information."
  [migrations]
  (let [count (count migrations)
        values (vals migrations)]
    (or (not= count (count (distinct (map :id values))))
        (not= count (count (distinct (map :timestamp values)))))))

(defn die!
  "Prints a message and terminates the application with an error code."
  [message]
  (println message)
  (System/exit 1))
