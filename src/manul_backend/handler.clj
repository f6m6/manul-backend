(ns manul-backend.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [clojure.string :as s]
            [clojure.data.json :as json]
            [clj-time.core :as time]
            [clj-time.format :as f]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.util.response :as resp]
            [clojure.java.jdbc :as jdbc]))

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

(defn normalize-id
  [insert-result]
  (cond
    (instance? java.sql.ResultSet insert-result) (let [row (first (jdbc/result-set-seq insert-result))]
                                                  (or (:id row) (-> row vals first)))
    (map? insert-result) (or (:id insert-result) (-> insert-result vals first))
    (sequential? insert-result) (let [first-val (first insert-result)]
                                  (if (map? first-val)
                                    (or (:id first-val) (-> first-val vals first))
                                    first-val))
    :else insert-result))

(defn with-transaction
  [f]
  (transaction (f)))

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
(defentity albums)
(defentity album_songs)

(def gig-types #{"practice" "open_mic" "busking" "booked" "gig"})

(defn normalize-gig-type
  [gig-type]
  (when (string? gig-type)
    (let [trimmed (s/trim gig-type)]
      (when (not (s/blank? trimmed)) trimmed))))

(defn gig-type-from-flags
  [free openmic]
  (cond
    (true? openmic) "open_mic"
    (true? free) "busking"
    :else "booked"))

(defn gig-type->flags
  [gig-type]
  (case gig-type
    "open_mic" {:free true :openmic true}
    "busking" {:free true :openmic false}
    "booked" {:free false :openmic false}
    "gig" {:free false :openmic false}
    "practice" {:free true :openmic false}
    {:free true :openmic true}))

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

(defn performances-with-setlists
  "List performances with nested setlists"
  []
  (let [rows (exec-raw
              ["select p.id, p.performancedate, p.venue, p.free, p.openmic, p.gig_type,
                       sp.setlistposition, sp.song_id
                from performances p
                left join song_performances sp on sp.performance_id = p.id
                order by p.performancedate desc, p.id desc, sp.setlistposition asc"]
              :results)
        grouped (->> rows
                     (group-by :id)
                     (map (fn [[id items]]
                            (let [base (first items)
                                  setlist (->> items
                                               (filter :song_id)
                                               (map (fn [row]
                                                      {:position (:setlistposition row)
                                                       :song_id (:song_id row)}))
                                               vec)]
                              {:id id
                               :performancedate (str (:performancedate base))
                               :venue (:venue base)
                               :free (:free base)
                               :openmic (:openmic base)
                               :gig_type (name (:gig_type base))
                               :setlist setlist})))
                     (sort-by :performancedate #(compare %2 %1))
                     vec)]
    (json-response grouped)))

(defn all-venues
  "List all venues"
  []
  (->> (select venues (fields :venuename :postcode))
       vec
       json-response))

(defn all-albums
  "List all albums"
  []
  (->> (select albums (fields :id :title :artist :release_date))
       (map (fn [row]
              (cond-> row
                (:release_date row) (clojure.core/update :release_date str))))
       vec
       json-response))

(defn album-tracks
  "Return tracks for an album with ordering"
  [album-id]
  (let [rows (exec-raw

(defn update-venue
  "Update venue postcode"
  [venuename request]
  (let [{:keys [postcode]} (json-read request)
        name (when venuename (s/trim venuename))]
    (if (s/blank? name)
      (-> (json-response {:error "venuename is required"})
          (resp/status 400))
      (let [rows (exec-raw
                  ["update venues set postcode = ? where venuename = ? returning venuename, postcode"
                   [postcode name]]
                  :results)
            row (first rows)]
        (if (nil? row)
          (-> (json-response {:error "venue not found"})
              (resp/status 404))
          (json-response {:venuename (:venuename row)
                          :postcode (:postcode row)}))))))

(defn delete-venue
  "Delete a venue"
  [venuename]
  (let [name (when venuename (s/trim venuename))]
    (if (s/blank? name)
      (-> (json-response {:error "venuename is required"})
          (resp/status 400))
      (let [rows (exec-raw
                  ["delete from venues where venuename = ? returning venuename"
                   [name]]
                  :results)
            row (first rows)]
        (if (nil? row)
          (-> (json-response {:error "venue not found"})
              (resp/status 404))
          (json-response {:venuename (:venuename row)}))))))

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
  (let [{:keys [venue songs date free openmic gig_type]} (json-read request)
        trimmed-venue (when venue (s/trim venue))
        songs-list (if (vector? songs) songs [])
        performance-date (if (and date (not (s/blank? date))) date (str (time/today)))
        gig-type-in (normalize-gig-type gig_type)
        gig-type-val (cond
                       (and gig-type-in (gig-types gig-type-in)) gig-type-in
                       gig-type-in nil
                       :else (gig-type-from-flags free openmic))
        flags (gig-type->flags gig-type-val)
        free-val (if gig-type-in (:free flags) (if (some? free) free true))
        openmic-val (if gig-type-in (:openmic flags) (if (some? openmic) openmic true))]
    (if (or (s/blank? trimmed-venue)
            (not (seq songs-list))
            (not (every? (fn [song] (and (string? song) (not (s/blank? song)))) songs-list)))
      (-> (json-response {:error "venue and songs are required"})
          (resp/status 400))
      (if (nil? gig-type-val)
        (-> (json-response {:error "gig_type is invalid"})
            (resp/status 400))
        (with-transaction
         (fn []
           (let [rows (exec-raw
                       ["insert into performances (performancedate, venue, free, openmic, gig_type) values (?, ?, ?, ?, ?) returning id"
                        [(java.sql.Date/valueOf performance-date) trimmed-venue free-val openmic-val gig-type-val]]
                       :results)
                 performance-id (normalize-id rows)]
             (doseq [[idx song] (map-indexed vector songs-list)]
               (exec-raw
                ["insert into song_performances (song_id, performance_id, setlistposition) values (?, ?, ?)"
                 [song performance-id (inc idx)]]))
             (json-response {:performanceId performance-id
                             :songs          (count songs-list)}))))))))

(defn update-performance
  "Update performance fields"
  [id request]
  (let [{:keys [venue date free openmic gig_type]} (json-read request)
        trimmed-venue (when venue (s/trim venue))
        performance-date (if (and date (not (s/blank? date))) date nil)
        gig-type-in (normalize-gig-type gig_type)
        gig-type-val (cond
                       (and gig-type-in (gig-types gig-type-in)) gig-type-in
                       gig-type-in nil
                       :else (gig-type-from-flags free openmic))
        flags (gig-type->flags gig-type-val)
        free-val (if gig-type-in (:free flags) (if (some? free) free true))
        openmic-val (if gig-type-in (:openmic flags) (if (some? openmic) openmic true))]
    (if (or (s/blank? trimmed-venue) (s/blank? performance-date))
      (-> (json-response {:error "venue and date are required"})
          (resp/status 400))
      (if (nil? gig-type-val)
        (-> (json-response {:error "gig_type is invalid"})
            (resp/status 400))
        (let [rows (exec-raw
                    ["update performances set performancedate = ?, venue = ?, free = ?, openmic = ?, gig_type = ? where id = ? returning id, performancedate, venue, free, openmic, gig_type"
                     [(java.sql.Date/valueOf performance-date) trimmed-venue free-val openmic-val gig-type-val (Integer/parseInt id)]]
                    :results)
              row (first rows)]
          (if (nil? row)
            (-> (json-response {:error "performance not found"})
                (resp/status 404))
            (json-response {:id (:id row)
                            :performancedate (str (:performancedate row))
                            :venue (:venue row)
                            :free (:free row)
                            :openmic (:openmic row)
                            :gig_type (name (:gig_type row))})))))))

(defn replace-performance-setlist
  "Replace setlist for a performance"
  [id request]
  (let [{:keys [songs]} (json-read request)
        songs-list (if (vector? songs) songs [])]
    (if (or (not (seq songs-list))
            (not (every? (fn [song] (and (string? song) (not (s/blank? song)))) songs-list)))
      (-> (json-response {:error "songs must be a non-empty list of titles"})
          (resp/status 400))
      (with-transaction
       (fn []
         (exec-raw
          ["delete from song_performances where performance_id = ?"
           [(Integer/parseInt id)]])
         (doseq [[idx song] (map-indexed vector songs-list)]
           (exec-raw
            ["insert into song_performances (song_id, performance_id, setlistposition) values (?, ?, ?)"
             [song (Integer/parseInt id) (inc idx)]]))
         (json-response {:performanceId (Integer/parseInt id)
                         :songs (count songs-list)}))))))

(defn delete-performance
  "Delete a performance and its setlist"
  [id]
  (with-transaction
   (fn []
     (exec-raw
      ["delete from song_performances where performance_id = ?"
       [(Integer/parseInt id)]])
     (let [rows (exec-raw
                 ["delete from performances where id = ? returning id"
                  [(Integer/parseInt id)]]
                 :results)
           row (first rows)]
       (if (nil? row)
         (-> (json-response {:error "performance not found"})
             (resp/status 404))
         (json-response {:id (:id row)}))))))

(defn create-venue
  "Create a venue"
  [request]
  (let [{:keys [venuename postcode]} (json-read request)]
    (let [name (when venuename (s/trim venuename))]
      (if (s/blank? name)
        (-> (json-response {:error "venuename is required"})
            (resp/status 400))
        (do
          (exec-raw
           ["insert into venues (venuename, postcode) values (?, ?)"
            [name postcode]])
          (json-response {:venuename name}))))))

(defn create-song
  "Create a song"
  [request]
  (let [{:keys [title cover active key length instrumental]} (json-read request)
        active-val (if (some? active) active true)
        cover-val (if (some? cover) cover false)
        instrumental-val (if (some? instrumental) instrumental false)]
    (let [song-title (when title (s/trim title))]
      (if (s/blank? song-title)
        (-> (json-response {:error "title is required"})
            (resp/status 400))
        (do
          (exec-raw
           ["insert into songs (title, cover, active, key, length, instrumental) values (?, ?, ?, ?, ?, ?)"
            [song-title cover-val active-val key length instrumental-val]])
          (json-response {:title song-title}))))))

(defn update-song
  "Update song fields"
  [title request]
  (let [{:keys [cover active key length instrumental]} (json-read request)
        song-title (when title (s/trim title))
        active-val (if (some? active) active true)
        cover-val (if (some? cover) cover false)
        instrumental-val (if (some? instrumental) instrumental false)
        length-val (if (and length (s/blank? (str length))) nil length)]
    (if (s/blank? song-title)
      (-> (json-response {:error "title is required"})
          (resp/status 400))
      (let [rows (exec-raw
                  ["update songs set cover = ?, active = ?, key = ?, length = ?, instrumental = ? where title = ? returning title, cover, active, key, length, instrumental"
                   [cover-val active-val key length-val instrumental-val song-title]]
                  :results)
            row (first rows)]
        (if (nil? row)
          (-> (json-response {:error "song not found"})
              (resp/status 404))
          (json-response {:title (:title row)
                          :cover (:cover row)
                          :active (:active row)
                          :key (:key row)
                          :length (when-let [l (:length row)] (str l))
                          :instrumental (:instrumental row)}))))))

(defn delete-song
  "Delete a song"
  [title]
  (let [song-title (when title (s/trim title))]
    (if (s/blank? song-title)
      (-> (json-response {:error "title is required"})
          (resp/status 400))
      (let [rows (exec-raw
                  ["delete from songs where title = ? returning title"
                   [song-title]]
                  :results)
            row (first rows)]
        (if (nil? row)
          (-> (json-response {:error "song not found"})
              (resp/status 404))
          (json-response {:title (:title row)}))))))

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

(declare update-venue delete-venue
         update-song delete-song
         update-performance replace-performance-setlist delete-performance)

(defroutes app-routes
  (GET "/" [] (root))
  (GET "/all-songs" [] (all-songs))
  (GET "/next-songs-to-play" [] (next-songs-to-play))
  (GET "/next-active-songs" [] (next-active-songs))
  (GET "/performances" [] (all-performances))
  (GET "/performances-with-setlists" [] (performances-with-setlists))
  (POST "/performances" request (create-performance request))
  (PUT "/performances/:id" [id :as request] (update-performance id request))
  (PUT "/performances/:id/setlist" [id :as request] (replace-performance-setlist id request))
  (DELETE "/performances/:id" [id] (delete-performance id))
  (GET "/albums" [] (all-albums))
  (GET "/albums/:id/tracks" [id] (album-tracks id))
  (GET "/venues" [] (all-venues))
  (PUT "/venues/:venuename" [venuename :as request] (update-venue venuename request))
  (DELETE "/venues/:venuename" [venuename] (delete-venue venuename))
  (GET "/view-song-plays" [] (view-song-plays))
  (GET "/view-song-plays-frequencies" [] (view-song-plays-frequencies))
  (GET "/song-performance-dates" [] (song-performance-dates))
  (GET "/visualiser" [] (visualiser))
  (GET "/last-gig-date" [] (last-gig-date))
  (GET "/normalised-count-per-day" [] (json-response (all-dates-and-seconds-normalised)))
  (POST "/create-performance" request (create-performance request))
  (POST "/create-venue" request (create-venue request))
  (POST "/create-song" request (create-song request))
  (PUT "/songs/:title" [title :as request] (update-song title request))
  (DELETE "/songs/:title" [title] (delete-song title))
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
