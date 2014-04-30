(ns steph-scrape.core
  (:use [steph-scrape.store]
        [clojure.tools.logging :only (info)]))

;;Constants...
(def csv-path "resources/big-init-urls.csv")
(def db "steph-scrape-top40-huge")

(defn test-run []
  (info "Beginning Test Run") 
  (let [store (init-db db)]
    (preload-db store csv-path)
    (info "Preloaded DB with URLs")
    (future (retrieve-unfetched-records store))))

(defn continue-run []
  (let [store (get-db db)]
    (future (retrieve-unfetched-records store))))
