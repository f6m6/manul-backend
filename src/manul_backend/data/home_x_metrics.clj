(ns manul-backend.data.home-x-metrics
  (:require [korma.core :as korma]))

(defprotocol HomeXMetricsStore
  (fetch-home-x-metrics [store]))

(defrecord DbHomeXMetricsStore []
  HomeXMetricsStore
  (fetch-home-x-metrics [_]
    (first
     (korma/exec-raw
      ["select gigs_ytd,
               gigs_lifetime,
               solo_practice_minutes_weekly,
               practice_minutes_ytd,
               practice_minutes_lifetime,
               songs_performed_live_ytd,
               songs_performed_live_lifetime,
               sessions_ytd
        from view_home_x_metrics"]
      :results))))

(def db-store (->DbHomeXMetricsStore))

(defn fetch-home-x-metrics-from
  [store]
  (or (fetch-home-x-metrics store) {}))
