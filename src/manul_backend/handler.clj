(ns manul-backend.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [clojure.string :as s]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clj-time.core :as time]
            [clj-time.format :as f]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.util.response :as resp]
            [clojure.java.jdbc :as jdbc]
            [manul-backend.data.recent-sessions :as recent-sessions]
            [manul-backend.data.song-plays :as song-plays]
            [manul-backend.data.direct-fan-outreach :as direct-fan-outreach]
            [manul-backend.data.home-x-metrics :as home-x-metrics]
            [manul-backend.data.home-x-goals :as home-x-goals]))

(use 'korma.db)
(use 'korma.core)

(def gigs-sslmode (or (System/getenv "GIGS_SSLMODE") "disable"))

(declare normalize-length-interval)
(declare home-metrics-data)
(declare last-gig-date-data)

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
(defentity instruments)
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
(def home-x-goal-keys #{"gigs_lifetime"
                        "solo_practice_minutes_weekly"
                        "practice_hours_lifetime"
                        "originals_live_lifetime"
                        "direct_outreach_lifetime"})

(def outreach-refresh-interval-ms (* 5 60 1000))
(def outreach-retry-interval-ms 30000)
(defonce home-outreach-state
  (atom {:status :idle
         :in-flight false
         :metrics nil
         :updated-at-ms nil
         :last-attempt-ms nil}))

(defn now-ms
  []
  (System/currentTimeMillis))

(defn should-refresh-outreach?
  [state now]
  (cond
    (:in-flight state) false
    (= :idle (:status state)) true
    (= :pending (:status state)) false
    (= :error (:status state))
    (> (- now (or (:last-attempt-ms state) 0)) outreach-retry-interval-ms)
    :else
    (> (- now (or (:updated-at-ms state) 0)) outreach-refresh-interval-ms)))

(defn start-outreach-refresh!
  []
  (let [start? (atom false)
        now (now-ms)]
    (swap! home-outreach-state
           (fn [state]
             (if (should-refresh-outreach? state now)
               (do
                 (reset! start? true)
                 (assoc state
                        :status (if (:metrics state) :refreshing :pending)
                        :in-flight true
                        :last-attempt-ms now))
               state)))
    (when @start?
      (future
        (try
          (let [metrics (direct-fan-outreach/fetch-direct-fan-outreach-from direct-fan-outreach/db-store)]
            (swap! home-outreach-state assoc
                   :status :ready
                   :in-flight false
                   :metrics metrics
                   :updated-at-ms (now-ms)))
          (catch Exception _
            (swap! home-outreach-state assoc
                   :status :error
                   :in-flight false)))))))

(defn outreach-snapshot
  []
  (start-outreach-refresh!)
  @home-outreach-state)

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
                                (try
                                  (Long/parseLong trimmed)
                                  (catch NumberFormatException _ ::invalid))))
    :else nil))

(defn parse-int-field
  [value]
  (when (and (some? value) (not (s/blank? (str value))))
    (let [trimmed (s/trim (str value))]
      (when (not (s/blank? trimmed))
        (try
          (Integer/parseInt trimmed)
          (catch NumberFormatException _ ::invalid))))))

(defn parse-bool-field
  [value]
  (cond
    (nil? value) nil
    (instance? Boolean value) value
    (string? value) (let [trimmed (s/lower-case (s/trim value))]
                      (cond
                        (= trimmed "true") true
                        (= trimmed "false") false
                        :else ::invalid))
    :else ::invalid))

(defn sentinel-hour-for-session-type
  [session-type]
  (case session-type
    "performance" 20
    "solo_practice" 10
    "singing_lesson" 15
    12))

(defn sentinel-occurred-at
  [session-type date-str]
  (let [d (java.time.LocalDate/parse (str date-str))
        dt (.atTime d (sentinel-hour-for-session-type session-type) 0 0)]
    (java.sql.Timestamp/valueOf dt)))

(defn parse-occurred-at-field
  [value]
  (cond
    (nil? value) nil
    (instance? java.sql.Timestamp value) value
    (instance? java.util.Date value) (java.sql.Timestamp. (.getTime ^java.util.Date value))
    (string? value)
    (let [trimmed (s/trim value)]
      (if (s/blank? trimmed)
        nil
        (try
          (java.sql.Timestamp/from (java.time.Instant/parse trimmed))
          (catch Exception _
            (try
              (java.sql.Timestamp/valueOf (java.time.LocalDateTime/parse trimmed))
              (catch Exception _ ::invalid))))))
    :else ::invalid))

(defn trim-or-nil
  [value]
  (when (some? value)
    (let [trimmed (s/trim (str value))]
      (when (not (s/blank? trimmed))
        trimmed))))

(defn parse-string-list-field
  [value]
  (cond
    (nil? value) []
    (vector? value)
    (let [trimmed (map trim-or-nil value)]
      (if (every? some? trimmed)
        (vec trimmed)
        ::invalid))
    :else ::invalid))

(defn parse-int-list-field
  [value]
  (cond
    (nil? value) []
    (vector? value)
    (let [parsed (map parse-int-field value)]
      (if (and (every? integer? parsed) (every? pos? parsed))
        (vec parsed)
        ::invalid))
    :else ::invalid))

