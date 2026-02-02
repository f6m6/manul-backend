(ns manul-backend.data.song-plays
  (:require [korma.core :as korma]))

(defprotocol SongPlaysStore
  (next-songs-to-play-anywhere [store]))

(defrecord DbSongPlaysStore []
  SongPlaysStore
  (next-songs-to-play-anywhere [_]
    (korma/exec-raw
     ["select * from view_next_songs_to_play_anywhere"]
     :results)))

(def db-store (->DbSongPlaysStore))

(defn fetch-next-songs-to-play-anywhere
  [store]
  (next-songs-to-play-anywhere store))
