(ns manul-backend.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [clojure.string :as s]))
(use 'korma.db)
(use 'korma.core)

(defdb heroku {:classname "org.postgresql.Driver"
               :subprotocol "postgresql"
               :subname (str
                         "//"
                         (System/getenv "GIGS_HOST")
                         "/"
                         (System/getenv "GIGS_DATABASE")
                         "?sslmode=require")
               :user (System/getenv "GIGS_USER")
               :password (System/getenv "GIGS_PASSWORD")})

(defentity view_songs_per_date)

(defn format-row
  "Takes [2 0 0 0 2 10 3] to 2---2a3)"
  [row]
  (->> row
   (map
    (fn [n] (if (= 0 n) "-" (Integer/toString n 16))))
   (s/join)))
 
  

(defn songs
  "Dump it out"
  [& args]
  (let [rows (select view_songs_per_date (fields :count))]
   (->> rows
        (map (comp :count))
        (into (vec (repeat 6 0)))
        (partition 7)
        (map vec)
        (map format-row)
        (into ["<pre>MTWTFSS"])
        (s/join "<br />"))))
        

(defroutes app-routes
  (GET "/" [] songs)
  (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes site-defaults))
