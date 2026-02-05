(defproject manul-backend "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.2"]
                 [compojure "1.5.1"]
                 [ring/ring-defaults "0.3.2"]
                 [korma "0.4.0"]
                 [org.postgresql/postgresql "42.7.3"]
                 [ring-cors "0.1.13"]
                 [org.clojure/data.json "0.2.6"]
                 [clj-time "0.14.2"]
                 [ring-logger "1.0.1"]]

  :plugins [[lein-ring "0.12.5"]
            [lein-cloverage "1.2.4"]]
  :ring {:handler manul-backend.handler/app
         :host "0.0.0.0"
         :port 3000
         :auto-reload? true
         :auto-refresh? true
         :reload-paths ["src"]}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.0"]]}})
