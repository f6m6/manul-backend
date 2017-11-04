(ns manul-backend.handler
  (:import (java.util UUID))
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [clojure.string :as s]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.params :refer [wrap-params]]))

(use 'korma.db)
(use 'korma.core)

;; (defdb heroku {:classname "org.postgresql.Driver"
;;                :subprotocol "postgresql"
;;                :subname (str
;;                          "//"
;;                          (System/getenv "GIGS_HOST")
;;                          "/"
;;                          (System/getenv "GIGS_DATABASE")
;;                          "?sslmode=require")
;;                :user (System/getenv "GIGS_USER")
;;                :password (System/getenv "GIGS_PASSWORD")})

(defdb local {:classname "org.postgresql.Driver"
               :subprotocol "postgresql"
               :subname (str
                         "//"
                         "localhost:5432"
                         "/"
                         "manul")
               :user "farhan"})

(defentity view_songs_per_date)
(defentity view_song_plays)
(defentity next_song)
(defentity venues)
(defentity song_performance_dates)
(defentity performances)
(defentity events)

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
            (str (format "%-20s" song_id) (format "%-9s" count) (str last_played " ago\n")))))
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

(defn select-all
  [entity]
  (str (vec (select entity))))
                    
(defn songs-per-date-edn
  "Dump it out"
  [& args]
  (str (vec (select view_songs_per_date))))

(defn song-performance-dates-edn
  "Dump it out"
  [& args]
  (str (vec (select song_performance_dates))))

(defn next-active-songs-edn
  "Dump it out"
  [& args]
  (str (vec (select next_song (fields :song_id :count)))))

(defn view-song-plays-edn
  "Dump it out"
  [& args]
  (str (vec (select view_song_plays (fields :song_id :count)))))

(defn view-song-plays-frequencies
  "Returns a vector [ [plays, frequency] ... ] for  frequencies"
  [& args]
  (->> (select view_song_plays (fields :count))
       (map :count)
       frequencies
       vec
       str))

(defn venues-edn
  "Dump it out"
  [& args]
  (str (vec (select venues (fields :venuename :postcode)))))

(defn create-event
  "Creates an event from form parameters"
  [params]
  (prn params)
  (insert events
   (values (assoc params :id (UUID/randomUUID)))))

(defroutes app-routes
  (GET "/" [] root)
  (POST "/performance" x (str (:form-params x)))
  (POST "/event" request (create-event (:params request)))
  (GET "/visualiser" [] visualiser)
  (GET "/plays" [] songs-per-date-edn)
  (GET "/performances" [] (select-all performances))
  (GET "/song-performance-dates" [] song-performance-dates-edn)
  (GET "/next-active-songs" [] next-active-songs-edn)
  (GET "/view-song-plays" [] view-song-plays-edn)
  (GET "/view-song-plays-frequencies" [] view-song-plays-frequencies)
  (GET "/venues" [] (venues-edn))
  (route/not-found "Not Found"))

(def app
  (wrap-cors
   (wrap-params
    (wrap-defaults app-routes  (assoc-in site-defaults [:security :anti-forgery] false)))
   :access-control-allow-origin [#"http://localhost:3449"
                                 #"http://manul-frontend.herokuapp.com"]
   :access-control-allow-methods [:get :put :post :delete]
   :access-control-allow-credentials "true"))
 
