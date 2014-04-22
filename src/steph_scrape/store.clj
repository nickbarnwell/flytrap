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
  {:fetched false 
   :raw-html nil 
   :parsed-text nil
   :last-fetched-at nil
   :last-fetched-resp nil
   })

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
(defn update-rec-from-web [rec] 
  (let [res (fetch-record rec) 
        rec-update (merge rec
                          {:raw-html (:body res)
                           :last-fetched-at (java.util.Date.)
                           :last-fetched-resp (:status res)
                           :fetch-payload res
                           })]
    (merge rec-update {:fetched true})))

(defn update-extracted-text [rec]
  (merge rec {:parsed-text 
              (get-text 
                (if-let [txt (:raw-html rec)] 
                  txt 
                  ""))}))

(defn retrieve-unfetched-records [db]
  (let [unfetched (c/get-view db "filtered" "unfetched")
        net-pool (cp/threadpool 100)
        cnt (atom 0)]
    (info "Fetching and updating records from:" @cnt)
    (doseq [recs (partition-all 25 unfetched)]
        (do
          (c/bulk-update db (doall (cp/upmap net-pool #(update-rec-from-web (:value %)) recs)))
          (-> (swap! cnt inc) (partial * 25) (info "records archived")))))
  (info "Finished Run"))
