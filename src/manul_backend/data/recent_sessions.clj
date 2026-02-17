(ns manul-backend.data.recent-sessions
  (:require [korma.core :as korma]))

(defprotocol RecentSessionsStore
  (fetch-recent-sessions [store limit]))

(defrecord DbRecentSessionsStore []
  RecentSessionsStore
  (fetch-recent-sessions [_ limit]
    (korma/exec-raw
     ["select session_type, session_id, session_date, session_occurred_at, session_created_at,
              session_label, song_count, minimum_minutes, actual_minutes, effective_minutes
       from view_recent_sessions
       order by session_date desc, session_occurred_at desc, session_created_at desc, session_id desc
       limit ?"
      [limit]]
     :results)))

(def db-store (->DbRecentSessionsStore))

(defn fetch-recent-sessions-from
  [store limit]
  (fetch-recent-sessions store limit))
