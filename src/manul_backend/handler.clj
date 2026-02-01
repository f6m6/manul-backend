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

(declare normalize-length-interval)

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
(defentity view_next_songs_to_perform_live)
(defentity view_next_songs_to_practise)
(defentity session_types)
(defentity songs)
(defentity view_song_plays)
(defentity view_songs_per_date)
(defentity next_song)
(defentity song_performance_dates)
(defentity albums)
(defentity album_songs)
(defentity practice_sessions)
(defentity practice_session_songs)
(defentity singing_lessons)
(defentity singing_lesson_songs)
(defentity view_song_last_performed_live)
(defentity view_song_perform_live_counts)
(defentity view_song_last_practiced)
(defentity view_song_practice_counts)

(def gig-types #{"open_mic" "booked" "busking" "showcase" "private_party"})

(defn normalize-gig-type
  [gig-type]
  (when (string? gig-type)
    (let [trimmed (s/trim gig-type)]
      (when (not (s/blank? trimmed)) trimmed))))

(defn normalize-fee-micro-gbp
  [fee-micro-gbp]
  (cond
    (integer? fee-micro-gbp) fee-micro-gbp
    (number? fee-micro-gbp) (long fee-micro-gbp)
    (string? fee-micro-gbp) (let [trimmed (s/trim fee-micro-gbp)]
                              (when (not (s/blank? trimmed))
                                (Long/parseLong trimmed)))
    :else nil))

(defn gig-type->flags
  [gig-type]
  (case gig-type
    "open_mic" {:free true :openmic true}
    "busking" {:free true :openmic false}
    "booked" {:free false :openmic false}
    "showcase" {:free false :openmic false}
    "private_party" {:free false :openmic false}
    {:free true :openmic true}))

(defn all-songs
  "List all songs in the system"
  []
  (let [rows (exec-raw
              ["select distinct on (s.title)\n                      s.*, a.id as album_id, a.title as album_title, asg.track_number\n               from songs s\n               left join album_songs asg on asg.song_title = s.title\n               left join albums a on a.id = asg.album_id\n               order by s.title, a.id"]
              :results)]
    (->> rows
         (map (fn [row]
                (-> row
                    (clojure.core/update :id str)
                    (clojure.core/update :length str))))
         vec
         json-response)))

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

(defn estimated-minutes
  "Compute estimated minutes for a list of rows that include :length."
  [rows]
  (let [minutes (->> rows
                     (map (fn [row]
                            (let [length (:length row)]
                              (cond
                                (instance? java.time.Duration length)
                                (/ (.toMillis length) 60000.0)
                                (instance? java.sql.Time length)
                                (/ (.getTime length) 60000.0)
                                (instance? java.sql.Timestamp length)
                                (/ (.getTime length) 60000.0)
                                (instance? java.util.Date length)
                                (/ (.getTime length) 60000.0)
                                (some? length)
                                (try
                                  (/ (.getTime length) 60000.0)
                                  (catch Exception _ 4))
                                :else 4))))
                     (reduce + 0))]
    (long (Math/ceil minutes))))

(defn performances-with-setlists
  "List performances with nested setlists"
  []
  (let [rows (exec-raw
              ["select p.id, p.performancedate, p.venue, p.free, p.openmic, p.gig_type,
                       p.fee_micro_gbp,
                       sp.setlistposition, sp.song_id, s.length
                from performances p
                left join song_performances sp on sp.performance_id = p.id
                left join songs s on s.title = sp.song_id
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
                                               vec)
                                  estimated-time (estimated-minutes items)]
                              {:id id
                               :performancedate (str (:performancedate base))
                               :venue (:venue base)
                               :free (:free base)
                               :openmic (:openmic base)
                               :gig_type (name (:gig_type base))
                               :fee_micro_gbp (:fee_micro_gbp base)
                               :estimated_time_minutes estimated-time
                               :setlist setlist})))
                     (sort-by :performancedate #(compare %2 %1))
                     vec)]
    (json-response grouped)))