(def valid-practice-vocal-modes
  #{"instrumental_only" "sing_and_play" "sing_over_instrumental"})

(def valid-practice-postures
  #{"sitting" "standing"})

(defn parse-capo-position
  [value]
  (if (nil? value)
    nil
    (let [parsed (parse-int-field value)]
      (if (or (= parsed ::invalid) (neg? parsed) (> parsed 24))
        ::invalid
        parsed))))

(defn normalize-practice-song-entry
  [entry]
  (let [normalized (cond
                     (string? entry) {:title entry :minutes nil}
                     (map? entry) {:title (:title entry)
                                    :minutes (:minutes entry)
                                    :instrument_id (:instrument_id entry)
                                    :vocal_mode (or (:vocal_mode entry) (:vocal_mode_code entry))
                                     :capo_position (or (:capo_position entry) (:capo entry))
                                     :used_metronome (:used_metronome entry)
                                     :practice_posture (or (:practice_posture entry) (:posture entry))
                                     :metronome_bpms (:metronome_bpms entry)
                                     :key_changes (:key_changes entry)
                                     :notes (:notes entry)}
                     :else nil)]
    (when normalized
      (let [title (trim-or-nil (:title normalized))
            instrument-id (parse-int-field (:instrument_id normalized))
            vocal-mode (trim-or-nil (:vocal_mode normalized))
            capo-position (parse-capo-position (:capo_position normalized))
            used-metronome (parse-bool-field (:used_metronome normalized))
            practice-posture (trim-or-nil (:practice_posture normalized))
            metronome-bpms (parse-int-list-field (:metronome_bpms normalized))
            key-changes (parse-string-list-field (:key_changes normalized))
            notes (trim-or-nil (:notes normalized))]
        (if (or (nil? title)
                (= instrument-id ::invalid)
                (= capo-position ::invalid)
                (= used-metronome ::invalid)
                (= metronome-bpms ::invalid)
                (= key-changes ::invalid)
                (and vocal-mode (not (valid-practice-vocal-modes vocal-mode)))
                (and practice-posture (not (valid-practice-postures practice-posture))))
          ::invalid
          {:title title
           :minutes (:minutes normalized)
           :instrument_id instrument-id
           :vocal_mode vocal-mode
           :capo_position capo-position
           :used_metronome used-metronome
           :practice_posture practice-posture
           :metronome_bpms metronome-bpms
           :key_changes key-changes
           :notes notes})))))

(defn practice-song-metadata-present?
  [song]
  (or (:instrument_id song)
      (:vocal_mode song)
      (some? (:capo_position song))
      (some? (:used_metronome song))
      (:practice_posture song)
      (seq (:metronome_bpms song))
      (seq (:key_changes song))
      (:notes song)))

(defn instrument-exists?
  [instrument-id]
  (if (nil? instrument-id)
    true
    (let [rows (exec-raw
                ["select id from instruments where id = ?"
                 [instrument-id]]
                :results)]
      (boolean (first rows)))))

(defn resolve-practice-vocal-mode-id
  [vocal-mode-code]
  (when vocal-mode-code
    (let [rows (exec-raw
                ["select id from practice_vocal_modes where code = ?"
                 [vocal-mode-code]]
                :results)]
      (:id (first rows)))))

(defn insert-practice-song-metadata!
  [session-id song]
  (when (practice-song-metadata-present? song)
    (let [instrument-id (:instrument_id song)
          vocal-mode-id (resolve-practice-vocal-mode-id (:vocal_mode song))]
      (when (and instrument-id (not (instrument-exists? instrument-id)))
        (throw (ex-info "instrument is invalid" {})))
      (when (and (:vocal_mode song) (nil? vocal-mode-id))
        (throw (ex-info "vocal mode is invalid" {})))
      (exec-raw
       ["insert into practice_song_details
         (practice_session_id, song_title, instrument_id, vocal_mode_id, capo_position, used_metronome, practice_posture, notes)
         values (?, ?, ?, ?, ?, ?, ?, ?)"
        [session-id
         (:title song)
         instrument-id
         vocal-mode-id
         (:capo_position song)
         (:used_metronome song)
         (:practice_posture song)
         (:notes song)]])
      (doseq [[idx bpm] (map-indexed vector (:metronome_bpms song))]
        (exec-raw
         ["insert into practice_song_tempos (practice_session_id, song_title, ordinal, bpm)
           values (?, ?, ?, ?)"
          [session-id (:title song) (inc idx) bpm]]))
      (doseq [[idx key-name] (map-indexed vector (:key_changes song))]
        (exec-raw
         ["insert into practice_song_keys (practice_session_id, song_title, ordinal, key_name)
           values (?, ?, ?, ?)"
          [session-id (:title song) (inc idx) key-name]])))))

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
              ["select p.id, p.performancedate, p.occurred_at, p.created_at, p.venue, p.free, p.openmic, p.gig_type,
                       p.fee_micro_gbp,
                       sp.setlistposition, sp.song_id, s.length
                from performances p
                left join song_performances sp on sp.performance_id = p.id
                left join songs s on s.title = sp.song_id
                order by p.performancedate desc, p.occurred_at desc, p.created_at desc, p.id desc, sp.setlistposition asc"]
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
                               :occurred_at (when-let [occurred (:occurred_at base)] (str occurred))
                               :created_at (when-let [created (:created_at base)] (str created))
                               :venue (:venue base)
                               :free (:free base)
                               :openmic (:openmic base)
                               :gig_type (name (:gig_type base))
                               :fee_micro_gbp (:fee_micro_gbp base)
                               :estimated_time_minutes estimated-time
                               :setlist setlist})))
                     (sort-by (fn [row]
                                (or (:occurred_at row) (:created_at row) (:performancedate row)))
                              #(compare %2 %1))
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
                  ["select p.id, p.performancedate, p.occurred_at, p.created_at, p.venue, p.free, p.openmic, p.gig_type,
                           p.fee_micro_gbp,
                           sp.setlistposition, sp.song_id, s.length
                    from performances p
                    left join song_performances sp on sp.performance_id = p.id
                    left join songs s on s.title = sp.song_id
                    where p.venue = ?
                    order by p.performancedate desc, p.occurred_at desc, p.created_at desc, p.id desc, sp.setlistposition asc"
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
                                   :occurred_at (when-let [occurred (:occurred_at base)] (str occurred))
                                   :created_at (when-let [created (:created_at base)] (str created))
                                   :venue (:venue base)
                                   :free (:free base)
                                   :openmic (:openmic base)
                                   :gig_type (name (:gig_type base))
                                   :fee_micro_gbp (:fee_micro_gbp base)
                                   :estimated_time_minutes estimated-time
                                   :setlist setlist})))
                         (sort-by (fn [row]
                                    (or (:occurred_at row) (:created_at row) (:performancedate row)))
                                  #(compare %2 %1))
                         vec)]
        (json-response grouped)))))

