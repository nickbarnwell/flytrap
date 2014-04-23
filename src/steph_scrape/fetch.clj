(ns steph-scrape.fetch
  (:import [java.net URI]
           [java.text SimpleDateFormat])
  (:use [clojure.tools.logging :only (info error debug)])
  (:require [org.httpkit.client :as http]
            [clojure.data.json :as json]))

(def default-http-params 
  {:timeout 5000  
   :max-redirects 3})

(defn redirect-exception? [e]
  (let [msg (.getMessage e)]
    (if (re-matches #"too many redirects:.+" msg)
      true
      false)))

(defn host-for-url [url] (.. (URI. url) (getHost)))

(defn fetcher [url opts params callback]
  (let [params (merge default-http-params opts {:query-params params})]
    (comment (debug "Fetching:" url  "Params:" params))
    (deref (http/get url 
                       params 
                       callback))))

(defn resp-map [rec]
  (fn [res] 
    (if (:error res)
      (do
        (error "Error:" (:url rec) (:status res) (:error res))
        {:error true})
      res)))

(defn parse-timestamp [timestamp]
  (if timestamp
    (let [parser (SimpleDateFormat. "yyyy-MM-dd HH:mm:ss")
          parsed-ts (. parser (parse timestamp))
          formatter (SimpleDateFormat. "YYYYMMddHHmmss")]
      (. formatter (format parsed-ts)))
    nil))

(defn fetch-wbm-snapshot [rec]
  (let [url (:url rec)
        o-ts (:timestamp rec)
        date (if o-ts (parse-timestamp o-ts) nil)
        availability-url "http://archive.org/wayback/available"
        nested-item-path ["archived_snapshots" "closest"]
        get-item (fn [ss] (get-in ss nested-item-path))
        available? (fn [ss] (get (get-item ss) "available"))
        callback (fn [{:keys [status headers body error]}]
                   (let [res (json/read-str body)]
                     (if (available? res)
                       (get-item res)
                       nil)))]

    (fetcher availability-url 
              {:as :text} 
              {:url url :date date}
              callback)))

(defn fetch-archive-org [rec]
  (if-let [url (get (fetch-wbm-snapshot rec) "url")]
    (fetcher url
             {:as :text} {}
             (resp-map rec))
    nil))

(defn fetch-live-site [rec]
  (fetcher (:url rec) {:as :text} {}
           (resp-map rec))) 

(defn special-cased-site? [rec]
  (let [host (host-for-url (:url rec))
        cases {"feedproxy.google.com" 
               (fn [rec] 
                 (let [o-url (:url rec)
                       feedproxy-result (deref (http/head 
                                           o-url
                                           default-http-params))
                       true-url (get-in feedproxy-result 
                                        [:opts :url] 
                                        nil)]
                   (comment (debug "Feedproxy Resolver:" o-url "==>" true-url))
                   (if (not= true-url (:url rec))
                     (merge rec {:proxy-url o-url :url true-url})
                     (merge rec {:url nil :proxy-url o-url}))))}]
    (get cases host)))

(defn fetch-record [rec]
  (let [ret (try
    (if-let [special (special-cased-site? rec)]
    (-> rec special fetch-record)
    (let [resp (or (fetch-archive-org rec) 
                   (fetch-live-site rec))
          fetched (not (contains? resp :error) )]
      (merge rec {:fetch-resp (dissoc resp :error) :fetched fetched})))
    (catch Exception e
      (error "Exception" e "caught in fetch record")
      (assoc rec :fetched false)))]
    ret))