(defn venue-performances-with-setlists
  "List performances for a venue with nested setlists"
  [venuename]
  (let [venue-name (when venuename (s/trim venuename))]
    (if (s/blank? venue-name)
      (-> (json-response {:error "venuename is required"})
          (resp/status 400))
      (let [rows (exec-raw
                  ["select p.id, p.performancedate, p.venue, p.free, p.openmic, p.gig_type,
                           p.fee_micro_gbp,
                           sp.setlistposition, sp.song_id, s.length
                    from performances p
                    left join song_performances sp on sp.performance_id = p.id
                    left join songs s on s.title = sp.song_id
                    where p.venue = ?
                    order by p.performancedate desc, p.id desc, sp.setlistposition asc"
                   [venue-name]]
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
                                                   vec)
                                      estimated-time (estimated-minutes items)]
                                  {:id id
                                   :performancedate (str (:performancedate base))
                                   :venue (:venue base)
                                   :free (:free base)
                                   :openmic (:openmic base)
                                   :gig_type (name (:gig_type base))
                                   :fee_micro_gbp (:fee_micro_gbp base)
                                   :estimated_time_minutes estimated-time
                                   :setlist setlist})))
                         (sort-by :performancedate #(compare %2 %1))
                         vec)]
        (json-response grouped)))))

