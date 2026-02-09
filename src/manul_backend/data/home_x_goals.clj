(ns manul-backend.data.home-x-goals
  (:require [korma.core :as korma]))

(def default-goals
  {:gigs_lifetime 200
   :practice_hours_lifetime 2000
   :originals_live_lifetime 120
   :direct_outreach_lifetime 3000})

(defprotocol HomeXGoalsStore
  (fetch-home-x-goals [store])
  (update-home-x-goal [store goal-key target-value]))

(defrecord DbHomeXGoalsStore []
  HomeXGoalsStore
  (fetch-home-x-goals [_]
    (let [rows (korma/exec-raw
                ["select goal_key, target_value from home_x_goals"]
                :results)]
      (merge default-goals
             (into {}
                   (map (fn [row]
                          [(keyword (:goal_key row))
                           (int (:target_value row))])
                        rows)))))
  (update-home-x-goal [_ goal-key target-value]
    (first
     (korma/exec-raw
      ["insert into home_x_goals (goal_key, target_value)
        values (?, ?)
        on conflict (goal_key)
        do update set target_value = excluded.target_value
        returning goal_key, target_value"
       [goal-key target-value]]
      :results))))

(def db-store (->DbHomeXGoalsStore))

(defn fetch-home-x-goals-from
  [store]
  (fetch-home-x-goals store))

(defn update-home-x-goal-from
  [store goal-key target-value]
  (update-home-x-goal store goal-key target-value))
