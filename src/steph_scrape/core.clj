(ns steph-scrape.core
  (:use [steph-scrape.store]
        [clojure.tools.logging :only (info)]))

;;Constants...
(def csv-path "resources/init-urls.csv")

(defn test-run []
  (info "Beginning Test Run") 
  (future (let [store (init-db "steph-scrape-top40")]
    (preload-db store csv-path)
    (update-all-records store))))