(defn practice-sessions-with-songs
  "List practice sessions with nested songs"
  []
  (let [rows (exec-raw
              ["select ps.id, ps.practiced_on, ps.total_minutes,\n                      pss.position, pss.song_id, pss.song_title, pss.minutes, s.length\n               from practice_sessions ps\n               left join practice_session_songs pss on pss.practice_session_id = ps.id\n               left join songs s on s.title = pss.song_id\n               order by ps.practiced_on desc, ps.id desc, pss.position asc"]
              :results)
        grouped (->> rows
                     (group-by :id)
                     (map (fn [[id items]]
                            (let [base (first items)
                                  songs (->> items
                                             (filter :song_title)
                                             (map (fn [row]
                                                    {:position (:position row)
                                                     :title (:song_title row)
                                                     :minutes (:minutes row)}))
                                             vec)
                                  estimated-time (estimated-minutes items)]
                              {:id id
                               :practiced_on (str (:practiced_on base))
                               :total_minutes (:total_minutes base)
                               :estimated_time_minutes estimated-time
                               :songs songs})))
                     (sort-by :practiced_on #(compare %2 %1))
                     vec)]
    (json-response grouped)))

(defn singing-lessons-with-songs
  "List singing lessons with nested songs"
  []
  (let [rows (exec-raw
              ["select sl.id, sl.lesson_date, sl.duration_minutes,\n               sls.position, sls.song_title\n               from singing_lessons sl\n               left join singing_lesson_songs sls on sls.singing_lesson_id = sl.id\n               order by sl.lesson_date desc, sl.id desc, sls.position asc"]
              :results)
        grouped (->> rows
                     (group-by :id)
                     (map (fn [[id items]]
                            (let [base (first items)
                                  songs (->> items
                                             (filter :song_title)
                                             (map (fn [row]
                                                    {:position (:position row)
                                                     :title (:song_title row)}))
                                             vec)]
                              {:id id
                               :lesson_date (str (:lesson_date base))
                               :duration_minutes (:duration_minutes base)
                               :songs songs})))
                     (sort-by :lesson_date #(compare %2 %1))
                     vec)]
    (json-response grouped)))

(defn create-singing-lesson
  "Create a singing lesson and its songs"
  [request]
  (let [{:keys [date songs]} (json-read request)
        lesson-date (if (and date (not (s/blank? date))) date (str (time/today)))
        songs-list (if (vector? songs) songs [])]
    (if (or (not (seq songs-list))
            (not (every? (fn [song] (and (string? song) (not (s/blank? song)))) songs-list)))
      (-> (json-response {:error "songs are required"})
          (resp/status 400))
      (with-transaction
       (fn []
         (let [rows (exec-raw
                     ["insert into singing_lessons (lesson_date, duration_minutes) values (?, 120) returning id"
                      [(java.sql.Date/valueOf lesson-date)]]
                     :results)
               lesson-id (normalize-id rows)]
           (doseq [[idx song] (map-indexed vector songs-list)]
             (exec-raw
              ["insert into singing_lesson_songs (singing_lesson_id, song_title, position) values (?, ?, ?)"
               [lesson-id song (inc idx)]]))
           (json-response {:singingLessonId lesson-id
                           :songs (count songs-list)})))))))

(defn delete-singing-lesson
  "Delete a singing lesson and its songs"
  [id]
  (with-transaction
   (fn []
     (exec-raw
      ["delete from singing_lesson_songs where singing_lesson_id = ?"
       [(Integer/parseInt id)]])
     (let [rows (exec-raw
                 ["delete from singing_lessons where id = ? returning id"
                  [(Integer/parseInt id)]]
                 :results)
           row (first rows)]
       (if (nil? row)
         (-> (json-response {:error "singing lesson not found"})
             (resp/status 404))
        (json-response {:id (:id row)}))))))

(defn update-singing-lesson
  "Update a singing lesson and its songs"
  [id request]
  (let [{:keys [date songs]} (json-read request)
        lesson-date (if (and date (not (s/blank? date))) date nil)
        songs-list (if (vector? songs) songs [])]
    (if (or (s/blank? id) (nil? lesson-date))
      (-> (json-response {:error "date is required"})
          (resp/status 400))
      (if (or (not (seq songs-list))
              (not (every? (fn [song] (and (string? song) (not (s/blank? song)))) songs-list)))
        (-> (json-response {:error "songs are required"})
            (resp/status 400))
        (with-transaction
         (fn []
           (let [rows (exec-raw
                       ["update singing_lessons set lesson_date = ?, duration_minutes = 120 where id = ? returning id, lesson_date, duration_minutes"
                        [(java.sql.Date/valueOf lesson-date) (Integer/parseInt id)]]
                       :results)
                 row (first rows)]
             (if (nil? row)
               (-> (json-response {:error "singing lesson not found"})
                   (resp/status 404))
               (do
                 (exec-raw
                  ["delete from singing_lesson_songs where singing_lesson_id = ?"
                   [(Integer/parseInt id)]])
                 (doseq [[idx song] (map-indexed vector songs-list)]
                   (exec-raw
                    ["insert into singing_lesson_songs (singing_lesson_id, song_title, position) values (?, ?, ?)"
                     [(Integer/parseInt id) song (inc idx)]]))
                 (json-response {:id (:id row)
                                 :lesson_date (str (:lesson_date row))
                                 :duration_minutes (:duration_minutes row)
                                 :songs (count songs-list)}))))))))))

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
              ["select a.id, a.title as album_title, a.artist, a.release_date,
                      s.title as song_title, s.length, s.active, s.cover, s.instrumental,
                      s.recorded_key, s.my_live_key, s.capo, s.bpm,
                      vpl.last_performed_live, vpc.live_count,
                      vpr.last_practiced, vpp.practice_count,
                      asg.track_number
               from albums a
               join album_songs asg on asg.album_id = a.id
               join songs s on s.title = asg.song_title
               left join view_song_last_performed_live vpl on vpl.song_id = s.title
               left join view_song_perform_live_counts vpc on vpc.song_id = s.title
               left join view_song_last_practiced vpr on vpr.song_id = s.title
               left join view_song_practice_counts vpp on vpp.song_id = s.title
               where a.id = ?
               order by asg.track_number asc"
               [(Integer/parseInt album-id)]]
              :results)
        album (first rows)]
    (if (nil? album)
      (-> (json-response {:error "album not found"})
          (resp/status 404))
      (let [tracks (map (fn [row]
                          {:track_number (:track_number row)
                           :title (:song_title row)
                           :length (when-let [l (:length row)] (str l))
                           :active (:active row)
                           :cover (:cover row)
                           :instrumental (:instrumental row)
                           :recorded_key (:recorded_key row)
                           :my_live_key (:my_live_key row)
                           :capo (:capo row)
                           :bpm (:bpm row)
                           :last_performed_live (when-let [d (:last_performed_live row)] (str d))
                           :live_count (:live_count row)
                           :last_practiced (when-let [d (:last_practiced row)] (str d))
                           :practice_count (:practice_count row)})
                        rows)]
        (json-response {:id (:id album)
                        :title (:album_title album)
                        :artist (:artist album)
                        :release_date (when-let [d (:release_date album)] (str d))
                        :tracks (vec tracks)})))))

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

(defn next-songs-to-perform-live
  "Return a JSON array with songs, live performance count and time since last performance"
  []
  (->> (select view_next_songs_to_perform_live)
       (map (fn [row] (clojure.core/update row :last_played str)))
       vec
       json-response))

(defn next-songs-to-practise
  "Return a JSON array with songs, practice count and time since last practice"
  []
  (->> (select view_next_songs_to_practise)
       (map (fn [row] (clojure.core/update row :last_practiced str)))
       vec
       json-response))

