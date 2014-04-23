(ns steph-scrape.core-test
  (:import [java.util Date])
  (:require [clojure.test :refer :all]
            [steph-scrape.fetch :as fetch]))

(def proxy-record {:sid 4 
                   :url "http://feedproxy.google.com/~r/instapundit/main/~3/qoJMkHQl46U/" 
                   :timestamp "2009-01-01 05:10:25"})

(def proxy-record-2 {:sid 4 
                     :url "http://feedproxy.google.com/~r/instapundit/main/~3/3xw_bqI6Z0Y/"
                     :timestamp "2009-01-01 08:41:02" })

(deftest timestamp-conversion
         ("steph-scrape.fetch/parse-timestamp"
            (testing "record timestamp parsing"
                     (is (=
                           (fetch/parse-timestamp (:timestamp proxy-record))
                           "20090101051025")))
            (testing "Timestamp handles a nil"
                     (is (nil? (fetch/parse-timestamp nil))))))

;;"TODO: This should use a mock/recorded responsed
(deftest wayback-fetcher
  (testing "steph-scrape.fetch/wbm-snapshot"
           ()
           ))
