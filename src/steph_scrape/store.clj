(ns steph-scrape.store
  (:use [clojure.tools.logging :only (info error debug)])
  (:require [com.ashafa.clutch :as c]
            [clojure-csv.core :as csv]
            [boilerpipe-clj.core :refer [get-text]]
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
  (-> (zipmap [:sid :url] row) (merge record-defaults) (update-in [:sid] #(Integer/parseInt %))))

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
   (loop [lst (lazy-read-csv csv-path)]
       (if (seq lst)
         (let [[batch rest] (split-at batch-size lst)]
           (do 
             (c/bulk-update db (doall (map init-record batch)))
             (recur rest)))
         true))))

;; Fetch and Update
(defn update-record-from-web [rec] 
  (let 
    [res @(fetch-record rec) 
     rec-update (merge rec
                       {:raw-html (:body res)
                        :parsed-text (get-text (if-let [txt (:body res)] txt ""))
                        :last-fetched-at (java.util.Date.)
                        :last-fetched-resp (:status res)})]
    (merge rec-update {:fetched true})))

(def db (c/get-database "steph-scrape-top40"))

(defn update-all-records [db]
  (let [unfetched (c/get-view db "filtered" "unfetched")
        cnt (atom 0)]
    (doseq [recs (partition-all 25 unfetched)]
        (do
          (info @cnt (first recs))
          (c/bulk-update db 
                       (doall (pmap #(update-record-from-web (:value %)) recs)))
          (swap! cnt inc)
          )))
  (info "Finished Run"))