(defn create-performance
  "Create a performance and its song_performances rows"
  [request]
  (let [{:keys [venue songs date gig_type fee_micro_gbp]} (json-read request)
        trimmed-venue (when venue (s/trim venue))
        songs-list (if (vector? songs) songs [])
        performance-date (if (and date (not (s/blank? date))) date (str (time/today)))
        gig-type-in (normalize-gig-type gig_type)
        gig-type-val (cond
                       (and gig-type-in (gig-types gig-type-in)) gig-type-in
                       gig-type-in nil
                       :else "open_mic")
        flags (gig-type->flags gig-type-val)
        free-val (:free flags)
        openmic-val (:openmic flags)
        fee-micro (or (normalize-fee-micro-gbp fee_micro_gbp) 0)]
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
                       ["insert into performances (performancedate, venue, free, openmic, fee_micro_gbp, gig_type) values (?, ?, ?, ?, ?, ?::gig_type) returning id"
                        [(java.sql.Date/valueOf performance-date) trimmed-venue free-val openmic-val fee-micro gig-type-val]]
                       :results)
                 performance-id (normalize-id rows)]
             (doseq [[idx song] (map-indexed vector songs-list)]
               (exec-raw
                ["insert into song_performances (song_id, performance_id, setlistposition) values (?, ?, ?)"
                 [song performance-id (inc idx)]]))
           (json-response {:performanceId performance-id
                           :songs          (count songs-list)}))))))))

(defn create-practice-session
  "Create a practice session and its practice_session_songs rows"
  [request]
  (let [{:keys [date total_minutes songs]} (json-read request)
        session-date (if (and date (not (s/blank? date))) date (str (time/today)))
        songs-list (if (vector? songs) songs [])
        normalized (map (fn [entry]
                          (cond
                            (string? entry) {:title entry :minutes nil}
                            (map? entry) {:title (:title entry) :minutes (:minutes entry)}
                            :else nil))
                        songs-list)
        valid (filter (fn [row]
                        (and row
                             (string? (:title row))
                             (not (s/blank? (:title row)))))
                      normalized)]
    (if (not (seq valid))
      (-> (json-response {:error "songs are required"})
          (resp/status 400))
      (with-transaction
       (fn []
         (let [rows (exec-raw
                     ["insert into practice_sessions (practiced_on, total_minutes) values (?, ?) returning id"
                      [(java.sql.Date/valueOf session-date) total_minutes]]
                     :results)
               session-id (normalize-id rows)]
           (doseq [[idx song] (map-indexed vector valid)]
             (exec-raw
              ["insert into practice_session_songs (practice_session_id, song_id, song_title, position, minutes) values (?, ?, ?, ?, ?)"
               [session-id (:title song) (:title song) (inc idx) (:minutes song)]]))
           (json-response {:practiceSessionId session-id
                           :songs (count valid)})))))))

(defn delete-practice-session
  "Delete a practice session and its songs"
  [id]
  (with-transaction
   (fn []
     (exec-raw
      ["delete from practice_session_songs where practice_session_id = ?"
       [(Integer/parseInt id)]])
     (let [rows (exec-raw
                 ["delete from practice_sessions where id = ? returning id"
                  [(Integer/parseInt id)]]
                 :results)
           row (first rows)]
       (if (nil? row)
         (-> (json-response {:error "practice session not found"})
             (resp/status 404))
         (json-response {:id (:id row)}))))))

(defn update-practice-session
  "Update a practice session and its songs"
  [id request]
  (let [{:keys [date total_minutes songs]} (json-read request)
        session-date (if (and date (not (s/blank? date))) date nil)
        songs-list (if (vector? songs) songs [])
        normalized (map (fn [entry]
                          (cond
                            (string? entry) {:title entry :minutes nil}
                            (map? entry) {:title (:title entry) :minutes (:minutes entry)}
                            :else nil))
                        songs-list)
        valid (filter (fn [row]
                        (and row
                             (string? (:title row))
                             (not (s/blank? (:title row)))))
                      normalized)]
    (if (or (s/blank? id) (nil? session-date))
      (-> (json-response {:error "date is required"})
          (resp/status 400))
      (if (not (seq valid))
        (-> (json-response {:error "songs are required"})
            (resp/status 400))
        (with-transaction
         (fn []
           (let [rows (exec-raw
                       ["update practice_sessions set practiced_on = ?, total_minutes = ? where id = ? returning id, practiced_on, total_minutes"
                        [(java.sql.Date/valueOf session-date) total_minutes (Integer/parseInt id)]]
                       :results)
                 row (first rows)]
             (if (nil? row)
               (-> (json-response {:error "practice session not found"})
                   (resp/status 404))
               (do
                 (exec-raw
                  ["delete from practice_session_songs where practice_session_id = ?"
                   [(Integer/parseInt id)]])
                 (doseq [[idx song] (map-indexed vector valid)]
                   (exec-raw
                    ["insert into practice_session_songs (practice_session_id, song_id, song_title, position, minutes) values (?, ?, ?, ?, ?)"
                     [(Integer/parseInt id) (:title song) (:title song) (inc idx) (:minutes song)]]))
                (json-response {:id (:id row)
                                :practiced_on (str (:practiced_on row))
                                :total_minutes (:total_minutes row)
                                :songs (count valid)}))))))))))
