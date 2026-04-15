(ns migrator.managers.migrations
  (:require [migrator.utils :as utils]
            [migrator.specs :as specs]
            [clojure.spec.alpha :as s]))

(defn edn-files->migrations-report
  "Iterates over a sequence of maps and returns a migrations report from it.
  
  Each map represents EDN file information. Each map MUST HAVE:
  - The key :file-path (path to an EDN file);
  - The key :content (EDN file's content).
  
  For performance reasons, it is better that a lazy sequence is passed as the edn-files argument."
  ([edn-files migration-id apply-previous?]
   (reduce 
     (fn [reports {:keys [file-path content]}]
       (let [{:keys [id]} content
             report-information {:valid-migration? (s/valid? :migration/migration content) :file-path file-path}
             report (-> content
                        (select-keys [:id :timestamp])
                        (into report-information))]
         (cond

           ;; A specific migration MUST BE found and it was found. Previous migrations may be applied (see the second conditional)
           (and migration-id
                (= migration-id id))
           (reduced (conj reports report))

           ;; Only previous OR all migrations need to be applied.
           (or apply-previous?
               (not migration-id))
           (conj reports report)

           ;; A specific migration MUST BE applied (and only it).
           :else
           reports)))
     []
     edn-files))
  ([edn-files migration-id]
   (edn-files->migrations-report edn-files migration-id true))
  ([edn-files]
   (edn-files->migrations-report edn-files nil true)))
