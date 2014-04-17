(ns steph-scrape.fetch
  (:use [clojure.tools.logging :only (info error debug)])
  (:require [org.httpkit.client :as http]
            [clojure.data.json :as json]))

(def default-http-params 
  {
  
   })

(defn- fetcher [url opts params callback]
  (let [params (merge default-http-params opts {:query-params params})]
  (http/get url 
            params 
            callback)))

(defn- resp-map [{:keys [status body error]}]
  (if error
    (do
      (error "Response was:" status error)
      {:status nil :body nil :error true})
    {:status status :body body :error false}))

(defn- fetch-wbm-snapshot
  ;;CHange date once scraping other than week25
  [url & {:keys [date] :or {date "200906"}}]
  (let [availability-url "http://archive.org/wayback/available"
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

(defn- fetch-archive-org [url]
  (future (if-let [url (get @(fetch-wbm-snapshot url) "url")]
    @(fetcher url
             {:as :text} {}
             resp-map)
    nil)))

(defn- fetch-live-site [url]
  (fetcher url {:as :text} {}
           resp-map))

(defn fetch-record [rec]
  (future (let [url (:url rec)]
            (let [res (if-let [archived @(fetch-archive-org url)]
                        archived
                        @(fetch-live-site url))]
              (debug res)
              res))))