(defn update-performance
  "Update performance fields"
  [id request]
  (let [{:keys [venue date gig_type fee_micro_gbp]} (json-read request)
        trimmed-venue (when venue (s/trim venue))
        performance-date (if (and date (not (s/blank? date))) date nil)
        gig-type-in (normalize-gig-type gig_type)
        gig-type-val (cond
                       (and gig-type-in (gig-types gig-type-in)) gig-type-in
                       gig-type-in nil
                       :else "open_mic")
        flags (gig-type->flags gig-type-val)
        free-val (:free flags)
        openmic-val (:openmic flags)
        fee-micro (or (normalize-fee-micro-gbp fee_micro_gbp) 0)]
    (if (or (s/blank? trimmed-venue) (s/blank? performance-date))
      (-> (json-response {:error "venue and date are required"})
          (resp/status 400))
      (if (nil? gig-type-val)
        (-> (json-response {:error "gig_type is invalid"})
            (resp/status 400))
        (let [rows (exec-raw
                    ["update performances set performancedate = ?, venue = ?, free = ?, openmic = ?, fee_micro_gbp = ?, gig_type = ?::gig_type where id = ? returning id, performancedate, venue, free, openmic, fee_micro_gbp, gig_type"
                     [(java.sql.Date/valueOf performance-date) trimmed-venue free-val openmic-val fee-micro gig-type-val (Integer/parseInt id)]]
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
                            :fee_micro_gbp (:fee_micro_gbp row)
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
  (let [{:keys [title cover active length instrumental artist bpm recorded_key my_live_key capo album_id track_number]} (json-read request)
        active-val (if (some? active) active true)
        cover-val (if (some? cover) cover false)
        instrumental-val (if (some? instrumental) instrumental false)
        bpm-val (when (and (some? bpm) (not (s/blank? (str bpm))))
                  (Integer/parseInt (str bpm)))
        capo-val (when (and (some? capo) (not (s/blank? (str capo))))
                   (Integer/parseInt (str capo)))
        album-id (when (and (some? album_id) (not (s/blank? (str album_id))))
                   (Integer/parseInt (str album_id)))
        track-num (when (and (some? track_number) (not (s/blank? (str track_number))))
                    (Integer/parseInt (str track_number)))
        length-val (normalize-length-interval length)]
    (let [song-title (when title (s/trim title))]
      (if (s/blank? song-title)
        (-> (json-response {:error "title is required"})
            (resp/status 400))
        (with-transaction
         (fn []
           (exec-raw
           ["insert into songs (title, cover, active, key, length, instrumental, artist, bpm, recorded_key, my_live_key, capo) values (?, ?, ?, ?, ?::interval, ?, ?, ?, ?, ?, ?)"
            [song-title cover-val active-val recorded_key length-val instrumental-val artist bpm-val recorded_key my_live_key capo-val]])
           (when album-id
             (exec-raw
              ["insert into album_songs (album_id, song_title, track_number) values (?, ?, ?)"
               [album-id song-title track-num]]))
           (json-response {:title song-title})))))))

(defn update-song
  "Update song fields"
  [title request]
  (let [{:keys [cover active length instrumental artist bpm recorded_key my_live_key capo album_id track_number]} (json-read request)
        song-title (when title (s/trim title))
        active-val (if (some? active) active true)
        cover-val (if (some? cover) cover false)
        instrumental-val (if (some? instrumental) instrumental false)
        length-val (normalize-length-interval length)
        bpm-val (when (and (some? bpm) (not (s/blank? (str bpm))))
                  (Integer/parseInt (str bpm)))
        capo-val (when (and (some? capo) (not (s/blank? (str capo))))
                   (Integer/parseInt (str capo)))
        album-id (when (and (some? album_id) (not (s/blank? (str album_id))))
                   (Integer/parseInt (str album_id)))
        track-num (when (and (some? track_number) (not (s/blank? (str track_number))))
                    (Integer/parseInt (str track_number)))]
    (if (s/blank? song-title)
      (-> (json-response {:error "title is required"})
          (resp/status 400))
      (with-transaction
       (fn []
         (let [rows (exec-raw
                     ["update songs set cover = ?, active = ?, key = ?, length = ?::interval, instrumental = ?, artist = ?, bpm = ?, recorded_key = ?, my_live_key = ?, capo = ? where title = ? returning title, cover, active, key, length, instrumental, artist, bpm, recorded_key, my_live_key, capo"
                      [cover-val active-val recorded_key length-val instrumental-val artist bpm-val recorded_key my_live_key capo-val song-title]]
                     :results)
               row (first rows)]
           (if (nil? row)
             (-> (json-response {:error "song not found"})
                 (resp/status 404))
             (do
               (exec-raw
                ["delete from album_songs where song_title = ?"
                 [song-title]])
               (when album-id
                 (exec-raw
                  ["insert into album_songs (album_id, song_title, track_number) values (?, ?, ?)"
                   [album-id song-title track-num]]))
               (json-response {:title (:title row)
                               :cover (:cover row)
                               :active (:active row)
                               :key (:key row)
                               :length (when-let [l (:length row)] (str l))
                               :instrumental (:instrumental row)
                               :artist (:artist row)
                               :bpm (:bpm row)
                               :recorded_key (:recorded_key row)
                               :my_live_key (:my_live_key row)
                               :capo (:capo row)})))))))))

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

(defn live-gigs-by-year
  "Return yearly gig counts and estimated live minutes"
  []
  (let [rows (exec-raw
              ["select extract(year from p.performancedate)::int as year,\n                      count(distinct p.id)::int as gigs,\n                      coalesce(ceil(sum(case\n               when sp.song_id is null then 0\n               else coalesce(extract(epoch from s.length), 240)\n               end) / 60.0)::int, 0) as estimated_minutes\n               from performances p\n               left join song_performances sp on sp.performance_id = p.id\n               left join songs s on s.title = sp.song_id\n               group by year\n               order by year desc"]
              :results)]
    (json-response (vec rows))))

(defn stringify
  [date]
  (first (s/split (str date) #"\.")))

(defn normalize-length-interval
  [value]
  (let [raw (when (some? value) (s/trim (str value)))]
    (cond
      (s/blank? raw) nil
      (re-matches #"^\d{1,2}:\d{2}:\d{2}$" raw) raw
      (re-matches #"^\d{1,2}:\d{2}$" raw)
      (let [[m s] (s/split raw #":")]
        (format "00:%02d:%02d" (Integer/parseInt m) (Integer/parseInt s)))
      :else raw)))

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
  (GET "/next-songs-to-play" [] (next-songs-to-perform-live))
  (GET "/next-songs-to-perform-live" [] (next-songs-to-perform-live))
  (GET "/next-songs-to-practise" [] (next-songs-to-practise))
  (GET "/next-active-songs" [] (next-active-songs))
  (GET "/performances" [] (all-performances))
  (GET "/performances-with-setlists" [] (performances-with-setlists))
  (GET "/venues/:venuename/performances" [venuename]
       (venue-performances-with-setlists venuename))
  (GET "/practice-sessions" [] (practice-sessions-with-songs))
  (POST "/practice-sessions" request (create-practice-session request))
  (PUT "/practice-sessions/:id" [id :as request] (update-practice-session id request))
  (DELETE "/practice-sessions/:id" [id] (delete-practice-session id))
  (GET "/singing-lessons" [] (singing-lessons-with-songs))
  (POST "/singing-lessons" request (create-singing-lesson request))
  (PUT "/singing-lessons/:id" [id :as request] (update-singing-lesson id request))
  (DELETE "/singing-lessons/:id" [id] (delete-singing-lesson id))
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
  (GET "/live-gigs-by-year" [] (live-gigs-by-year))
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
