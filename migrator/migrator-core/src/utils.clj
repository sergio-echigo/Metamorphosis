(ns migrator.utils
  (:require [migrator.specs :as specs]
            [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clojure.edn :as edn])
  (:import (java.io File)))

;; Taken from https://clojuredocs.org/clojure.edn/read
(defn- load-edn
  "Load edn from an io/reader source (filename or io/resource)."
  [source]
  (try
    (with-open [r (io/reader source)]
      (edn/read (java.io.PushbackReader. r)))

    (catch java.io.IOException e
      (printf "Couldn't open '%s': %s\n" source (.getMessage e))
      nil)
    (catch RuntimeException e
      (printf "Error parsing edn file '%s': %s\n" source (.getMessage e))
      nil)))

(defn retrieve-edn-files!
  "Returns a sequence of .edn files relative paths."
  [dir-path]
  (let [java-dir (File. dir-path)]
    (when (.isDirectory java-dir)
      (->> (.listFiles java-dir)
           (seq)
           (filter #(and (.isFile %)
                         (.endsWith (.getName %) ".edn")))
           (map #(.getPath %))))))

(defn edn-files->migrations-report
  [edn-files migration-id apply-previous?]
  (let [file-count (count edn-files)]
    (reduce (fn [r f] (let [loaded-edn (load-edn f)
                            id (:id loaded-edn)
                            migration-report {f {:valid? (s/valid? :migration/migration loaded-edn)
                                                 :id id
                                                 :timestamp (:timestamp loaded-edn)}}
                            full-report (assoc-in r [:migrations-report f] migration-report)]
                        (cond

                          ;; All previous migrations MUST BE applied.
                          (or apply-previous?
                              (not migration-id))
                          full-report

                          ;; A specific migration MUST BE applied and it has been found:
                          (= migration-id id)
                          (reduced {:count 1 :migrations-report migration-report})

                          ;; A specific migration MUST BE applied and it has not been found yet:
                          :else r))) {:count file-count :migrations-report {}} edn-files))))

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
