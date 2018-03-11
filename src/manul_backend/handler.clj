(ns manul-backend.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [clojure.string :as s]
            [clojure.data.json :as json]
            [clj-time.core :as time]
            [ring.middleware.cors :refer [wrap-cors]]))

(use 'korma.db)
(use 'korma.core)

(defdb heroku {:classname "org.postgresql.Driver"
               :subprotocol "postgresql"
               :subname (str
                         "//"
                         "localhost"
                         "/"
                         "manul08032018")})
                         
(defentity venues)
(defentity song_performances)
(defentity performances)
(defentity view_event_counts_per_date)
(defentity view_next_songs_to_play)

(defn select-all
  "A generic function to SELECT ALL of an entity a.k.a. relation in the database"
  [entity]
  (vec (select entity)))

(defn song-performances-counts-per-date
  "Return a JSON array of number of songs played per date [{ date: '2016-01-01', count: 6 }]"
  []
  (->> (select view_event_counts_per_date)
       (map (fn [row] (str (:performancedate row))))
       frequencies
       (sort-by first)
       (map (fn [pair] {:date (first pair) :count (second pair)}))
       json/write-str))

(defn next-songs-to-play
  "Return a JSON array with songs, play count and time since last play"
  []
  (->> (select view_next_songs_to_play)
       (map (fn [row] (clojure.core/update row :last_played str)))
       vec
       json/write-str))

(defn last-gig-date
  "Return a JSON { lastGigDate } with date of last gig"
  []
  (->> (select performances)
       (sort-by :performancedate)
       last
       :performancedate
       str
       json/write-str))

(defroutes app-routes
  (GET "/song-performances-counts-per-date" [] (song-performances-counts-per-date))
  (GET "/next-songs-to-play" [] (next-songs-to-play))
  (GET "/last-gig-date" [] (last-gig-date))
  (route/not-found "Not Found"))

(def app
  (wrap-cors
   (wrap-defaults app-routes  (assoc-in site-defaults [:security :anti-forgery] false))
   :access-control-allow-origin [#"http://localhost:3449"
                                 #"http://localhost:3000"
                                 #"http://manul-frontend.herokuapp.com"]
   :access-control-allow-methods [:get :put :post :delete]
   :access-control-allow-credentials "true"))
 
