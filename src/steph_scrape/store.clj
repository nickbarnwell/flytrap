(ns steph-scrape.store
  (:use [clojure.tools.logging :only (info error debug)])
  (:require [com.ashafa.clutch :as c]
            [clojure-csv.core :as csv]
            [boilerpipe-clj.core :refer [get-text]]
            [com.climate.claypoole :as cp]
            [steph-scrape.fetch :refer [fetch-record]]
            [steph-scrape.export :refer [lazy-read-csv]]))

;; Constants
(def record-defaults
  {:fetched nil 
   :last-fetched-at nil})

(defmacro swallow-exceptions [& body]
    `(try ~@body (catch Exception e#)))

;; DB Core Functions
(defn view-records [doc-name views]
  {:_id (str "_design/" doc-name) :language :javascript :views (into {} views)})

(defn init-record [row]
  (-> (zipmap [:sid :url :timestamp] row) (merge record-defaults) (update-in [:sid] #(Integer/parseInt %))))

(defn get-views [] 
  (let [views (map #(.toString %) (filter #(.. % (toString) (endsWith ".js")) 
                      (file-seq (clojure.java.io/file "resources/views/" ))))]
    (for [view views]
      (let [name (-> view (clojure.string/split #"/") last)]
        {(keyword (subs name 0 (- (count name) 3))) {:map (slurp view)}}))))

;; Side-effecting DB Functions
(defn create-views [db]
  (c/put-document db (view-records "filtered" (get-views))))

(defn get-db [db-name]
  (c/get-database db-name))

(defn init-db [db-name]
  (do (c/delete-database db-name)
    (let [db (c/get-database db-name)] 
      (create-views db)
      db)))

(defn preload-db 
  ([db csv-path] (preload-db db csv-path 1000))
  ([db csv-path batch-size]
   (let [cnt (atom 0)] 
     (loop [lst (lazy-read-csv csv-path)]
       (if (seq lst)
         (let [[batch rest] (split-at batch-size lst)]
           (do 
             (c/bulk-update db (doall (map init-record batch)))
             (-> (swap! cnt (partial + 1000)) (info "records preloaded from CSV thus far"))
             (recur rest)))
         true)))))

;; Fetch and Update

(defn update-extracted-text [rec]
  (merge rec {:parsed-text 
              (get-text 
                (if-let [txt (:raw-html rec)] 
                  txt 
                  ""))}))

(defn retrieve-unfetched-records [db]
  (let [pool-size 100
        unfetched (c/get-view db "filtered" "unfetched")
        net-pool (cp/threadpool pool-size)
        cnt (atom 0)]
    (info "Fetching and updating records from:" @cnt)
    (doseq [recs (partition-all pool-size unfetched)]
      (c/bulk-update db 
                     (doall 
                       (do
                         (info "Processing head of new partition")
                         (cp/upmap net-pool #(fetch-record (:value %)) recs))))))
  (info "Finished Run"))
