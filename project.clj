(defproject steph-scrape "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :jvm-opts ["-Xmx2g" "-server"]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [http-kit "2.1.16"] 
                 [org.clojure/data.json "0.2.4"]
                 [com.ashafa/clutch "0.4.0-RC1"]
                 ;;Logging & Instrumentation Libs
                 [log4j/log4j "1.2.17"]
                 [org.clojure/tools.logging "0.2.6"]
                 [clojure-csv/clojure-csv "2.0.1"]
                 [io.curtis/boilerpipe-clj "0.2.0"]])
