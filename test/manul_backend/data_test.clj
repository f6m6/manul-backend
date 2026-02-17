(ns manul-backend.data-test
  (:require [clojure.test :refer :all]
            [manul-backend.data.recent-sessions :as recent-sessions]
            [manul-backend.data.home-x-metrics :as home-x-metrics]
            [manul-backend.data.song-plays :as song-plays]
            [korma.core :as korma]))

(deftest fetch-recent-sessions-from-orders-by-session-date
  (let [captured (atom nil)]
    (with-redefs [korma/exec-raw (fn [& args]
                                   (let [[sql params] (if (vector? (first args))
                                                        (first args)
                                                        (second args))]
                                     (reset! captured {:sql sql :params params})
                                     [{:session_type "solo_practice"
                                       :session_id 1
                                       :session_date (java.sql.Date/valueOf "2026-02-03")
                                       :session_created_at (java.sql.Timestamp/valueOf
                                                            "2026-02-03 10:30:00")
                                       :session_label nil
                                       :song_count 2
                                       :estimated_minutes 15}]))]
      (let [rows (recent-sessions/fetch-recent-sessions-from recent-sessions/db-store 5)]
        (is (= 5 (first (:params @captured))))
        (is (re-find #"from view_recent_sessions" (:sql @captured)))
        (is (re-find #"order by session_date desc, session_occurred_at desc, session_created_at desc" (:sql @captured)))
        (is (= 1 (:session_id (first rows))))))))

(deftest fetch-next-songs-to-play-anywhere-uses-view
  (let [captured (atom nil)]
    (with-redefs [korma/exec-raw (fn [& args]
                                   (let [[sql] (if (vector? (first args))
                                                 (first args)
                                                 (second args))]
                                     (reset! captured sql)
                                     [{:song_id "Song A"}]))]
      (let [rows (song-plays/fetch-next-songs-to-play-anywhere song-plays/db-store)]
        (is (re-find #"view_next_songs_to_play_anywhere" @captured))
        (is (= "Song A" (:song_id (first rows))))))))

(deftest fetch-next-live-songs-by-frecency-uses-active-mirror-priority
  (let [captured (atom nil)]
    (with-redefs [korma/exec-raw (fn [& args]
                                   (let [[sql params] (if (vector? (first args))
                                                         (first args)
                                                         (second args))]
                                     (reset! captured {:sql sql :params params})
                                     [{:song_id "Song A"}]))]
      (let [rows (song-plays/fetch-next-live-songs-by-frecency song-plays/db-store 3)]
        (is (re-find #"from song_performances" (:sql @captured)))
        (is (re-find #"where s.active = true" (:sql @captured)))
        (is (re-find #"a.title = 'Mirror You'" (:sql @captured)))
        (is (re-find #"order by mirror_you desc, overdue_frecency desc" (:sql @captured)))
        (is (= [3] (:params @captured)))
        (is (= "Song A" (:song_id (first rows))))))))

(deftest fetch-next-practice-songs-by-frecency-uses-play-events-view
  (let [captured (atom nil)]
    (with-redefs [korma/exec-raw (fn [& args]
                                   (let [[sql params] (if (vector? (first args))
                                                         (first args)
                                                         (second args))]
                                     (reset! captured {:sql sql :params params})
                                     [{:song_id "Song B"}]))]
      (let [rows (song-plays/fetch-next-practice-songs-by-frecency song-plays/db-store 3)]
        (is (re-find #"from view_song_play_events" (:sql @captured)))
        (is (re-find #"last_played_anywhere" (:sql @captured)))
        (is (= [3] (:params @captured)))
        (is (= "Song B" (:song_id (first rows))))))))

(deftest fetch-home-x-metrics-uses-view
  (let [captured (atom nil)]
    (with-redefs [korma/exec-raw (fn [& args]
                                   (let [[sql] (if (vector? (first args))
                                                 (first args)
                                                 (second args))]
                                     (reset! captured sql)
                                     [{:gigs_ytd 3
                                       :gigs_lifetime 83
                                       :solo_practice_minutes_weekly 95
                                       :practice_minutes_ytd 318
                                       :practice_minutes_lifetime 2400
                                       :songs_performed_live_ytd 12
                                       :songs_performed_live_lifetime 57
                                       :sessions_ytd 10}]))]
      (let [row (home-x-metrics/fetch-home-x-metrics-from home-x-metrics/db-store)]
        (is (re-find #"from view_home_x_metrics" @captured))
        (is (= 83 (:gigs_lifetime row)))
        (is (= 95 (:solo_practice_minutes_weekly row)))))))
