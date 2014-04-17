(ns steph-scrape.export
  (:require [clojure-csv.core :as csv]
            [clojure.java.io :as io]
            [com.ashafa.clutch :as c]))

(defn lazy-read-csv [csv-file]
  (let [in-file (io/reader csv-file)
        csv-seq (csv/parse-csv in-file)
        lazy (fn lazy [wrapped]
               (lazy-seq
                 (if-let [s (seq wrapped)]
                   (cons (first s) (lazy (rest s)))
                   (.close in-file))))]
    (lazy csv-seq)))

(defn- map-to-vec [m ks]
  (into []
        (for [k ks]
          (get m k))))

(defn export-records [db]  
  (let [recs (c/get-view db "filtered" "top40-replaced")]
    (doseq [rec recs]
      (let [val (map-to-vec (:value rec) 
                            [:sid :url :text])
            csv-out-format (vector (map str val)) ]
        (spit "test.csv" (csv/write-csv csv-out-format :force-quote true ) :append true)))))
