(ns manul-backend.data-test
  (:require [clojure.test :refer :all]
            [manul-backend.data.recent-sessions :as recent-sessions]
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
        (is (re-find #"order by session_date desc, session_created_at desc" (:sql @captured)))
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
