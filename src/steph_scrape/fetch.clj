(ns steph-scrape.fetch
  (:import [java.net URI])
  (:use [clojure.tools.logging :only (info error debug)])
  (:require [org.httpkit.client :as http]
            [clojure.data.json :as json]))

(def default-http-params 
  {
   :timeout 5000  
   :max-redirects 10
  })

(defn- fetcher [url opts params callback]
  (let [params (merge default-http-params opts {:query-params params})]
    (debug "Fetching:" url  "Params:" params)
    (try 
      (deref (http/get url 
              params 
              callback))
      (catch Exception e
        (error e "for URL:" url)
        (error (.printStackTrace e))
        (callback nil nil nil e)))))

(defn- resp-map [{:keys [status body error]}]
  (if error
    (do
      (error "Response was:" status error)
      {:status nil :body nil :error true})
    {:status status :body body :error false}))

(defn- fetch-wbm-snapshot
  ;;Change date once scraping other than week25
  [url & {:keys [date] :or {date "200906"}}]
  (let [availability-url "http://archive.org/wayback/available"
        nested-item-path ["archived_snapshots" "closest"]
        get-item (fn [ss] (get-in ss nested-item-path))
        available? (fn [ss] (get (get-item ss) "available"))
        callback (fn [{:keys [status headers body error]}]
                   (debug "WBM Callback entered for" url)
                   (let [res (json/read-str body)]
                     (if (available? res)
                       (get-item res)
                       nil)))]

    (fetcher availability-url 
              {:as :text} 
              {:url url :date date}
              callback)))

(defn- fetch-archive-org [rec]
  (if-let [url (get (fetch-wbm-snapshot (:url rec)) "url")]
    (fetcher url
             {:as :text} {}
             resp-map)
    nil))

(defn- fetch-live-site [rec]
  (fetcher url {:as :text} {}
           resp-map))

(def special-case-hosts  
  {"feedproxy.google.com" 
   (fn [rec] 
     (let [o-url (:url rec)
           feedproxy-result @(http/head 
                               o-url
                               default-http-params)
           true-url (get-in feedproxy-result 
                            [:opts :url] 
                            nil)]
       (debug "Proxy URL:" o-url)
       (debug "True URL:" true-url)
       (if (not= true-url (:url rec))
         (merge rec {:proxied-url o-url :url true-url})
         rec)))})

(defn fetch-record [rec]
  (let [url (:url rec)
        host (.. (URI. url) getHost) ]
    (debug "URL:" url "Host:" host)
    (if-let [special-fn (get special-case-hosts host)]
      (-> rec special-fn fetch-record)
      (if-let [snapshot (fetch-archive-org url)]
                snapshot
                (fetch-live-site url)))))