(defn practice-sessions-with-songs
  "List practice sessions with nested songs"
  []
  (let [rows (exec-raw
              ["select ps.id, ps.practiced_on, ps.occurred_at, ps.created_at, ps.total_minutes,\n                      pss.position, pss.song_id, pss.song_title, pss.minutes, s.length,\n                      psd.instrument_id,\n                      i.name as instrument_name,\n                      pvm.code as vocal_mode_code,\n                      psd.capo_position,\n                      psd.used_metronome,\n                      psd.practice_posture,\n                      psd.notes,\n                      coalesce((\n                        select json_agg(pst.bpm order by pst.ordinal)\n                        from practice_song_tempos pst\n                        where pst.practice_session_id = pss.practice_session_id\n                          and pst.song_title = pss.song_title\n                      ), '[]'::json) as metronome_bpms_json,\n                      coalesce((\n                        select json_agg(psk.key_name order by psk.ordinal)\n                        from practice_song_keys psk\n                        where psk.practice_session_id = pss.practice_session_id\n                          and psk.song_title = pss.song_title\n                      ), '[]'::json) as key_changes_json\n               from practice_sessions ps\n               left join practice_session_songs pss on pss.practice_session_id = ps.id\n               left join songs s on s.title = pss.song_id\n               left join practice_song_details psd\n                 on psd.practice_session_id = pss.practice_session_id\n                and psd.song_title = pss.song_title\n               left join instruments i on i.id = psd.instrument_id\n               left join practice_vocal_modes pvm on pvm.id = psd.vocal_mode_id\n               order by ps.practiced_on desc, ps.occurred_at desc, ps.created_at desc, ps.id desc, pss.position asc"]
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
                                                     :minutes (:minutes row)
                                                     :instrument_id (:instrument_id row)
                                                     :instrument (:instrument_name row)
                                                     :vocal_mode (:vocal_mode_code row)
                                                     :capo_position (:capo_position row)
                                                     :used_metronome (:used_metronome row)
                                                     :practice_posture (:practice_posture row)
                                                     :metronome_bpms (if-let [raw (:metronome_bpms_json row)]
                                                                       (json/read-str (str raw))
                                                                       [])
                                                     :key_changes (if-let [raw (:key_changes_json row)]
                                                                    (json/read-str (str raw))
                                                                    [])
                                                     :notes (:notes row)}))
                                             vec)
                                  estimated-time (estimated-minutes items)]
                              {:id id
                               :practiced_on (str (:practiced_on base))
                               :occurred_at (when-let [occurred (:occurred_at base)] (str occurred))
                               :created_at (when-let [created (:created_at base)] (str created))
                               :total_minutes (:total_minutes base)
                               :estimated_time_minutes estimated-time
                               :songs songs})))
                     (sort-by (fn [row]
                                (or (:occurred_at row) (:created_at row) (:practiced_on row)))
                              #(compare %2 %1))
                     vec)]
    (json-response grouped)))

(defn singing-lessons-with-songs
  "List singing lessons with nested songs"
  []
  (let [rows (exec-raw
              ["select sl.id, sl.lesson_date, sl.occurred_at, sl.created_at, sl.duration_minutes,\n               sls.position, sls.song_title\n               from singing_lessons sl\n               left join singing_lesson_songs sls on sls.singing_lesson_id = sl.id\n               order by sl.lesson_date desc, sl.occurred_at desc, sl.created_at desc, sl.id desc, sls.position asc"]
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
                               :occurred_at (when-let [occurred (:occurred_at base)] (str occurred))
                               :created_at (when-let [created (:created_at base)] (str created))
                               :duration_minutes (:duration_minutes base)
                               :songs songs})))
                     (sort-by (fn [row]
                                (or (:occurred_at row) (:created_at row) (:lesson_date row)))
                              #(compare %2 %1))
                     vec)]
    (json-response grouped)))

