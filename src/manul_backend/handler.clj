(ns manul-backend.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [clojure.string :as s]
            [clojure.data.json :as json]
            [clj-time.core :as time]
            [clj-time.format :as f]
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
(defentity sessions)
(defentity view_song_lengths_by_date)
(defentity view_next_songs_to_play)

(defn select-all
  "A generic function to SELECT ALL of an entity a.k.a. relation in the database"
  [entity]
  (vec (select entity)))

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
       (map :performancedate)
       sort
       last
       str
       (assoc {} :lastGigDate)
       json/write-str))

(defn stringify
  [date]
  (first (s/split (str date) #"\.")))

;; Getting seq of { :date, :count = minutes } maps out of sessions
(defn to-array [session] (vals (select-keys session [:start :end])))
(defn parse [postgres-date] (f/parse (f/formatters :mysql) (stringify postgres-date)))
(defn diff-dates [dates] (time/interval (first dates) (last dates)))
(defn seconds [session] (time/in-seconds (diff-dates (map parse (to-array session)))))
(defn date [session] (f/unparse (f/formatters :date) (parse (:start session))))
(defn date-and-seconds [session] { :date (date session), :seconds (seconds session)})
(defn sessions-as-date-and-seconds [] (map date-and-seconds (select sessions)))

;; Getting seq of { :date, :count } maps out of performances
(defn seconds-song-perf [{ :keys [length]}]  (apply + [(.getSeconds length) (* (.getMinutes length) 60) (* (.getHours length) 3600)]))
(defn date-and-seconds-song-perf [sp] { :date (str (:performancedate sp)), :seconds (seconds-song-perf sp)})
(defn song-perfs-as-date-and-seconds [] (map date-and-seconds-song-perf (select view_song_lengths_by_date)))

(defn dates-and-seconds-sessions-and-perfs [] (concat (sessions-as-date-and-seconds) (song-perfs-as-date-and-seconds)))

(defn add-seconds [out {:keys [date seconds]}] (update out date + seconds))  

(defn merge-d2s-with-das [d2s row1] (clojure.core/update d2s (:date row1) (fn [x y] (+ (if (some? x) x 0) y)) (:seconds row1)))
(defn to-d2s [dass] (reduce merge-d2s-with-das {} dass))
(defn to-date-and-seconds [d2s] (map (fn [[date seconds]] {:date date :seconds seconds}) d2s))
(defn all-dates-and-seconds [] (vec (to-date-and-seconds (to-d2s (dates-and-seconds-sessions-and-perfs)))))
(defn max-seconds [] (:seconds (apply max-key :seconds (all-dates-and-seconds))))
(defn all-dates-and-seconds-normalised [] (sort-by :date (map (fn [{:keys [date seconds]}] {:date date :count (Math/round (* 4 (/ seconds (max-seconds))) ) }) (all-dates-and-seconds))))

;; TODO - that thing above can be switched back to dates-and-second-sessions... the thing iwth all on line 71
;; make more endpoints for the others
;; the DB view this relies on is wrong :(

(defn date-three-months-ago [] (time/minus (time/today) (time/months 3)))

;; (defn test [] (str (date-three-months-ago)))

(defroutes app-routes
  (GET "/next-songs-to-play" [] (next-songs-to-play))
  (GET "/last-gig-date" [] (last-gig-date))
  (GET "/normalised-count-per-day" [] (json/write-str (all-dates-and-seconds-normalised)))
  (route/not-found "Not Found"))

(def app
  (wrap-cors
   (wrap-defaults app-routes  (assoc-in site-defaults [:security :anti-forgery] false))
   :access-control-allow-origin [#"http://localhost:3449"
                                 #"http://localhost:3000"
                                 #"http://192.168.0.6:3000"
                                 #"http://manul-frontend.herokuapp.com"]
   :access-control-allow-methods [:get :put :post :delete]
   :access-control-allow-credentials "true"))
 
