(ns manul-backend.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [clojure.string :as s]
            [ring.middleware.cors :refer [wrap-cors]]))

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
(defentity next_song)
(defentity venues)

(defn format-day
  "Takes 0 to -, takes 10 to a"
  [n]
  (if (= 0 n) "-" (Integer/toString n 16)))

(defn bar
  [n]
  (s/join (repeat n "•")))

(defn format-row
  "Takes [2 0 0 0 2 10 3] to 2---2a3)"
  [row]
  (let [n (apply + row)]
   (->> row
        (map format-day)
        ((fn [x] (concat (vec x) [" " (bar n) " " (if (pos? n) n)])))
        (s/join))))

(defn next-active-songs-html
  "Dump it out"
  [& args]
  (let [rows (select next_song)]
   (->> rows
        (map
         (fn
           [row]
           (let [{:keys [song_id count last_played ]} row]
            (str (format "%-20s" song_id) (format "%-9s" count) last_played " ago\n"))))
        (into ["<pre>A c t i v e    S o n g s\n\nNAME                PLAYS    LAST PLAYED\n"])
        (apply str))))

(defn visualiser
  "Dump it out"
  [& args]
  (let [rows (select view_songs_per_date (fields :count))]
    (->> rows
         (map (comp :count))
         (into (vec (repeat 6 0)))
         (partition 7 7 [0 0 0 0 0 0 0])
         (map vec)
         (map format-row)
         (into ["<pre>MTWTFSS"])
         (s/join "<br />"))))

(defn root
  "Dump it out"
  [& args]
  (str
   (doall (next-active-songs-html))
   (visualiser)))
   
(defn songs-per-date-edn
  "Dump it out"
  [& args]
  (select view_songs_per_date))

(defn next-active-songs-edn
  "Dump it out"
  [& args]
  (str (vec (select next_song (fields :song_id :count)))))

(defn venues-edn
  "Dump it out"
  [& args]
  (str (vec (select venues (fields :venuename :postcode)))))

(defroutes app-routes
  (GET "/" [] root)
  (GET "/visualiser" [] visualiser)
  (GET "/plays" [] songs-per-date-edn)
  (GET "/next-active-songs" [] next-active-songs-edn)
  (GET "/venues" [] (venues-edn))
  (route/not-found "Not Found"))

(def app
  (wrap-cors
   (wrap-defaults app-routes site-defaults)
   :access-control-allow-origin [#"http://localhost:3449"
                                 #"http://manul-frontend.herokuapp.com"]
   :access-control-allow-methods [:get :put :post :delete]
   :access-control-allow-credentials "true"))
 