(defn create-singing-lesson
  "Create a singing lesson and its songs"
  [request]
  (let [{:keys [date songs occurred_at]} (json-read request)
        lesson-date (if (and date (not (s/blank? date))) date (str (time/today)))
        occurred-at-val (or (parse-occurred-at-field occurred_at)
                            (sentinel-occurred-at "singing_lesson" lesson-date))
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
             (exec-raw
              ["update singing_lessons set occurred_at = ? where id = ?"
               [occurred-at-val lesson-id]])
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
  (let [payload (json-read request)
        {:keys [date songs occurred_at]} payload
        lesson-date (if (and date (not (s/blank? date))) date nil)
        occurred-at-val (if (contains? payload :occurred_at)
                          (parse-occurred-at-field occurred_at)
                          nil)
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
                 (when occurred-at-val
                   (exec-raw
                    ["update singing_lessons set occurred_at = ? where id = ?"
                     [occurred-at-val (Integer/parseInt id)]]))
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

(defn all-instruments
  "List all instruments"
  []
  (let [rows (exec-raw
              ["select id, name, manufacturer, model, family, arrange_category, competence, next_step
                from instruments
                order by name asc"]
              :results)]
    (json-response (vec rows))))

(defn create-instrument
  "Create an instrument"
  [request]
  (let [{:keys [name manufacturer model family arrange_category competence next_step]} (json-read request)
        trimmed-name (trim-or-nil name)]
    (if (nil? trimmed-name)
      (-> (json-response {:error "name is required"})
          (resp/status 400))
      (let [rows (exec-raw
                  ["insert into instruments (name, manufacturer, model, family, arrange_category, competence, next_step)
                    values (?, ?, ?, ?, ?, ?, ?)
                    on conflict (name) do update
                      set manufacturer = excluded.manufacturer,
                          model = excluded.model,
                          family = excluded.family,
                          arrange_category = excluded.arrange_category,
                          competence = excluded.competence,
                          next_step = excluded.next_step
                    returning id, name, manufacturer, model, family, arrange_category, competence, next_step"
                   [trimmed-name
                    (trim-or-nil manufacturer)
                    (trim-or-nil model)
                    (trim-or-nil family)
                    (trim-or-nil arrange_category)
                    (trim-or-nil competence)
                    (trim-or-nil next_step)]]
                  :results)]
        (json-response (first rows))))))

(defn update-instrument
  "Update an instrument"
  [id request]
  (let [{:keys [name manufacturer model family arrange_category competence next_step]} (json-read request)
        trimmed-name (trim-or-nil name)]
    (if (or (s/blank? id) (nil? trimmed-name))
      (-> (json-response {:error "name is required"})
          (resp/status 400))
      (let [rows (exec-raw
                  ["update instruments
                    set name = ?, manufacturer = ?, model = ?, family = ?, arrange_category = ?, competence = ?, next_step = ?
                    where id = ?
                    returning id, name, manufacturer, model, family, arrange_category, competence, next_step"
                   [trimmed-name
                    (trim-or-nil manufacturer)
                    (trim-or-nil model)
                    (trim-or-nil family)
                    (trim-or-nil arrange_category)
                    (trim-or-nil competence)
                    (trim-or-nil next_step)
                    (Integer/parseInt id)]]
                  :results)
            row (first rows)]
        (if row
          (json-response row)
          (-> (json-response {:error "instrument not found"})
              (resp/status 404)))))))

(defn delete-instrument
  "Delete an instrument"
  [id]
  (if (s/blank? id)
    (-> (json-response {:error "id is required"})
        (resp/status 400))
    (let [rows (exec-raw
                ["delete from instruments where id = ? returning id"
                 [(Integer/parseInt id)]]
                :results)
          row (first rows)]
      (if row
        (json-response {:id (:id row)})
        (-> (json-response {:error "instrument not found"})
            (resp/status 404))))))

(def london-postcode-pattern #"^(E|EC|N|NW|SE|SW|W|WC)\d")

(defonce geocode-cache (atom {}))

(defn london-postcode?
  [postcode]
  (boolean (re-find london-postcode-pattern (s/upper-case (or postcode "")))))

(defn geocode-query
  [query]
  (when (and query (not (s/blank? query)))
    (if (contains? @geocode-cache query)
      (get @geocode-cache query)
      (let [url (str "https://nominatim.openstreetmap.org/search?format=json&limit=1&q="
                     (java.net.URLEncoder/encode query "UTF-8"))
            conn (.openConnection (java.net.URL. url))]
        (.setRequestProperty conn "User-Agent" "manul-backend/1.0")
        (let [result (with-open [r (io/reader (.getInputStream conn))]
                       (let [rows (json/read r :key-fn keyword)
                             row (first rows)]
                         (when row
                           {:lat (Double/parseDouble (str (:lat row)))
                            :lng (Double/parseDouble (str (:lon row)))})))]
          (swap! geocode-cache assoc query result)
          result)))))

(defn london-heatmap
  []
  (try
    (let [rows (exec-raw
                ["select v.venuename, v.postcode, count(p.id) as gig_count
                  from performances p
                  join venues v on v.venuename = p.venue
                  group by v.venuename, v.postcode
                  order by count(p.id) desc, v.venuename asc"]
                :results)
          filtered (filter (fn [{:keys [venuename postcode]}]
                             (or (london-postcode? postcode)
                                 (re-find #"\bLondon\b" (or venuename ""))))
                           rows)
          payload (reduce (fn [acc {:keys [venuename postcode gig_count]}]
                            (let [count* (int (or gig_count 0))
                                  query (if (london-postcode? postcode) postcode venuename)
                                  geo (try
                                        (geocode-query query)
                                        (catch Exception _ nil))]
                              (if geo
                                (clojure.core/update acc :points conj {:name venuename
                                                                       :count count*
                                                                       :lat (:lat geo)
                                                                       :lng (:lng geo)
                                                                       :intensity (min count* 10)})
                                (clojure.core/update acc :unresolved conj venuename))))
                          {:points [] :unresolved []}
                          filtered)]
      (json-response payload))
    (catch Exception e
      (-> (json-response {:error (or (.getMessage e) "Failed to load heatmap")})
          (resp/status 500)))))

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
  (->> (song-plays/fetch-next-live-songs-by-frecency song-plays/db-store 500)
       (map (fn [row] (clojure.core/update row :last_played str)))
       vec
       json-response))

(defn next-songs-to-practise
  "Return a JSON array with songs, play count and time since last play in any context"
  []
  (->> (song-plays/fetch-next-practice-songs-by-frecency song-plays/db-store 500)
       (map (fn [row] (clojure.core/update row :last_played_anywhere str)))
       vec
       json-response))

(defn home-next-actions-data
  "Return backend-owned next-action rankings."
  []
  (let [live (->> (song-plays/fetch-next-live-songs-by-frecency song-plays/db-store 3)
                  (map (fn [row] (clojure.core/update row :last_played str)))
                  vec)
        solo-practice (->> (song-plays/fetch-next-practice-songs-by-frecency song-plays/db-store 3)
                           (map (fn [row] (clojure.core/update row :last_played_anywhere str)))
                           vec)]
    {:next_live live
     :next_solo_practice solo-practice}))

(defn home-next-actions
  "Return thin home-page recommendations with backend-owned ranking logic."
  []
  (json-response (home-next-actions-data)))

(defn recent-sessions-data
  "Return the most recent sessions by session date."
  []
  (->> (recent-sessions/fetch-recent-sessions-from recent-sessions/db-store 5)
       (map (fn [row]
              (let [effective (or (:effective_minutes row)
                                  (:actual_minutes row)
                                  (:minimum_minutes row))]
                (-> row
                    (assoc :effective_minutes effective)
                    (clojure.core/update :session_date str)
                    (clojure.core/update :session_occurred_at (fn [value]
                                                                (when value (str value))))
                    (clojure.core/update :session_created_at (fn [value]
                                                               (when value (str value))))))))
       vec))

(defn recent-sessions
  "Return a JSON array with the 5 most recent sessions by session date."
  []
  (json-response (recent-sessions-data)))

(defn home-metrics-local-data
  []
  (let [metrics-row (home-x-metrics/fetch-home-x-metrics-from home-x-metrics/db-store)
        solo-practice-heatmap (->> (exec-raw
                                    ["with days as (
                                        select generate_series((current_date - interval '363 days')::date,
                                                               current_date::date,
                                                               interval '1 day')::date as day
                                      )
                                      select d.day,
                                             coalesce(sum(vrs.effective_minutes), 0)::int as minutes
                                      from days d
                                      left join view_recent_sessions vrs
                                        on vrs.session_date = d.day
                                       and vrs.session_type = 'solo_practice'
                                      group by d.day
                                      order by d.day asc"]
                                    :results)
                                   (map (fn [row]
                                          {:date (str (:day row))
                                           :minutes (:minutes row)}))
                                   vec)
        all-activity-heatmap (->> (exec-raw
                                   ["with days as (
                                       select generate_series((current_date - interval '363 days')::date,
                                                              current_date::date,
                                                              interval '1 day')::date as day
                                     )
                                     select d.day,
                                            coalesce(sum(vrs.effective_minutes), 0)::int as minutes
                                     from days d
                                     left join view_recent_sessions vrs
                                       on vrs.session_date = d.day
                                     group by d.day
                                     order by d.day asc"]
                                   :results)
                                  (map (fn [row]
                                         {:date (str (:day row))
                                          :minutes (:minutes row)}))
                                  vec)
        focus-songs (->> (exec-raw
                          ["select v.song_id
                            from view_next_songs_to_perform_live v
                            join songs s on s.title = v.song_id
                            where s.artist = 'Farhan Mannan'
                            order by v.count asc, v.song_id asc
                            limit 3"]
                          :results)
                         (map :song_id)
                         vec)
        goals (try
                (home-x-goals/fetch-home-x-goals-from home-x-goals/db-store)
                (catch Exception _
                  home-x-goals/default-goals))]
    {:metrics {:gigs_ytd (:gigs_ytd metrics-row)
               :gigs_lifetime (:gigs_lifetime metrics-row)
               :solo_practice_minutes_weekly (:solo_practice_minutes_weekly metrics-row)
               :practice_minutes_ytd (:practice_minutes_ytd metrics-row)
               :practice_minutes_lifetime (:practice_minutes_lifetime metrics-row)
               :songs_performed_live_ytd (:songs_performed_live_ytd metrics-row)
               :songs_performed_live_lifetime (:songs_performed_live_lifetime metrics-row)
               :sessions_ytd (:sessions_ytd metrics-row)}
     :goals goals
     :focus_songs focus-songs
     :solo_practice_minutes_heatmap solo-practice-heatmap
     :all_activity_minutes_heatmap all-activity-heatmap}))

(defn home-metrics-outreach-data
  []
  (let [snapshot (outreach-snapshot)]
    {:metrics (:metrics snapshot)
     :status (name (:status snapshot))
     :updated_at_ms (:updated-at-ms snapshot)}))

(defn week-progress-fraction
  []
  (let [today (java.time.LocalDate/now)
        week-start (.atStartOfDay
                    (.with today
                           (java.time.temporal.TemporalAdjusters/previousOrSame java.time.DayOfWeek/MONDAY)))
        week-end (.plusDays week-start 7)
        now (java.time.LocalDateTime/now)
        total-seconds (double (.getSeconds (java.time.Duration/between week-start week-end)))
        elapsed-seconds (double (.getSeconds (java.time.Duration/between week-start now)))
        raw-fraction (if (pos? total-seconds) (/ elapsed-seconds total-seconds) 0.0)]
    (max 0.0 (min 1.0 raw-fraction))))

(defn farhan-weekly-pace-data
  [weekly-goal]
  (let [safe-goal (max 0 (long (or weekly-goal 0)))
        fraction (week-progress-fraction)
        expected-minutes (long (Math/round (* safe-goal fraction)))
        expected-progress-pct (long (Math/round (* 100.0 fraction)))]
    {:farhan_weekly_expected_minutes expected-minutes
     :farhan_weekly_expected_progress_pct expected-progress-pct
     :week_mode "calendar_week"}))

(defn home-metrics-data
  []
  (let [local (home-metrics-local-data)
        outreach (home-metrics-outreach-data)
        outreach-metrics (or (:metrics outreach) {})
        weekly-goal (get-in local [:goals :solo_practice_minutes_weekly])]
    (-> local
        (assoc :pace (farhan-weekly-pace-data weekly-goal))
        (assoc :outreach_status (:status outreach))
        (assoc :outreach_updated_at_ms (:updated_at_ms outreach))
        (clojure.core/update :metrics merge outreach-metrics))))

(defn home-metrics-local
  []
  (json-response (home-metrics-local-data)))

(defn home-metrics-outreach
  []
  (json-response (home-metrics-outreach-data)))

(defn home-metrics
  []
  (json-response (home-metrics-data)))

(defn home-x-local
  []
  (json-response (home-metrics-local-data)))

(defn home-x-outreach
  []
  (json-response (home-metrics-outreach-data)))

(defn home-x
  "Return combined Home-X payload (local + outreach)."
  []
  (json-response (home-metrics-data)))

(defn home-x-data
  []
  (home-metrics-data))

(defn update-home-x-goal
  [goal-key request]
  (let [{:keys [target]} (json-read request)
        parsed-target (parse-int-field target)]
    (cond
      (not (home-x-goal-keys goal-key))
      (-> (json-response {:error "goal key is invalid"})
          (resp/status 400))
      (= parsed-target ::invalid)
      (-> (json-response {:error "target is invalid"})
          (resp/status 400))
      (or (nil? parsed-target) (<= parsed-target 0))
      (-> (json-response {:error "target is invalid"})
          (resp/status 400))
      :else
      (let [row (home-x-goals/update-home-x-goal-from home-x-goals/db-store goal-key parsed-target)]
        (json-response {:goal_key (:goal_key row)
                        :target_value (:target_value row)})))))

(defn update-home-goal
  [goal-key request]
  (update-home-x-goal goal-key request))

(defn create-performance
  "Create a performance and its song_performances rows"
  [request]
  (let [{:keys [venue songs date gig_type fee_micro_gbp occurred_at]} (json-read request)
        trimmed-venue (when venue (s/trim venue))
        songs-list (if (vector? songs) songs [])
        performance-date (if (and date (not (s/blank? date))) date (str (time/today)))
        occurred-at-val (or (parse-occurred-at-field occurred_at)
                            (sentinel-occurred-at "performance" performance-date))
        gig-type-in (normalize-gig-type gig_type)
        gig-type-val (cond
                       (and gig-type-in (gig-types gig-type-in)) gig-type-in
                       gig-type-in nil
                       :else "open_mic")
        flags (gig-type->flags gig-type-val)
        free-val (:free flags)
        openmic-val (:openmic flags)
        fee-micro (normalize-fee-micro-gbp fee_micro_gbp)]
    (if (or (s/blank? trimmed-venue)
            (not (seq songs-list))
            (not (every? (fn [song] (and (string? song) (not (s/blank? song)))) songs-list)))
      (-> (json-response {:error "venue and songs are required"})
          (resp/status 400))
      (cond
        (nil? gig-type-val)
        (-> (json-response {:error "gig_type is invalid"})
            (resp/status 400))
        (= fee-micro ::invalid)
        (-> (json-response {:error "fee_micro_gbp is invalid"})
            (resp/status 400))
        :else
        (with-transaction
         (fn []
           (let [rows (exec-raw
                       ["insert into performances (performancedate, venue, free, openmic, fee_micro_gbp, gig_type) values (?, ?, ?, ?, ?, ?::gig_type) returning id"
                        [(java.sql.Date/valueOf performance-date) trimmed-venue free-val openmic-val (or fee-micro 0) gig-type-val]]
                       :results)
                 performance-id (normalize-id rows)]
             (exec-raw
              ["update performances set occurred_at = ? where id = ?"
               [occurred-at-val performance-id]])
             (doseq [[idx song] (map-indexed vector songs-list)]
               (exec-raw
                ["insert into song_performances (song_id, performance_id, setlistposition) values (?, ?, ?)"
                 [song performance-id (inc idx)]]))
           (json-response {:performanceId performance-id
                           :songs          (count songs-list)}))))))))

(defn create-practice-session
  "Create a practice session and its practice_session_songs rows"
  [request]
  (let [{:keys [date total_minutes songs occurred_at]} (json-read request)
        session-date (if (and date (not (s/blank? date))) date (str (time/today)))
        occurred-at-val (or (parse-occurred-at-field occurred_at)
                            (sentinel-occurred-at "solo_practice" session-date))
        songs-list (if (vector? songs) songs [])
        normalized (map normalize-practice-song-entry songs-list)
        valid (filter (fn [row]
                        (and row (not= row ::invalid)))
                      normalized)]
    (if (or (not (seq valid)) (not= (count valid) (count songs-list)))
      (-> (json-response {:error "songs are required"})
          (resp/status 400))
      (try
        (with-transaction
         (fn []
           (let [rows (exec-raw
                       ["insert into practice_sessions (practiced_on, total_minutes) values (?, ?) returning id"
                        [(java.sql.Date/valueOf session-date) total_minutes]]
                       :results)
                 session-id (normalize-id rows)]
             (exec-raw
              ["update practice_sessions set occurred_at = ? where id = ?"
               [occurred-at-val session-id]])
             (doseq [[idx song] (map-indexed vector valid)]
               (exec-raw
                ["insert into practice_session_songs (practice_session_id, song_id, song_title, position, minutes) values (?, ?, ?, ?, ?)"
                 [session-id (:title song) (:title song) (inc idx) (:minutes song)]])
               (insert-practice-song-metadata! session-id song))
             (json-response {:practiceSessionId session-id
                             :songs (count valid)}))))
        (catch clojure.lang.ExceptionInfo e
          (-> (json-response {:error (.getMessage e)})
              (resp/status 400)))))))

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
  (let [payload (json-read request)
        {:keys [date total_minutes songs occurred_at]} payload
        session-date (if (and date (not (s/blank? date))) date nil)
        occurred-at-val (if (contains? payload :occurred_at)
                          (parse-occurred-at-field occurred_at)
                          nil)
        songs-list (if (vector? songs) songs [])
        normalized (map normalize-practice-song-entry songs-list)
        valid (filter (fn [row]
                        (and row (not= row ::invalid)))
                      normalized)]
    (if (or (s/blank? id) (nil? session-date))
      (-> (json-response {:error "date is required"})
          (resp/status 400))
      (if (or (not (seq valid)) (not= (count valid) (count songs-list)))
        (-> (json-response {:error "songs are required"})
            (resp/status 400))
        (try
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
                   (when occurred-at-val
                     (exec-raw
                      ["update practice_sessions set occurred_at = ? where id = ?"
                       [occurred-at-val (Integer/parseInt id)]]))
                   (exec-raw
                    ["delete from practice_session_songs where practice_session_id = ?"
                     [(Integer/parseInt id)]])
                   (doseq [[idx song] (map-indexed vector valid)]
                     (exec-raw
                      ["insert into practice_session_songs (practice_session_id, song_id, song_title, position, minutes) values (?, ?, ?, ?, ?)"
                       [(Integer/parseInt id) (:title song) (:title song) (inc idx) (:minutes song)]])
                     (insert-practice-song-metadata! (Integer/parseInt id) song))
                   (json-response {:id (:id row)
                                   :practiced_on (str (:practiced_on row))
                                   :total_minutes (:total_minutes row)
                                   :songs (count valid)}))))))
          (catch clojure.lang.ExceptionInfo e
            (-> (json-response {:error (.getMessage e)})
                (resp/status 400))))))))
(defn update-performance
  "Update performance fields"
  [id request]
  (let [payload (json-read request)
        {:keys [venue date gig_type fee_micro_gbp occurred_at]} payload
        trimmed-venue (when venue (s/trim venue))
        performance-date (if (and date (not (s/blank? date))) date nil)
        occurred-at-val (if (contains? payload :occurred_at)
                          (parse-occurred-at-field occurred_at)
                          nil)
        gig-type-in (normalize-gig-type gig_type)
        gig-type-val (cond
                       (and gig-type-in (gig-types gig-type-in)) gig-type-in
                       gig-type-in nil
                       :else "open_mic")
        flags (gig-type->flags gig-type-val)
        free-val (:free flags)
        openmic-val (:openmic flags)
        fee-micro (normalize-fee-micro-gbp fee_micro_gbp)]
    (if (or (s/blank? trimmed-venue) (s/blank? performance-date))
      (-> (json-response {:error "venue and date are required"})
          (resp/status 400))
      (cond
        (nil? gig-type-val)
        (-> (json-response {:error "gig_type is invalid"})
            (resp/status 400))
        (= fee-micro ::invalid)
        (-> (json-response {:error "fee_micro_gbp is invalid"})
            (resp/status 400))
        :else
        (let [rows (exec-raw
                    ["update performances set performancedate = ?, venue = ?, free = ?, openmic = ?, fee_micro_gbp = ?, gig_type = ?::gig_type where id = ? returning id, performancedate, venue, free, openmic, fee_micro_gbp, gig_type"
                     [(java.sql.Date/valueOf performance-date) trimmed-venue free-val openmic-val (or fee-micro 0) gig-type-val (Integer/parseInt id)]]
                    :results)
              row (first rows)]
          (if (nil? row)
            (-> (json-response {:error "performance not found"})
                (resp/status 404))
            (do
              (when occurred-at-val
                (exec-raw
                 ["update performances set occurred_at = ? where id = ?"
                  [occurred-at-val (Integer/parseInt id)]]))
              (json-response {:id (:id row)
                              :performancedate (str (:performancedate row))
                              :venue (:venue row)
                              :free (:free row)
                              :openmic (:openmic row)
                              :fee_micro_gbp (:fee_micro_gbp row)
                              :gig_type (name (:gig_type row))}))))))))

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
        bpm-val (parse-int-field bpm)
        capo-val (parse-int-field capo)
        album-id (when (and (some? album_id) (not (s/blank? (str album_id))))
                   (Integer/parseInt (str album_id)))
        track-num (when (and (some? track_number) (not (s/blank? (str track_number))))
                    (Integer/parseInt (str track_number)))
        length-val (normalize-length-interval length)]
    (let [song-title (when title (s/trim title))]
      (cond
        (s/blank? song-title)
        (-> (json-response {:error "title is required"})
            (resp/status 400))
        (= bpm-val ::invalid)
        (-> (json-response {:error "bpm must be a number"})
            (resp/status 400))
        (= capo-val ::invalid)
        (-> (json-response {:error "capo must be a number"})
            (resp/status 400))
        :else
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
        bpm-val (parse-int-field bpm)
        capo-val (parse-int-field capo)
        album-id (when (and (some? album_id) (not (s/blank? (str album_id))))
                   (Integer/parseInt (str album_id)))
        track-num (when (and (some? track_number) (not (s/blank? (str track_number))))
                    (Integer/parseInt (str track_number)))]
    (cond
      (s/blank? song-title)
      (-> (json-response {:error "title is required"})
          (resp/status 400))
      (= bpm-val ::invalid)
      (-> (json-response {:error "bpm must be a number"})
          (resp/status 400))
      (= capo-val ::invalid)
      (-> (json-response {:error "capo must be a number"})
          (resp/status 400))
      :else
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
  "Return a JSON with date, venue, and setlist of the most recent gig."
  []
  (json-response (last-gig-date-data)))

(defn last-gig-date-data
  "Return date, venue, and setlist for the most recent gig."
  []
  (let [rows (exec-raw
              ["with latest as (
                  select p.id, p.performancedate, p.venue
                  from performances p
                  order by p.performancedate desc, p.created_at desc, p.id desc
                  limit 1
                )
                select l.performancedate, l.venue, sp.song_id, sp.setlistposition
                from latest l
                left join song_performances sp on sp.performance_id = l.id
                order by sp.setlistposition asc nulls last"]
              :results)
        base (first rows)
        payload (if base
                  {:lastGigDate (str (:performancedate base))
                   :lastGigVenue (:venue base)
                   :lastGigSetlist (->> rows
                                        (map :song_id)
                                        (remove nil?)
                                        vec)}
                  {:lastGigDate ""
                   :lastGigVenue nil
                   :lastGigSetlist []})]
    payload))

(defn live-gigs-by-year-data
  "Return yearly gig counts and estimated live minutes."
  []
  (let [rows (exec-raw
              ["select extract(year from p.performancedate)::int as year,\n                      count(distinct p.id)::int as gigs,\n                      coalesce(ceil(sum(case\n               when sp.song_id is null then 0\n               else coalesce(extract(epoch from s.length), 240)\n               end) / 60.0)::int, 0) as estimated_minutes\n               from performances p\n               left join song_performances sp on sp.performance_id = p.id\n               left join songs s on s.title = sp.song_id\n               group by year\n               order by year desc"]
              :results)]
    (vec rows)))

(defn live-gigs-by-year
  "Return yearly gig counts and estimated live minutes"
  []
  (json-response (live-gigs-by-year-data)))

(defn sessions-by-year-data
  "Return yearly session counts and minute totals."
  []
  (let [rows (exec-raw
              ["select extract(year from session_date)::int as year,\n                      count(*)::int as sessions,\n                      coalesce(sum(minimum_minutes)::int, 0) as minimum_minutes,\n                      coalesce(sum(actual_minutes)::int, 0) as actual_minutes,\n                      coalesce(sum(effective_minutes)::int, 0) as effective_minutes,\n                      coalesce(sum(case\n                                     when session_type in ('solo_practice', 'singing_lesson') then effective_minutes\n                                     else 0\n                                   end)::int, 0) as practice_minutes,\n                      coalesce(sum(case\n                                     when session_type = 'performance' then effective_minutes\n                                     else 0\n                                   end)::int, 0) as performance_minutes\n               from view_recent_sessions\n               group by year\n               order by year desc"]
              :results)]
    (vec rows)))

(defn sessions-by-year
  "Return yearly session counts and minimum/actual minutes across all sessions"
  []
  (json-response (sessions-by-year-data)))

(defn home-dashboard
  "Canonical home payload so frontend can render from one contract."
  []
  (json-response
   {:last_gig (last-gig-date-data)
    :home_next_actions (home-next-actions-data)
    :recent_sessions (recent-sessions-data)
    :live_gigs_by_year (live-gigs-by-year-data)
    :sessions_by_year (sessions-by-year-data)
    :home_metrics (home-metrics-data)}))

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
  (GET "/home-next-actions" [] (home-next-actions))
  (GET "/home-dashboard" [] (home-dashboard))
  (GET "/home-metrics/local" [] (home-metrics-local))
  (GET "/home-metrics/outreach" [] (home-metrics-outreach))
  (GET "/home-metrics" [] (home-metrics))
  (PUT "/home-goals/:goal-key" [goal-key :as request] (update-home-goal goal-key request))
  (GET "/home-x/local" [] (home-x-local))
  (GET "/home-x/outreach" [] (home-x-outreach))
  (GET "/home-x" [] (home-x))
  (PUT "/home-x-goals/:goal-key" [goal-key :as request] (update-home-x-goal goal-key request))
  (GET "/recent-sessions" [] (recent-sessions))
  (GET "/next-active-songs" [] (next-active-songs))
  (GET "/performances" [] (all-performances))
  (GET "/performances-with-setlists" [] (performances-with-setlists))
  (GET "/venues/:venuename/performances" [venuename]
       (venue-performances-with-setlists venuename))
  (GET "/instruments" [] (all-instruments))
  (POST "/instruments" request (create-instrument request))
  (PUT "/instruments/:id" [id :as request] (update-instrument id request))
  (DELETE "/instruments/:id" [id] (delete-instrument id))
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
  (GET "/london-heatmap" [] (london-heatmap))
  (PUT "/venues/:venuename" [venuename :as request] (update-venue venuename request))
  (DELETE "/venues/:venuename" [venuename] (delete-venue venuename))
  (GET "/view-song-plays" [] (view-song-plays))
  (GET "/view-song-plays-frequencies" [] (view-song-plays-frequencies))
  (GET "/song-performance-dates" [] (song-performance-dates))
  (GET "/visualiser" [] (visualiser))
  (GET "/last-gig-date" [] (last-gig-date))
  (GET "/live-gigs-by-year" [] (live-gigs-by-year))
  (GET "/sessions-by-year" [] (sessions-by-year))
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
                                 #"http://localhost:3100"
                                 #"http://localhost:3101"
                                 #"http://192.168.178.20:3001"
                                 #"http://192.168.0.6:3000"
                                 #"http://pallas.herokuapp.com"
                                 #"https://pallas.herokuapp.com"]
   :access-control-allow-methods [:get :put :post :delete]
   :access-control-allow-credentials "true"))
