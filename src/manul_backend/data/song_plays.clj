(ns manul-backend.data.song-plays
  (:require [korma.core :as korma]))

(defprotocol SongPlaysStore
  (next-songs-to-play-anywhere [store])
  (next-live-songs-by-frecency [store limit])
  (next-practice-songs-by-frecency [store limit]))

(def mirror-you-priority-cte
  "mirror_you_songs as (
     select distinct asg.song_title as song_id
     from album_songs asg
     join albums a on a.id = asg.album_id
     where a.title = 'Mirror You'
   )")

(defn overdue-frecency-query
  [events-cte last-played-col]
  (str
   "with "
   events-cte
   ", play_stats as (
      select song_id,
             count(*)::bigint as count,
             max(played_on) as "
   last-played-col
   "
      from play_events
      group by song_id
    ),
    "
   mirror-you-priority-cte
   "
    select
      s.title as song_id,
      s.title as title,
      coalesce(ps.count, 0)::bigint as count,
      ps."
   last-played-col
   " as "
   last-played-col
   ",
      (mys.song_id is not null) as mirror_you,
      (
        (case
           when ps."
   last-played-col
   " is null then 10000
           else greatest(0, current_date - ps."
   last-played-col
   ")
         end)
        + (1000.0 / (1 + coalesce(ps.count, 0)))
      ) as overdue_frecency
    from songs s
    left join play_stats ps on ps.song_id = s.title
    left join mirror_you_songs mys on mys.song_id = s.title
    where s.active = true
    order by mirror_you desc, overdue_frecency desc, s.title asc
    limit ?"))

(defrecord DbSongPlaysStore []
  SongPlaysStore
  (next-songs-to-play-anywhere [_]
    (korma/exec-raw
     ["select * from view_next_songs_to_play_anywhere"]
     :results))
  (next-live-songs-by-frecency [_ limit]
    (korma/exec-raw
     [(overdue-frecency-query
       "play_events as (
          select sp.song_id::text as song_id,
                 p.performancedate::date as played_on
          from song_performances sp
          join performances p on p.id = sp.performance_id
        )"
       "last_played")
      [limit]]
     :results))
  (next-practice-songs-by-frecency [_ limit]
    (korma/exec-raw
     [(overdue-frecency-query
       "play_events as (
          select spe.song_id::text as song_id,
                 spe.played_on::date as played_on
          from view_song_play_events spe
        )"
       "last_played_anywhere")
      [limit]]
     :results)))

(def db-store (->DbSongPlaysStore))

(defn fetch-next-songs-to-play-anywhere
  [store]
  (next-songs-to-play-anywhere store))

(defn fetch-next-live-songs-by-frecency
  [store limit]
  (next-live-songs-by-frecency store limit))

(defn fetch-next-practice-songs-by-frecency
  [store limit]
  (next-practice-songs-by-frecency store limit))
