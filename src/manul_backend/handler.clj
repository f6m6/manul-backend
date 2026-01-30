(ns manul-backend.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [clojure.string :as s]
            [clojure.data.json :as json]
            [clj-time.core :as time]
            [clj-time.format :as f]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.util.response :as resp]))

(use 'korma.db)
(use 'korma.core)

(def gigs-sslmode (or (System/getenv "GIGS_SSLMODE") "disable"))

(defn json-write
  [data]
  (json/write-str
   data
   :value-fn (fn [_ v]
               (cond
                 (instance? java.sql.Date v) (str v)
                 (instance? java.sql.Timestamp v) (str v)
                 (instance? java.util.Date v) (str v)
                 :else v))))

(defn json-response
  [data]
  (-> (resp/response (json-write data))
      (resp/content-type "application/json; charset=utf-8")))

(defn json-read
  [request]
  (json/read-str (slurp (:body request)) :key-fn keyword))

(defdb heroku {:classname   "org.postgresql.Driver"
               :subprotocol "postgresql"
               :subname     (str
                             "//"
                             (System/getenv "GIGS_HOST")
                             "/"
                             (System/getenv "GIGS_DATABASE")
                             "?sslmode="
                             gigs-sslmode)
               :user        (System/getenv "GIGS_USER")
               :password    (System/getenv "GIGS_PASSWORD")})

(defentity venues)
(defentity song_performances)
(defentity performances)
(defentity sessions)
(defentity view_song_lengths_by_date)
(defentity view_next_songs_to_play)
(defentity session_types)
(defentity songs)
(defentity view_song_plays)
(defentity view_songs_per_date)
(defentity next_song)
(defentity song_performance_dates)

(defn all-songs
  "List all songs in the system"
  []
  (->> (select songs)
       (map (fn [row] (clojure.core/update (clojure.core/update row :id str) :length str)))
       vec
       json-response))

(defn next-active-songs
  "Return a JSON array with active songs, play count and time since last play"
  []
  (->> (select next_song (fields :song_id :count :last_played))
       (map (fn [row] (clojure.core/update row :last_played str)))
       vec
       json-response))

(defn all-performances
  "List all performances"
  []
  (->> (select performances)
       vec
       json-response))

(defn all-venues
  "List all venues"
  []
  (->> (select venues (fields :venuename :postcode))
       vec
       json-response))

(defn view-song-plays
  "Return song play counts (from view)"
  []
  (->> (select view_song_plays)
       vec
       json-response))

(defn view-song-plays-frequencies
  "Return play frequencies per date (from view)"
  []
  (->> (select view_songs_per_date)
       vec
       json-response))

(defn song-performance-dates
  "Return song performance dates"
  []
  (->> (select song_performance_dates)
       vec
       json-response))

(defn visualiser
  "Return a simple text visualiser"
  []
  (->> (select view_songs_per_date (fields :count))
       (map (comp :count))
       (into (vec (repeat 6 0)))
       (partition 7 7 [0 0 0 0 0 0 0])
       (map vec)
       (map (fn [row]
              (let [n (apply + row)]
                (->> row
                     (map (fn [x] (if (= 0 x) "-" (Integer/toString x 16))))
                     ((fn [x] (concat (vec x) [" " (s/join (repeat n "•")) " " (if (pos? n) n)])))
                     (s/join)))))
       (into ["<pre>MTWTFSS"])
       (s/join "<br />")))

(defn root
  "Return a combined visualiser + next songs"
  []
  (str (visualiser) "<br /><br />" (next-active-songs)))

(defn next-songs-to-play
  "Return a JSON array with songs, play count and time since last play"
  []
  (->> (select view_next_songs_to_play)
       (map (fn [row] (clojure.core/update row :last_played str)))
       vec
       json-response))

(defn create-performance
  "Create a performance and its song_performances rows"
  [request]
  (let [{:keys [venue songs date free openmic]} (json-read request)
        performance-date (or date (str (time/today)))
        free-val (if (some? free) free true)
        openmic-val (if (some? openmic) openmic true)]
    (if (or (nil? venue) (empty? venue) (not (seq songs)))
      (-> (json-response {:error "venue and songs are required"})
          (resp/status 400))
      (let [performance-id (insert performances
                                   (values {:performancedate performance-date
                                            :venue           venue
                                            :free            free-val
                                            :openmic         openmic-val}))]
        (doseq [[idx song] (map-indexed vector songs)]
          (insert song_performances
                  (values {:song_id         song
                           :performance_id  performance-id
                           :setlistposition (inc idx)})))
        (json-response {:performanceId performance-id
                        :songs          (count songs)})))))

(defn last-gig-date
  "Return a JSON { lastGigDate } with date of last gig"
  []
  (->> (select performances)
       (map :performancedate)
       sort
       last
       str
       (assoc {} :lastGigDate)
       json-response))

(defn stringify
  [date]
  (first (s/split (str date) #"\.")))

;; Getting seq of { :date, :count = minutes } maps out of sessions
(defn to-array [session] (vals (select-keys session [:start :end])))
(defn parse [postgres-date] (f/parse (f/formatters :mysql) (stringify postgres-date)))
(defn diff-dates [dates] (time/interval (first dates) (last dates)))
(defn seconds [session] (time/in-seconds (diff-dates (map parse (to-array session)))))
(defn date [session] (f/unparse (f/formatters :date) (parse (:start session))))
(defn date-and-seconds [session] {:date    (date session)
                                  :seconds (seconds session)})
(defn sessions-as-date-and-seconds [] (map date-and-seconds (select sessions)))

;; Getting seq of { :date, :count } maps out of performances
(defn seconds-song-perf [{:keys [length]}]  (apply + [(.getSeconds length) (* (.getMinutes length) 60) (* (.getHours length) 3600)]))
(defn date-and-seconds-song-perf [sp] {:date    (str (:performancedate sp))
                                       :seconds (seconds-song-perf sp)})
(defn song-perfs-as-date-and-seconds [] (map date-and-seconds-song-perf (select view_song_lengths_by_date)))

(defn dates-and-seconds-sessions-and-perfs [] (concat (sessions-as-date-and-seconds) (song-perfs-as-date-and-seconds)))

(defn add-seconds [out {:keys [date seconds]}] (update out date + seconds))

(defn merge-d2s-with-das [d2s row1] (clojure.core/update d2s (:date row1) (fn [x y] (+ (if (some? x) x 0) y)) (:seconds row1)))
(defn to-d2s [dass] (reduce merge-d2s-with-das {} dass))
(defn to-date-and-seconds [d2s] (map (fn [[date seconds]] {:date    date
                                                           :seconds seconds}) d2s))
(defn all-dates-and-seconds [] (vec (to-date-and-seconds (to-d2s (dates-and-seconds-sessions-and-perfs)))))
(defn max-seconds [] (:seconds (apply max-key :seconds (all-dates-and-seconds))))
(defn to-count [seconds] (Math/ceil (* 4 (/ seconds (max-seconds)))))
(defn all-dates-and-seconds-normalised [] (sort-by :date (map (fn [{:keys [date seconds]}] {:date  date
                                                                                            :count (to-count seconds)}) (all-dates-and-seconds))))

(defn date-three-months-ago [] (time/minus (time/today) (time/months 3)))

(defroutes app-routes
  (GET "/" [] (root))
  (GET "/all-songs" [] (all-songs))
  (GET "/next-songs-to-play" [] (next-songs-to-play))
  (GET "/next-active-songs" [] (next-active-songs))
  (GET "/performances" [] (all-performances))
  (GET "/venues" [] (all-venues))
  (GET "/view-song-plays" [] (view-song-plays))
  (GET "/view-song-plays-frequencies" [] (view-song-plays-frequencies))
  (GET "/song-performance-dates" [] (song-performance-dates))
  (GET "/visualiser" [] (visualiser))
  (GET "/last-gig-date" [] (last-gig-date))
  (GET "/normalised-count-per-day" [] (json-response (all-dates-and-seconds-normalised)))
  (POST "/create-performance" request (create-performance request))
  (GET "/session-types" [] (json-response (vec (map (fn [row] {:id   (str (:id row))
                                                               :name (:name row)})  (select session_types)))))
  (route/not-found "Not Found"))

(def app
  (wrap-cors
   (wrap-defaults app-routes  (assoc-in site-defaults [:security :anti-forgery] false))
   :access-control-allow-origin [#"http://localhost:3449"
                                 #"http://localhost:3000"
                                 #"http://localhost:3001"
                                 #"http://192.168.178.20:3001"
                                 #"http://192.168.0.6:3000"
                                 #"http://pallas.herokuapp.com"
                                 #"https://pallas.herokuapp.com"]
   :access-control-allow-methods [:get :put :post :delete]
   :access-control-allow-credentials "true"))
