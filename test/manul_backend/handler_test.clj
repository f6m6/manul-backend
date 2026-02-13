(ns manul-backend.handler-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [clojure.data.json :as json]
            [korma.core :as korma]
            [manul-backend.data.recent-sessions :as recent-sessions]
            [manul-backend.data.song-plays :as song-plays]
            [manul-backend.data.direct-fan-outreach :as direct-fan-outreach]
            [manul-backend.data.home-x-metrics :as home-x-metrics]
            [manul-backend.data.home-x-goals :as home-x-goals]
            [manul-backend.handler :refer :all]))

(deftest create-performance-requires-venue-and-songs
  (let [response (app (-> (mock/request :post "/create-performance" "{}")
                          (mock/content-type "application/json")))]
    (is (= 400 (:status response)))
    (is (re-find #"venue and songs are required" (:body response)))))

(deftest last-gig-date-returns-details
  (with-redefs [korma/exec-raw (fn [& _]
                                 [{:performancedate (java.sql.Date/valueOf "2026-02-01")
                                   :venue "The Dignity"
                                   :song_id "Song A"
                                   :setlistposition 1}
                                  {:performancedate (java.sql.Date/valueOf "2026-02-01")
                                   :venue "The Dignity"
                                   :song_id "Song B"
                                   :setlistposition 2}])]
    (let [response (last-gig-date)
          body (json/read-str (:body response) :key-fn keyword)]
      (is (= 200 (:status response)))
      (is (= "2026-02-01" (:lastGigDate body)))
      (is (= "The Dignity" (:lastGigVenue body)))
      (is (= ["Song A" "Song B"] (:lastGigSetlist body))))))

(deftest create-performance-uses-id-from-insert
  (let [song-perf-insert (atom nil)
        performance-insert (atom nil)]
    (with-redefs [with-transaction (fn [f] (f))
                  korma/exec-raw (fn [& args]
                                   (let [[sql params with-results?] (if (vector? (first args))
                                                                      [(first args) (second (first args)) (second args)]
                                                                      [(second args) (second (second args)) (nth args 2 nil)])]
                                     (cond
                                       (re-find #"insert into performances" (first sql)) (do
                                                                                           (reset! performance-insert {:sql sql :params params})
                                                                                           [{:id 123}])
                                       (re-find #"insert into song_performances" (first sql)) (do
                                                                                                (reset! song-perf-insert sql)
                                                                                                :ok)
                                       :else :ok)))]
      (let [body (json/write-str {:venue "The Place" :songs ["Song A" "Song B"]})
            response (create-performance (-> (mock/request :post "/create-performance" body)
                                             (mock/content-type "application/json")))
            response-body (json/read-str (:body response) :key-fn keyword)]
        (is (= 200 (:status response)))
        (is (re-find #"insert into performances" (first (:sql @performance-insert))))
        (is (= 0 (nth (:params @performance-insert) 4)))
        (is (= "open_mic" (nth (:params @performance-insert) 5)))
        (is (= 123 (second (second @song-perf-insert))))
        (is (= 123 (:performanceId response-body)))
        (is (= 2 (:songs response-body)))))))

(deftest create-performance-allows-fee-and-showcase-type
  (let [performance-insert (atom nil)]
    (with-redefs [with-transaction (fn [f] (f))
                  korma/exec-raw (fn [& args]
                                   (let [[sql params with-results?] (if (vector? (first args))
                                                                      [(first args) (second (first args)) (second args)]
                                                                      [(second args) (second (second args)) (nth args 2 nil)])]
                                     (cond
                                       (re-find #"insert into performances" (first sql)) (do
                                                                                           (reset! performance-insert {:sql sql :params params})
                                                                                           [{:id 77}])
                                       (re-find #"insert into song_performances" (first sql)) :ok
                                       :else :ok)))]
      (let [body (json/write-str {:venue "The Joint"
                                  :songs ["Song A"]
                                  :gig_type "showcase"
                                  :fee_micro_gbp 0})
            response (create-performance (-> (mock/request :post "/create-performance" body)
                                             (mock/content-type "application/json")))
            response-body (json/read-str (:body response) :key-fn keyword)]
        (is (= 200 (:status response)))
        (is (= 77 (:performanceId response-body)))
        (is (= 0 (nth (:params @performance-insert) 4)))
        (is (= "showcase" (nth (:params @performance-insert) 5)))))))

(deftest create-performance-defaults-gig-type-when-blank
  (let [performance-insert (atom nil)]
    (with-redefs [with-transaction (fn [f] (f))
                  korma/exec-raw (fn [& args]
                                   (let [[sql params with-results?] (if (vector? (first args))
                                                                      [(first args) (second (first args)) (second args)]
                                                                      [(second args) (second (second args)) (nth args 2 nil)])]
                                     (cond
                                       (re-find #"insert into performances" (first sql)) (do
                                                                                           (reset! performance-insert {:sql sql :params params})
                                                                                           [{:id 88}])
                                       (re-find #"insert into song_performances" (first sql)) :ok
                                       :else :ok)))]
      (let [body (json/write-str {:venue "The Place" :songs ["Song A"] :gig_type "   "})
            response (create-performance (-> (mock/request :post "/create-performance" body)
                                             (mock/content-type "application/json")))
            response-body (json/read-str (:body response) :key-fn keyword)]
        (is (= 200 (:status response)))
        (is (= 88 (:performanceId response-body)))
        (is (= "open_mic" (nth (:params @performance-insert) 5)))))))

(deftest create-performance-normalizes-fee
  (let [performance-insert (atom nil)]
    (with-redefs [with-transaction (fn [f] (f))
                  korma/exec-raw (fn [& args]
                                   (let [[sql params with-results?] (if (vector? (first args))
                                                                      [(first args) (second (first args)) (second args)]
                                                                      [(second args) (second (second args)) (nth args 2 nil)])]
                                     (cond
                                       (re-find #"insert into performances" (first sql)) (do
                                                                                           (reset! performance-insert {:sql sql :params params})
                                                                                           [{:id 77}])
                                       (re-find #"insert into song_performances" (first sql)) :ok
                                       :else :ok)))]
      (let [body (json/write-str {:venue "The Place" :songs ["Song A"] :gig_type "open_mic" :fee_micro_gbp "0"})
            response (create-performance (-> (mock/request :post "/create-performance" body)
                                             (mock/content-type "application/json")))]
        (is (= 200 (:status response)))
        (is (= 0 (nth (:params @performance-insert) 4)))))))

(deftest update-performance-normalizes-fee
  (let [performance-update (atom nil)]
    (with-redefs [korma/exec-raw (fn [& args]
                                   (let [[sql params] (if (vector? (first args))
                                                       (first args)
                                                       (second args))]
                                     (when (re-find #"update performances" sql)
                                       (reset! performance-update {:sql sql :params params}))
                                     [{:id 1
                                       :performancedate "2026-02-01"
                                       :venue "The Place"
                                       :free false
                                       :openmic true
                                       :fee_micro_gbp 0
                                       :gig_type "open_mic"}]))]
      (let [body (json/write-str {:venue "The Place" :date "2026-02-01" :gig_type "open_mic" :fee_micro_gbp "0"})
            response (update-performance "1" (-> (mock/request :put "/performances/1" body)
                                                (mock/content-type "application/json")))]
        (is (= 200 (:status response)))
        (is (= 0 (nth (:params @performance-update) 4)))))))

(deftest update-performance-rejects-invalid-fee
  (with-redefs [korma/exec-raw (fn [& _] :ok)]
    (let [body (json/write-str {:venue "The Place" :date "2026-02-01" :gig_type "open_mic" :fee_micro_gbp "abc"})
          response (update-performance "1" (-> (mock/request :put "/performances/1" body)
                                              (mock/content-type "application/json")))]
      (is (= 400 (:status response)))
      (is (re-find #"fee_micro_gbp is invalid" (:body response))))))

(deftest create-performance-rejects-invalid-gig-type
  (let [body (json/write-str {:venue "The Place" :songs ["Song A"] :gig_type "invalid_type"})
        response (create-performance (-> (mock/request :post "/create-performance" body)
                                         (mock/content-type "application/json")))]
    (is (= 400 (:status response)))
    (is (re-find #"gig_type is invalid" (:body response)))))

(deftest create-performance-trims-gig-type
  (let [performance-insert (atom nil)]
    (with-redefs [with-transaction (fn [f] (f))
                  korma/exec-raw (fn [& args]
                                   (let [[sql params with-results?] (if (vector? (first args))
                                                                      [(first args) (second (first args)) (second args)]
                                                                      [(second args) (second (second args)) (nth args 2 nil)])]
                                     (cond
                                       (re-find #"insert into performances" (first sql)) (do
                                                                                           (reset! performance-insert {:sql sql :params params})
                                                                                           [{:id 55}])
                                       (re-find #"insert into song_performances" (first sql)) :ok
                                       :else :ok)))]
      (let [body (json/write-str {:venue "The Place" :songs ["Song A"] :gig_type "  open_mic  "})
            response (create-performance (-> (mock/request :post "/create-performance" body)
                                             (mock/content-type "application/json")))
            response-body (json/read-str (:body response) :key-fn keyword)]
        (is (= 200 (:status response)))
        (is (= 55 (:performanceId response-body)))
        (is (= "open_mic" (nth (:params @performance-insert) 5)))))))

(deftest create-performance-requires-nonblank-song-titles
  (let [body (json/write-str {:venue "The Place" :songs [" " "\t"]})
        response (create-performance (-> (mock/request :post "/create-performance" body)
                                         (mock/content-type "application/json")))]
    (is (= 400 (:status response)))
    (is (re-find #"venue and songs are required" (:body response)))))

(deftest create-performance-rejects-non-string-song
  (let [body (json/write-str {:venue "The Place" :songs [123]})
        response (create-performance (-> (mock/request :post "/create-performance" body)
                                         (mock/content-type "application/json")))]
    (is (= 400 (:status response)))
    (is (re-find #"venue and songs are required" (:body response)))))

(deftest create-performance-rejects-invalid-gig-type-before-db
  (with-redefs [with-transaction (fn [_] (throw (ex-info "should not hit db" {})))]
    (let [body (json/write-str {:venue "The Place" :date "2026-02-01" :songs ["Song A"] :gig_type "invalid_type"})
          response (create-performance (-> (mock/request :post "/create-performance" body)
                                           (mock/content-type "application/json")))]
      (is (= 400 (:status response)))
      (is (re-find #"gig_type is invalid" (:body response))))))

(deftest create-performance-requires-nonblank-venue
  (let [body (json/write-str {:venue "   " :songs ["Song A"]})
        response (create-performance (-> (mock/request :post "/create-performance" body)
                                         (mock/content-type "application/json")))]
    (is (= 400 (:status response)))
    (is (re-find #"venue and songs are required" (:body response)))))

(deftest update-performance-rejects-invalid-gig-type
  (with-redefs [korma/exec-raw (fn [& _] [])]
    (let [body (json/write-str {:venue "The Place" :date "2026-02-01" :gig_type "invalid_type"})
          response (update-performance "42" (-> (mock/request :put "/performances/42" body)
                                               (mock/content-type "application/json")))]
      (is (= 400 (:status response)))
      (is (re-find #"gig_type is invalid" (:body response))))))

(deftest update-performance-rejects-invalid-gig-type-before-db
  (with-redefs [with-transaction (fn [_] (throw (ex-info "should not hit db" {})))]
    (let [body (json/write-str {:venue "The Place" :date "2026-02-01" :gig_type "invalid_type"})
          response (update-performance "42" (-> (mock/request :put "/performances/42" body)
                                               (mock/content-type "application/json")))]
      (is (= 400 (:status response)))
      (is (re-find #"gig_type is invalid" (:body response))))))

(deftest update-performance-requires-venue-and-date
  (let [body (json/write-str {:venue "   " :date "" :gig_type "open_mic"})
        response (update-performance "42" (-> (mock/request :put "/performances/42" body)
                                             (mock/content-type "application/json")))]
    (is (= 400 (:status response)))
    (is (re-find #"venue and date are required" (:body response)))))

(deftest update-performance-trims-venue
  (let [calls (atom [])]
    (with-redefs [korma/exec-raw (fn [& args]
                                   (let [[sql params] (if (vector? (first args))
                                                       (first args)
                                                       (second args))]
                                     (swap! calls conj {:sql sql :params params})
                                     [{:id 1
                                       :performancedate "2026-02-01"
                                       :venue "The Place"
                                       :free false
                                       :openmic true
                                       :fee_micro_gbp 0
                                       :gig_type "open_mic"}]))]
      (let [body (json/write-str {:venue "  The Place  " :date "2026-02-01" :gig_type "open_mic"})
            response (update-performance "1" (-> (mock/request :put "/performances/1" body)
                                                (mock/content-type "application/json")))]
        (is (= 200 (:status response)))
        (let [update (first @calls)]
          (is (= "The Place" (nth (:params update) 1))))))))

(deftest update-performance-trims-gig-type
  (let [calls (atom [])]
    (with-redefs [korma/exec-raw (fn [& args]
                                   (let [[sql params] (if (vector? (first args))
                                                       (first args)
                                                       (second args))]
                                     (swap! calls conj {:sql sql :params params})
                                     [{:id 1
                                       :performancedate "2026-02-01"
                                       :venue "The Place"
                                       :free false
                                       :openmic true
                                       :fee_micro_gbp 0
                                       :gig_type "open_mic"}]))]
      (let [body (json/write-str {:venue "The Place" :date "2026-02-01" :gig_type "  open_mic  "})
            response (update-performance "1" (-> (mock/request :put "/performances/1" body)
                                                (mock/content-type "application/json")))]
        (is (= 200 (:status response)))
        (let [update (first @calls)]
          (is (= "open_mic" (nth (:params update) 5))))))))

(deftest update-performance-returns-404-when-missing
  (with-redefs [korma/exec-raw (fn [& _] [])]
    (let [body (json/write-str {:venue "The Place" :date "2026-02-01" :gig_type "open_mic"})
          response (update-performance "999" (-> (mock/request :put "/performances/999" body)
                                                 (mock/content-type "application/json")))]
      (is (= 404 (:status response)))
      (is (re-find #"performance not found" (:body response))))))

(deftest update-performance-defaults-gig-type-when-omitted
  (let [performance-update (atom nil)]
    (with-redefs [korma/exec-raw (fn [& args]
                                   (let [[sql params] (if (vector? (first args))
                                                       (first args)
                                                       (second args))]
                                     (when (re-find #"update performances" sql)
                                       (reset! performance-update {:sql sql :params params}))
                                     [{:id 1
                                       :performancedate "2026-02-01"
                                       :venue "The Place"
                                       :free false
                                       :openmic true
                                       :fee_micro_gbp 0
                                       :gig_type :open_mic}]))]
      (let [body (json/write-str {:venue "The Place" :date "2026-02-01"})
            response (update-performance "1" (-> (mock/request :put "/performances/1" body)
                                                (mock/content-type "application/json")))]
        (is (= 200 (:status response)))
        (is (= true (nth (:params @performance-update) 2)))
        (is (= true (nth (:params @performance-update) 3)))
        (is (= "open_mic" (nth (:params @performance-update) 5)))))))

(deftest update-performance-non-numeric-id-throws
  (let [body (json/write-str {:venue "The Place" :date "2026-02-01" :gig_type "open_mic"})]
    (is (thrown? NumberFormatException
                 (update-performance "abc" (-> (mock/request :put "/performances/abc" body)
                                              (mock/content-type "application/json")))))))

(deftest album-tracks-non-numeric-id-throws
  (is (thrown? NumberFormatException (album-tracks "abc"))))

(deftest create-song-invalid-json-throws
  (is (thrown? Exception
               (create-song (-> (mock/request :post "/create-song" "{")
                                (mock/content-type "application/json"))))))

(deftest update-song-defaults-boolean-flags-when-omitted
  (let [song-update (atom nil)]
    (with-redefs [with-transaction (fn [f] (f))
                  korma/exec-raw (fn [& args]
                                   (let [[sql params] (if (vector? (first args))
                                                       (first args)
                                                       (second args))]
                                     (cond
                                       (re-find #"update songs" sql) (do
                                                                       (reset! song-update {:sql sql :params params})
                                                                       [{:title "Song A"
                                                                         :cover false
                                                                         :active true
                                                                         :key nil
                                                                         :length nil
                                                                         :instrumental false
                                                                         :artist nil
                                                                         :bpm nil
                                                                         :recorded_key nil
                                                                         :my_live_key nil
                                                                         :capo nil}])
                                       :else :ok)))]
      (let [body (json/write-str {:artist "Artist"})
            response (update-song "Song A" (-> (mock/request :put "/songs/Song%20A" body)
                                               (mock/content-type "application/json")))]
        (is (= 200 (:status response)))
        (is (= false (nth (:params @song-update) 0)))
        (is (= true (nth (:params @song-update) 1)))
        (is (= false (nth (:params @song-update) 4)))))))

(deftest create-venue-requires-name
  (let [response (app (-> (mock/request :post "/create-venue" "{}")
                          (mock/content-type "application/json")))]
    (is (= 400 (:status response)))
    (is (re-find #"venuename is required" (:body response)))))

(deftest create-venue-rejects-blank-name
  (let [body (json/write-str {:venuename "   "})
        response (create-venue (-> (mock/request :post "/create-venue" body)
                                   (mock/content-type "application/json")))]
    (is (= 400 (:status response)))
    (is (re-find #"venuename is required" (:body response)))))

(deftest create-venue-trims-and-inserts
  (let [inserted (atom nil)]
    (with-redefs [korma/exec-raw (fn [& args]
                                   (let [[sql params] (if (vector? (first args))
                                                       (first args)
                                                       (second args))]
                                     (reset! inserted {:sql sql :params params})
                                     :ok))]
      (let [body (json/write-str {:venuename "  The Place  " :postcode "AB12"})
            response (create-venue (-> (mock/request :post "/create-venue" body)
                                       (mock/content-type "application/json")))
            response-body (json/read-str (:body response) :key-fn keyword)]
        (is (= 200 (:status response)))
        (is (re-find #"insert into venues" (:sql @inserted)))
        (is (= ["The Place" "AB12"] (:params @inserted)))
        (is (= "The Place" (:venuename response-body)))))))

(deftest update-venue-trims-name
  (with-redefs [korma/exec-raw (fn [& args]
                                 (let [[sql params] (if (vector? (first args))
                                                     (first args)
                                                     (second args))]
                                   [{:venuename (second params) :postcode "AB12"}]))]
    (let [body (json/write-str {:venuename "  The Place  " :postcode "AB12"})
          response (update-venue "  The Place  " (-> (mock/request :put "/venues/The%20Place" body)
                                                    (mock/content-type "application/json")))
          response-body (json/read-str (:body response) :key-fn keyword)]
      (is (= 200 (:status response)))
      (is (= "The Place" (:venuename response-body)))
      (is (= "AB12" (:postcode response-body))))))

(deftest create-song-requires-title
  (let [response (app (-> (mock/request :post "/create-song" "{}")
                          (mock/content-type "application/json")))]
    (is (= 400 (:status response)))
    (is (re-find #"title is required" (:body response)))))

(deftest performances-with-setlists-nests-rows
  (with-redefs [korma/exec-raw (fn [& _]
                                 [{:id 2
                                  :performancedate "2026-01-29"
                                   :venue "The Dignity"
                                 :free true
                                 :openmic true
                                 :gig_type "open_mic"
                                  :setlistposition 1
                                   :song_id "Song A"
                                   :length nil}
                                  {:id 2
                                   :performancedate "2026-01-29"
                                   :venue "The Dignity"
                                   :free true
                                   :openmic true
                                   :gig_type "open_mic"
                                   :setlistposition 2
                                   :song_id "Song B"
                                   :length nil}
                                  {:id 1
                                   :performancedate "2026-01-28"
                                   :venue "The Hideaway"
                                   :free false
                                   :openmic false
                                   :gig_type "booked"
                                   :setlistposition nil
                                   :song_id nil
                                   :length nil}])]
    (let [response (performances-with-setlists)
          body (json/read-str (:body response) :key-fn keyword)]
      (is (= 2 (count body)))
      (is (= 2 (:id (first body))))
      (is (= "The Dignity" (:venue (first body))))
      (is (= 8 (:estimated_time_minutes (first body))))
      (is (= [{:position 1 :song_id "Song A"}
              {:position 2 :song_id "Song B"}]
             (:setlist (first body))))
      (is (= 1 (:id (second body))))
      (is (= [] (:setlist (second body)))))))

(deftest practice-sessions-with-songs-adds-estimated-time
  (with-redefs [korma/exec-raw (fn [& _]
                                 [{:id 1
                                   :practiced_on "2026-02-01"
                                   :total_minutes 20
                                   :position 1
                                   :song_title "Song A"
                                   :minutes 10
                                   :length nil}
                                  {:id 1
                                   :practiced_on "2026-02-01"
                                   :total_minutes 20
                                   :position 2
                                   :song_title "Song B"
                                   :minutes 10
                                   :length nil}])]
    (let [response (practice-sessions-with-songs)
          body (json/read-str (:body response) :key-fn keyword)]
      (is (= 1 (count body)))
      (is (= 8 (:estimated_time_minutes (first body)))))))

(deftest practice-sessions-query-has-no-plus
  (let [captured (atom nil)]
    (with-redefs [korma/exec-raw (fn [& args]
                                   (let [[sql] (if (vector? (first args))
                                                 (first args)
                                                 (second args))]
                                     (reset! captured sql)
                                     []))]
      (practice-sessions-with-songs)
      (is (re-find #"left join songs" @captured))
      (is (re-find #"song_id" @captured))
      (is (not (re-find #"\\+\\s*left join" @captured))))))

(deftest singing-lessons-with-songs-returns-rows
  (with-redefs [korma/exec-raw (fn [& _]
                                 [{:id 1
                                   :lesson_date "2026-02-01"
                                   :duration_minutes 120
                                   :position 1
                                   :song_title "Song A"}
                                  {:id 1
                                   :lesson_date "2026-02-01"
                                   :duration_minutes 120
                                   :position 2
                                   :song_title "Song B"}])]
    (let [response (singing-lessons-with-songs)
          body (json/read-str (:body response) :key-fn keyword)]
      (is (= 1 (count body)))
      (is (= 2 (count (:songs (first body))))))))

(deftest singing-lessons-query-has-no-plus
  (let [captured (atom nil)]
    (with-redefs [korma/exec-raw (fn [& args]
                                   (let [[sql] (if (vector? (first args))
                                                 (first args)
                                                 (second args))]
                                     (reset! captured sql)
                                     []))]
      (singing-lessons-with-songs)
      (is (re-find #"from singing_lessons" @captured))
      (is (not (re-find #"\\+\\s*left join" @captured))))))

(deftest create-singing-lesson-inserts-songs
  (let [calls (atom [])]
    (with-redefs [with-transaction (fn [f] (f))
                  korma/exec-raw (fn [& args]
                                   (let [[sql params] (if (vector? (first args))
                                                       (first args)
                                                       (second args))]
                                     (swap! calls conj {:sql sql :params params})
                                     (if (re-find #"returning id" sql)
                                       [{:id 7}]
                                       :ok)))]
      (let [body (json/write-str {:date "2026-02-01" :songs ["Song A"]})
            response (create-singing-lesson (-> (mock/request :post "/singing-lessons" body)
                                                (mock/content-type "application/json")))]
        (is (= 200 (:status response)))
        (is (some #(re-find #"insert into singing_lessons" (:sql %)) @calls))
        (is (some #(re-find #"insert into singing_lesson_songs" (:sql %)) @calls))))))

(deftest create-singing-lesson-forces-120-minutes
  (let [lesson-insert (atom nil)]
    (with-redefs [with-transaction (fn [f] (f))
                  korma/exec-raw (fn [& args]
                                   (let [[sql params] (if (vector? (first args))
                                                       (first args)
                                                       (second args))]
                                     (when (re-find #"insert into singing_lessons" sql)
                                       (reset! lesson-insert {:sql sql :params params}))
                                     (if (re-find #"returning id" sql)
                                       [{:id 7}]
                                       :ok)))]
      (let [body (json/write-str {:date "2026-02-01" :songs ["Song A"]})
            response (create-singing-lesson (-> (mock/request :post "/singing-lessons" body)
                                                (mock/content-type "application/json")))]
        (is (= 200 (:status response)))
        (is lesson-insert)
        (is (re-find #"duration_minutes\) values \(\?, 120\)" (:sql @lesson-insert)))
        (is (= "2026-02-01" (str (first (:params @lesson-insert)))))))))

(deftest create-singing-lesson-requires-songs
  (with-redefs [with-transaction (fn [f] (f))
                korma/exec-raw (fn [& _] :ok)]
    (let [body (json/write-str {:date "2026-02-01" :songs []})
          response (create-singing-lesson (-> (mock/request :post "/singing-lessons" body)
                                              (mock/content-type "application/json")))]
      (is (= 400 (:status response)))
      (is (re-find #"songs are required" (:body response))))))

(deftest create-singing-lesson-rejects-blank-song-titles
  (with-redefs [with-transaction (fn [f] (f))
                korma/exec-raw (fn [& _] :ok)]
    (let [body (json/write-str {:date "2026-02-01" :songs [" " "\t"]})
          response (create-singing-lesson (-> (mock/request :post "/singing-lessons" body)
                                              (mock/content-type "application/json")))]
      (is (= 400 (:status response)))
      (is (re-find #"songs are required" (:body response))))))

(deftest create-singing-lesson-rejects-non-string-song
  (with-redefs [with-transaction (fn [f] (f))
                korma/exec-raw (fn [& _] :ok)]
    (let [body (json/write-str {:date "2026-02-01" :songs [123]})
          response (create-singing-lesson (-> (mock/request :post "/singing-lessons" body)
                                              (mock/content-type "application/json")))]
      (is (= 400 (:status response)))
      (is (re-find #"songs are required" (:body response))))))

(deftest update-singing-lesson-upserts-songs
  (let [calls (atom [])]
    (with-redefs [with-transaction (fn [f] (f))
                  korma/exec-raw (fn [& args]
                                   (let [[sql params] (if (vector? (first args))
                                                       (first args)
                                                       (second args))]
                                     (swap! calls conj {:sql sql :params params})
                                     (if (re-find #"update singing_lessons" sql)
                                       [{:id 42
                                         :lesson_date "2026-02-01"
                                         :duration_minutes 120}]
                                       :ok)))]
      (let [body (json/write-str {:date "2026-02-01" :songs ["Song A" "Song B"]})
            response (update-singing-lesson "42" (-> (mock/request :put "/singing-lessons/42" body)
                                                     (mock/content-type "application/json")))
            response-body (json/read-str (:body response) :key-fn keyword)]
        (is (= 200 (:status response)))
        (is (= 42 (:id response-body)))
        (is (some #(re-find #"delete from singing_lesson_songs" (:sql %)) @calls))
        (is (some #(re-find #"insert into singing_lesson_songs" (:sql %)) @calls))))))

(deftest update-singing-lesson-requires-date
  (let [body (json/write-str {:date "" :songs ["Song A"]})
        response (update-singing-lesson "42" (-> (mock/request :put "/singing-lessons/42" body)
                                                 (mock/content-type "application/json")))]
    (is (= 400 (:status response)))
    (is (re-find #"date is required" (:body response)))))

(deftest update-singing-lesson-rejects-blank-songs
  (let [body (json/write-str {:date "2026-02-01" :songs [" " "\t"]})
        response (update-singing-lesson "42" (-> (mock/request :put "/singing-lessons/42" body)
                                                 (mock/content-type "application/json")))]
    (is (= 400 (:status response)))
    (is (re-find #"songs are required" (:body response)))))

(deftest update-singing-lesson-returns-404-when-missing
  (with-redefs [with-transaction (fn [f] (f))
                korma/exec-raw (fn [& args]
                                 (let [[sql] (if (vector? (first args))
                                               (first args)
                                               (second args))]
                                   (if (re-find #"update singing_lessons" sql)
                                     []
                                     :ok)))]
    (let [body (json/write-str {:date "2026-02-01" :songs ["Song A"]})
          response (update-singing-lesson "999" (-> (mock/request :put "/singing-lessons/999" body)
                                                   (mock/content-type "application/json")))]
      (is (= 404 (:status response)))
      (is (re-find #"singing lesson not found" (:body response))))))

(deftest delete-singing-lesson-removes-row
  (with-redefs [with-transaction (fn [f] (f))
                korma/exec-raw (fn [& args]
                                 (let [[sql params] (if (vector? (first args))
                                                     (first args)
                                                     (second args))]
                                   (if (re-find #"delete from singing_lessons" sql)
                                     [{:id 9}]
                                     :ok)))]
    (let [response (delete-singing-lesson "9")
          body (json/read-str (:body response) :key-fn keyword)]
      (is (= 200 (:status response)))
      (is (= 9 (:id body))))))

(deftest delete-singing-lesson-returns-404-when-missing
  (with-redefs [with-transaction (fn [f] (f))
                korma/exec-raw (fn [& args]
                                 (let [[sql params] (if (vector? (first args))
                                                     (first args)
                                                     (second args))]
                                   (if (re-find #"delete from singing_lessons" sql)
                                     []
                                     :ok)))]
    (let [response (delete-singing-lesson "999")]
      (is (= 404 (:status response)))
      (is (re-find #"singing lesson not found" (:body response))))))

(deftest delete-practice-session-returns-404-when-missing
  (with-redefs [with-transaction (fn [f] (f))
                korma/exec-raw (fn [& args]
                                 (let [[sql params] (if (vector? (first args))
                                                     (first args)
                                                     (second args))]
                                   (if (re-find #"delete from practice_sessions" sql)
                                     []
                                     :ok)))]
    (let [response (delete-practice-session "999")]
      (is (= 404 (:status response)))
      (is (re-find #"practice session not found" (:body response))))))

(deftest delete-performance-returns-404-when-missing
  (with-redefs [with-transaction (fn [f] (f))
                korma/exec-raw (fn [& args]
                                 (let [[sql params] (if (vector? (first args))
                                                     (first args)
                                                     (second args))]
                                   (if (re-find #"delete from performances" sql)
                                     []
                                     :ok)))]
    (let [response (delete-performance "999")]
      (is (= 404 (:status response)))
      (is (re-find #"performance not found" (:body response))))))

(deftest delete-venue-returns-404-when-missing
  (with-redefs [korma/exec-raw (fn [& args]
                                 (let [[sql params] (if (vector? (first args))
                                                     (first args)
                                                     (second args))]
                                   (if (re-find #"delete from venues" sql)
                                     []
                                     :ok)))]
    (let [response (delete-venue "Missing Venue")]
      (is (= 404 (:status response)))
      (is (re-find #"venue not found" (:body response))))))

(deftest delete-song-returns-404-when-missing
  (with-redefs [korma/exec-raw (fn [& args]
                                 (let [[sql params] (if (vector? (first args))
                                                     (first args)
                                                     (second args))]
                                   (if (re-find #"delete from songs" sql)
                                     []
                                     :ok)))]
    (let [response (delete-song "Missing Song")]
      (is (= 404 (:status response)))
      (is (re-find #"song not found" (:body response))))))

(deftest update-song-returns-400-on-blank-title
  (let [body (json/write-str {:active false})
        response (update-song "   " (-> (mock/request :put "/songs/%20%20%20" body)
                                        (mock/content-type "application/json")))]
    (is (= 400 (:status response)))
    (is (re-find #"title is required" (:body response)))))

(deftest create-song-rejects-non-numeric-bpm
  (let [body (json/write-str {:title "Song A" :bpm "fast"})
        response (create-song (-> (mock/request :post "/create-song" body)
                                  (mock/content-type "application/json")))]
    (is (= 400 (:status response)))
    (is (re-find #"bpm must be a number" (:body response)))))

(deftest create-song-rejects-bad-bpm-before-db
  (with-redefs [with-transaction (fn [_] (throw (ex-info "should not hit db" {})))]
    (let [body (json/write-str {:title "Song A" :bpm "fast"})
          response (create-song (-> (mock/request :post "/create-song" body)
                                    (mock/content-type "application/json")))]
      (is (= 400 (:status response)))
      (is (re-find #"bpm must be a number" (:body response))))))

(deftest update-song-rejects-non-numeric-bpm
  (let [body (json/write-str {:bpm "fast"})
        response (update-song "Song A" (-> (mock/request :put "/songs/Song%20A" body)
                                           (mock/content-type "application/json")))]
    (is (= 400 (:status response)))
    (is (re-find #"bpm must be a number" (:body response)))))

(deftest update-song-rejects-bad-bpm-before-db
  (with-redefs [with-transaction (fn [_] (throw (ex-info "should not hit db" {})))]
    (let [body (json/write-str {:bpm "fast"})
          response (update-song "Song A" (-> (mock/request :put "/songs/Song%20A" body)
                                             (mock/content-type "application/json")))]
      (is (= 400 (:status response)))
      (is (re-find #"bpm must be a number" (:body response))))))

(deftest create-song-rejects-non-numeric-capo
  (let [body (json/write-str {:title "Song A" :capo "high"})
        response (create-song (-> (mock/request :post "/create-song" body)
                                  (mock/content-type "application/json")))]
    (is (= 400 (:status response)))
    (is (re-find #"capo must be a number" (:body response)))))

(deftest update-song-rejects-non-numeric-capo
  (let [body (json/write-str {:capo "high"})
        response (update-song "Song A" (-> (mock/request :put "/songs/Song%20A" body)
                                           (mock/content-type "application/json")))]
    (is (= 400 (:status response)))
    (is (re-find #"capo must be a number" (:body response)))))

(deftest update-song-rejects-bad-capo-before-db
  (with-redefs [with-transaction (fn [_] (throw (ex-info "should not hit db" {})))]
    (let [body (json/write-str {:capo "high"})
          response (update-song "Song A" (-> (mock/request :put "/songs/Song%20A" body)
                                             (mock/content-type "application/json")))]
      (is (= 400 (:status response)))
      (is (re-find #"capo must be a number" (:body response))))))

(deftest create-practice-session-inserts-song-id
  (let [calls (atom [])]
    (with-redefs [with-transaction (fn [f] (f))
                  korma/exec-raw (fn [& args]
                                   (let [[sql params] (if (vector? (first args))
                                                       (first args)
                                                       (second args))]
                                     (swap! calls conj {:sql sql :params params})
                                     (if (re-find #"returning id" sql)
                                       [{:id 7}]
                                       :ok)))]
      (let [body (json/write-str {:date "2026-02-01" :songs ["Song A"]})
            response (create-practice-session (-> (mock/request :post "/practice-sessions" body)
                                                  (mock/content-type "application/json")))]
        (is (= 200 (:status response)))
        (let [insert (some #(when (re-find #"insert into practice_session_songs" (:sql %)) %) @calls)]
          (is insert)
          (is (re-find #"song_id" (:sql insert)))
          (is (= [7 "Song A" "Song A" 1 nil] (:params insert))))))))

(deftest create-practice-session-requires-songs
  (with-redefs [with-transaction (fn [f] (f))
                korma/exec-raw (fn [& _] :ok)]
    (let [body (json/write-str {:date "2026-02-01" :songs []})
          response (create-practice-session (-> (mock/request :post "/practice-sessions" body)
                                                (mock/content-type "application/json")))]
      (is (= 400 (:status response)))
      (is (re-find #"songs are required" (:body response))))))

(deftest create-practice-session-rejects-blank-songs
  (with-redefs [with-transaction (fn [f] (f))
                korma/exec-raw (fn [& _] :ok)]
    (let [body (json/write-str {:date "2026-02-01" :songs [" " "\t"]})
          response (create-practice-session (-> (mock/request :post "/practice-sessions" body)
                                                (mock/content-type "application/json")))]
      (is (= 400 (:status response)))
      (is (re-find #"songs are required" (:body response))))))

(deftest create-practice-session-rejects-non-string-song
  (with-redefs [with-transaction (fn [f] (f))
                korma/exec-raw (fn [& _] :ok)]
    (let [body (json/write-str {:date "2026-02-01" :songs [123]})
          response (create-practice-session (-> (mock/request :post "/practice-sessions" body)
                                                (mock/content-type "application/json")))]
      (is (= 400 (:status response)))
      (is (re-find #"songs are required" (:body response))))))

(deftest update-practice-session-requires-date
  (let [body (json/write-str {:date "" :songs ["Song A"]})
        response (update-practice-session "42" (-> (mock/request :put "/practice-sessions/42" body)
                                                   (mock/content-type "application/json")))]
    (is (= 400 (:status response)))
    (is (re-find #"date is required" (:body response)))))

(deftest update-practice-session-rejects-blank-songs
  (let [body (json/write-str {:date "2026-02-01" :songs [" " "\t"]})
        response (update-practice-session "42" (-> (mock/request :put "/practice-sessions/42" body)
                                                   (mock/content-type "application/json")))]
    (is (= 400 (:status response)))
    (is (re-find #"songs are required" (:body response)))))

(deftest update-practice-session-rejects-non-string-song-before-db
  (with-redefs [with-transaction (fn [_] (throw (ex-info "should not hit db" {})))]
    (let [body (json/write-str {:date "2026-02-01" :songs [123]})
          response (update-practice-session "42" (-> (mock/request :put "/practice-sessions/42" body)
                                                   (mock/content-type "application/json")))]
      (is (= 400 (:status response)))
      (is (re-find #"songs are required" (:body response))))))

(deftest next-songs-to-practise-uses-frecency-store
  (let [captured (atom nil)
        played-date (java.sql.Date/valueOf "2026-02-01")]
    (with-redefs [song-plays/fetch-next-practice-songs-by-frecency (fn [store limit]
                                                                      (reset! captured {:store store :limit limit})
                                                                      [{:song_id "Song A"
                                                                        :last_played_anywhere played-date}])]
      (let [response (next-songs-to-practise)
            body (json/read-str (:body response) :key-fn keyword)]
        (is (= 200 (:status response)))
        (is (:store @captured))
        (is (= 500 (:limit @captured)))
        (is (= "Song A" (:song_id (first body))))
        (is (= "2026-02-01" (:last_played_anywhere (first body))))))))

(deftest home-next-actions-returns-two-ranked-lists
  (let [live-date (java.sql.Date/valueOf "2026-02-05")
        practice-date (java.sql.Date/valueOf "2026-02-04")]
    (with-redefs [song-plays/fetch-next-live-songs-by-frecency
                  (fn [_ limit]
                    (is (= 3 limit))
                    [{:song_id "Live Song 1" :last_played live-date}
                     {:song_id "Live Song 2" :last_played nil}
                     {:song_id "Live Song 3" :last_played nil}])
                  song-plays/fetch-next-practice-songs-by-frecency
                  (fn [_ limit]
                    (is (= 3 limit))
                    [{:song_id "Practice Song 1" :last_played_anywhere practice-date}
                     {:song_id "Practice Song 2" :last_played_anywhere nil}
                     {:song_id "Practice Song 3" :last_played_anywhere nil}])]
      (let [response (home-next-actions)
            body (json/read-str (:body response) :key-fn keyword)]
        (is (= 200 (:status response)))
        (is (= 3 (count (:next_live body))))
        (is (= 3 (count (:next_solo_practice body))))
        (is (= "Live Song 1" (get-in body [:next_live 0 :song_id])))
        (is (= "2026-02-05" (get-in body [:next_live 0 :last_played])))
        (is (= "Practice Song 1" (get-in body [:next_solo_practice 0 :song_id])))
        (is (= "2026-02-04" (get-in body [:next_solo_practice 0 :last_played_anywhere])))))))

(deftest recent-sessions-uses-store
  (let [captured (atom nil)
        session-date (java.sql.Date/valueOf "2026-02-01")
        session-created (java.sql.Timestamp/valueOf "2026-02-01 09:15:00")]
    (with-redefs [recent-sessions/fetch-recent-sessions-from
                  (fn [store limit]
                    (reset! captured {:store store :limit limit})
                    [{:session_type "solo_practice"
                      :session_id 7
                      :session_date session-date
                      :session_created_at session-created
                      :session_label nil
                      :song_count 3
                      :minimum_minutes 25
                      :actual_minutes 30}])]
      (let [response (recent-sessions)
            body (json/read-str (:body response) :key-fn keyword)]
        (is (= 200 (:status response)))
        (is (= 5 (:limit @captured)))
        (is (:store @captured))
        (is (= "solo_practice" (:session_type (first body))))
        (is (= 7 (:session_id (first body))))
        (is (= "2026-02-01" (:session_date (first body))))
        (is (= "2026-02-01 09:15:00.0" (:session_created_at (first body))))
        (is (= 3 (:song_count (first body))))
        (is (= 25 (:minimum_minutes (first body))))
        (is (= 30 (:actual_minutes (first body))))
        (is (= 30 (:effective_minutes (first body))))))))

(deftest update-practice-session-returns-404-when-missing
  (with-redefs [with-transaction (fn [f] (f))
                korma/exec-raw (fn [& args]
                                 (let [[sql] (if (vector? (first args))
                                               (first args)
                                               (second args))]
                                   (if (re-find #"update practice_sessions" sql)
                                     []
                                     :ok)))]
    (let [body (json/write-str {:date "2026-02-01" :songs ["Song A"]})
          response (update-practice-session "999" (-> (mock/request :put "/practice-sessions/999" body)
                                                      (mock/content-type "application/json")))]
      (is (= 404 (:status response)))
      (is (re-find #"practice session not found" (:body response))))))

(deftest create-song-trims-and-inserts-defaults
  (let [inserted (atom nil)]
    (with-redefs [with-transaction (fn [f] (f))
                  korma/exec-raw (fn [& args]
                                   (let [[sql params] (if (vector? (first args))
                                                       (first args)
                                                       (second args))]
                                     (reset! inserted {:sql sql :params params})
                                     :ok))]
      (let [body (json/write-str {:title "  Song A  "})
            response (create-song (-> (mock/request :post "/create-song" body)
                                      (mock/content-type "application/json")))
            response-body (json/read-str (:body response) :key-fn keyword)]
        (is (= 200 (:status response)))
        (is (re-find #"insert into songs" (:sql @inserted)))
        (is (= ["Song A" false true nil nil false nil nil nil nil nil] (:params @inserted)))
        (is (= "Song A" (:title response-body)))))))

(deftest create-song-rejects-blank-title
  (let [body (json/write-str {:title "   "})
        response (create-song (-> (mock/request :post "/create-song" body)
                                  (mock/content-type "application/json")))]
    (is (= 400 (:status response)))
    (is (re-find #"title is required" (:body response)))))

(deftest create-song-casts-length-to-interval
  (let [inserted (atom nil)]
    (with-redefs [with-transaction (fn [f] (f))
                  korma/exec-raw (fn [& args]
                                   (let [[sql params] (if (vector? (first args))
                                                       (first args)
                                                       (second args))]
                                     (reset! inserted {:sql sql :params params})
                                     :ok))]
      (let [body (json/write-str {:title "Song A" :length "02:52"})
            response (create-song (-> (mock/request :post "/create-song" body)
                                      (mock/content-type "application/json")))]
        (is (= 200 (:status response)))
        (is (re-find #"\\?::interval" (:sql @inserted)))
        (is (= "00:02:52" (nth (:params @inserted) 4)))))))

(deftest create-song-normalizes-short-length
  (let [inserted (atom nil)]
    (with-redefs [with-transaction (fn [f] (f))
                  korma/exec-raw (fn [& args]
                                   (let [[sql params] (if (vector? (first args))
                                                       (first args)
                                                       (second args))]
                                     (reset! inserted {:sql sql :params params})
                                     :ok))]
      (let [body (json/write-str {:title "Song A" :length "2:05"})
            response (create-song (-> (mock/request :post "/create-song" body)
                                      (mock/content-type "application/json")))]
        (is (= 200 (:status response)))
        (is (= "00:02:05" (nth (:params @inserted) 4)))))))

(deftest update-song-normalizes-short-length
  (let [calls (atom [])]
    (with-redefs [with-transaction (fn [f] (f))
                  korma/exec-raw (fn [& args]
                                   (let [[sql params] (if (vector? (first args))
                                                       (first args)
                                                       (second args))]
                                     (swap! calls conj {:sql sql :params params})
                                     [{:title "Song A"}]))]
      (let [body (json/write-str {:length "2:05"})
            response (update-song "Song A" (-> (mock/request :put "/songs/Song%20A" body)
                                               (mock/content-type "application/json")))]
        (is (= 200 (:status response)))
        (let [update (first @calls)]
          (is (= "00:02:05" (nth (:params update) 3))))))))

(deftest update-song-clears-blank-length
  (let [calls (atom [])]
    (with-redefs [with-transaction (fn [f] (f))
                  korma/exec-raw (fn [& args]
                                   (let [[sql params] (if (vector? (first args))
                                                       (first args)
                                                       (second args))]
                                     (swap! calls conj {:sql sql :params params})
                                     [{:title "Song A"}]))]
      (let [body (json/write-str {:length ""})
            response (update-song "Song A" (-> (mock/request :put "/songs/Song%20A" body)
                                               (mock/content-type "application/json")))]
        (is (= 200 (:status response)))
        (let [update (first @calls)]
          (is (nil? (nth (:params update) 3))))))))

(deftest normalize-length-interval-handles-blanks
  (is (nil? (normalize-length-interval nil)))
  (is (nil? (normalize-length-interval "")))
  (is (nil? (normalize-length-interval "   "))))

(deftest normalize-length-interval-keeps-hms
  (is (= "01:02:03" (normalize-length-interval "01:02:03"))))

(deftest normalize-length-interval-expands-mmss
  (is (= "00:04:05" (normalize-length-interval "4:05"))))

(deftest normalize-length-interval-keeps-non-time-string
  (is (= "unknown" (normalize-length-interval "unknown"))))

(deftest normalize-length-interval-expands-mmss-with-leading-zero
  (is (= "00:00:59" (normalize-length-interval "0:59"))))

(deftest normalize-length-interval-trims-whitespace
  (is (= "00:04:05" (normalize-length-interval "  4:05  "))))

(deftest normalize-length-interval-keeps-mmss-seconds
  (is (= "00:10:09" (normalize-length-interval "10:09"))))

(deftest normalize-length-interval-keeps-zero
  (is (= "00:00:00" (normalize-length-interval "00:00:00"))))

(deftest normalize-length-interval-trims-hms
  (is (= "01:02:03" (normalize-length-interval " 01:02:03 "))))

(deftest normalize-fee-micro-gbp-parses-strings
  (is (= 1200000 (normalize-fee-micro-gbp "1200000")))
  (is (= 1200000 (normalize-fee-micro-gbp 1200000)))
  (is (nil? (normalize-fee-micro-gbp ""))))

(deftest normalize-fee-micro-gbp-trims-input
  (is (= 1200000 (normalize-fee-micro-gbp "  1200000  "))))

(deftest normalize-fee-micro-gbp-rejects-non-numeric
  (is (= :manul-backend.handler/invalid (normalize-fee-micro-gbp "abc"))))

(deftest create-performance-rejects-invalid-fee
  (with-redefs [with-transaction (fn [f] (f))
                korma/exec-raw (fn [& _] :ok)]
    (let [body (json/write-str {:venue "The Place" :songs ["Song A"] :fee_micro_gbp "abc"})
          response (create-performance (-> (mock/request :post "/create-performance" body)
                                           (mock/content-type "application/json")))]
      (is (= 400 (:status response)))
      (is (re-find #"fee_micro_gbp is invalid" (:body response))))))

(deftest create-performance-rejects-invalid-fee-before-db
  (with-redefs [with-transaction (fn [_] (throw (ex-info "should not hit db" {})))]
    (let [body (json/write-str {:venue "The Place" :songs ["Song A"] :fee_micro_gbp "abc"})
          response (create-performance (-> (mock/request :post "/create-performance" body)
                                           (mock/content-type "application/json")))]
      (is (= 400 (:status response)))
      (is (re-find #"fee_micro_gbp is invalid" (:body response))))))

(deftest update-performance-rejects-invalid-fee-before-db
  (with-redefs [with-transaction (fn [_] (throw (ex-info "should not hit db" {})))]
    (let [body (json/write-str {:venue "The Place" :date "2026-02-01" :gig_type "open_mic" :fee_micro_gbp "abc"})
          response (update-performance "1" (-> (mock/request :put "/performances/1" body)
                                              (mock/content-type "application/json")))]
      (is (= 400 (:status response)))
      (is (re-find #"fee_micro_gbp is invalid" (:body response))))))

(deftest normalize-fee-micro-gbp-handles-nil
  (is (nil? (normalize-fee-micro-gbp nil))))

(deftest normalize-fee-micro-gbp-handles-zero
  (is (= 0 (normalize-fee-micro-gbp 0)))
  (is (= 0 (normalize-fee-micro-gbp "0"))))

(deftest normalize-fee-micro-gbp-handles-numeric-string
  (is (= 123 (normalize-fee-micro-gbp "123"))))

(deftest normalize-fee-micro-gbp-handles-number
  (is (= 123 (normalize-fee-micro-gbp 123))))

(deftest normalize-fee-micro-gbp-rounds-float
  (is (= 12 (normalize-fee-micro-gbp 12.9))))

(deftest normalize-fee-micro-gbp-rejects-decimal-string
  (is (= :manul-backend.handler/invalid (normalize-fee-micro-gbp "12.9"))))

(deftest parse-int-field-handles-blanks
  (is (nil? (parse-int-field nil)))
  (is (nil? (parse-int-field "")))
  (is (nil? (parse-int-field "   "))))

(deftest parse-int-field-rejects-non-numeric
  (is (= :manul-backend.handler/invalid (parse-int-field "abc"))))

(deftest parse-int-field-rejects-float-string
  (is (= :manul-backend.handler/invalid (parse-int-field "7.2"))))

(deftest parse-int-field-parses-numeric
  (is (= 42 (parse-int-field "42")))
  (is (= 7 (parse-int-field 7))))

(deftest parse-int-field-trims-whitespace
  (is (= 7 (parse-int-field "  7  "))))

(deftest normalize-gig-type-trims-and-rejects-blank
  (is (= "open_mic" (normalize-gig-type "  open_mic  ")))
  (is (nil? (normalize-gig-type "")))
  (is (nil? (normalize-gig-type "   "))))

(deftest normalize-gig-type-rejects-non-strings
  (is (nil? (normalize-gig-type nil)))
  (is (nil? (normalize-gig-type 123))))

(deftest normalize-gig-type-trims-tabs
  (is (= "open_mic" (normalize-gig-type (str "\t" "open_mic" "\t")))))

(deftest create-song-inserts-album-association
  (let [calls (atom [])]
    (with-redefs [with-transaction (fn [f] (f))
                  korma/exec-raw (fn [& args]
                                   (let [[sql params] (if (vector? (first args))
                                                       (first args)
                                                       (second args))]
                                     (swap! calls conj {:sql sql :params params})
                                     :ok))]
      (let [body (json/write-str {:title "Song A" :album_id 2 :track_number 3})
            response (create-song (-> (mock/request :post "/create-song" body)
                                      (mock/content-type "application/json")))]
        (is (= 200 (:status response)))
        (is (some #(re-find #"insert into songs" (:sql %)) @calls))
        (is (some #(and (re-find #"insert into album_songs" (:sql %))
                        (= [2 "Song A" 3] (:params %)))
                  @calls))))))

(deftest update-song-returns-404-when-missing
  (with-redefs [with-transaction (fn [f] (f))
                korma/exec-raw (fn [& _] [])]
    (let [body (json/write-str {:active false})
          response (update-song "Missing" (-> (mock/request :put "/songs/Missing" body)
                                              (mock/content-type "application/json")))]
      (is (= 404 (:status response)))
      (is (re-find #"song not found" (:body response))))))

(deftest update-song-upserts-album-association
  (let [calls (atom [])]
    (with-redefs [with-transaction (fn [f] (f))
                  korma/exec-raw (fn [& args]
                                   (let [[sql params] (if (vector? (first args))
                                                       (first args)
                                                       (second args))]
                                     (swap! calls conj {:sql sql :params params})
                                     (cond
                                       (re-find #"update songs" sql) [{:title "Song A"
                                                                       :cover false
                                                                       :active true
                                                                       :key nil
                                                                       :length nil
                                                                       :instrumental false
                                                                       :artist nil
                                                                       :bpm nil
                                                                       :original_key nil
                                                                       :my_key nil
                                                                       :capo nil}]
                                       :else :ok)))]
      (let [body (json/write-str {:album_id 3 :track_number 8})
            response (update-song "Song A" (-> (mock/request :put "/songs/Song%20A" body)
                                               (mock/content-type "application/json")))]
        (is (= 200 (:status response)))
        (is (some #(re-find #"delete from album_songs" (:sql %)) @calls))
        (is (some #(and (re-find #"insert into album_songs" (:sql %))
                        (= [3 "Song A" 8] (:params %)))
                  @calls))))))

(deftest update-song-casts-length-to-interval
  (let [calls (atom [])]
    (with-redefs [with-transaction (fn [f] (f))
                  korma/exec-raw (fn [& args]
                                   (let [[sql params] (if (vector? (first args))
                                                       (first args)
                                                       (second args))]
                                     (swap! calls conj {:sql sql :params params})
                                     [{:title "Song A"}]))]
      (let [body (json/write-str {:length "02:52"})
            response (update-song "Song A" (-> (mock/request :put "/songs/Song%20A" body)
                                               (mock/content-type "application/json")))]
        (is (= 200 (:status response)))
        (let [update (first @calls)]
          (is (re-find #"\\?::interval" (:sql update)))
          (is (= "00:02:52" (nth (:params update) 3))))))))

(deftest update-venue-updates-postcode
  (with-redefs [korma/exec-raw (fn [& _] [{:venuename "The Place" :postcode "AB12"}])]
    (let [body (json/write-str {:postcode "AB12"})
          response (update-venue "The Place" (-> (mock/request :put "/venues/The%20Place" body)
                                                 (mock/content-type "application/json")))
          response-body (json/read-str (:body response) :key-fn keyword)]
      (is (= 200 (:status response)))
      (is (= "The Place" (:venuename response-body)))
      (is (= "AB12" (:postcode response-body))))))

(deftest update-venue-returns-404-when-missing
  (with-redefs [korma/exec-raw (fn [& _] [])]
    (let [body (json/write-str {:postcode "AB12"})
          response (update-venue "Missing" (-> (mock/request :put "/venues/Missing" body)
                                               (mock/content-type "application/json")))]
      (is (= 404 (:status response)))
      (is (re-find #"venue not found" (:body response))))))

(deftest london-heatmap-returns-points-and-unresolved
  (with-redefs [korma/exec-raw (fn [& _]
                                 [{:venuename "Leicester Square (North West), Westminster, London"
                                   :postcode nil
                                   :gig_count 4}
                                  {:venuename "St Martin's Church, Westminster, London"
                                   :postcode nil
                                   :gig_count 2}
                                  {:venuename "The Place"
                                   :postcode "AB12 3CD"
                                   :gig_count 9}
                                  {:venuename "Trafalgar Square"
                                   :postcode "WC2N 5DN"
                                   :gig_count 3}])
                geocode-query (fn [query]
                                (case query
                                  "Leicester Square (North West), Westminster, London" {:lat 51.51 :lng -0.13}
                                  "WC2N 5DN" {:lat 51.50 :lng -0.12}
                                  nil))]
    (let [response (london-heatmap)
          body (json/read-str (:body response) :key-fn keyword)]
      (is (= 200 (:status response)))
      (is (= 2 (count (:points body))))
      (is (= 1 (count (:unresolved body))))
      (is (= "Leicester Square (North West), Westminster, London" (:name (first (:points body)))))
      (is (= 4 (:count (first (:points body)))))
      (is (= 4 (:intensity (first (:points body)))))
      (is (= ["St Martin's Church, Westminster, London"] (:unresolved body))))))

(deftest london-heatmap-route-available
  (with-redefs [korma/exec-raw (fn [& _] [])
                geocode-query (fn [_] nil)]
    (let [response (app (mock/request :get "/london-heatmap"))]
      (is (= 200 (:status response))))))

(deftest home-x-returns-summary-metrics
  (with-redefs [home-x-metrics/fetch-home-x-metrics-from (fn [_]
                                                           {:gigs_ytd 3
                                                            :gigs_lifetime 83
                                                            :solo_practice_minutes_weekly 95
                                                            :practice_minutes_ytd 318
                                                            :practice_minutes_lifetime 2400
                                                            :songs_performed_live_ytd 12
                                                            :songs_performed_live_lifetime 57
                                                            :sessions_ytd 10})
                korma/exec-raw (fn [& _]
                                 [{:song_id "Song A"}
                                  {:song_id "Song B"}
                                  {:song_id "Song C"}])
                direct-fan-outreach/fetch-direct-fan-outreach-from (fn [_]
                                                                     {:mailchimp_campaigns_sent 12
                                                                      :mailchimp_campaigns_sent_ytd 3
                                                                      :tiktok_posts 37
                                                                      :tiktok_posts_ytd 0
                                                                      :direct_fan_outreach_total 49
                                                                      :direct_fan_outreach_ytd 3})
                home-x-goals/fetch-home-x-goals-from (fn [_]
                                                        {:gigs_lifetime 200
                                                        :solo_practice_minutes_weekly 240
                                                        :practice_hours_lifetime 2000
                                                        :originals_live_lifetime 120
                                                        :direct_outreach_lifetime 3000})]
    (let [response (home-x)
          body (json/read-str (:body response) :key-fn keyword)]
      (is (= 200 (:status response)))
      (is (= 3 (get-in body [:metrics :gigs_ytd])))
      (is (= 83 (get-in body [:metrics :gigs_lifetime])))
      (is (= 95 (get-in body [:metrics :solo_practice_minutes_weekly])))
      (is (= 318 (get-in body [:metrics :practice_minutes_ytd])))
      (is (= 2400 (get-in body [:metrics :practice_minutes_lifetime])))
      (is (= 12 (get-in body [:metrics :songs_performed_live_ytd])))
      (is (= 57 (get-in body [:metrics :songs_performed_live_lifetime])))
      (is (= 10 (get-in body [:metrics :sessions_ytd])))
      (is (= 12 (get-in body [:metrics :mailchimp_campaigns_sent])))
      (is (= 3 (get-in body [:metrics :mailchimp_campaigns_sent_ytd])))
      (is (= 37 (get-in body [:metrics :tiktok_posts])))
      (is (= 0 (get-in body [:metrics :tiktok_posts_ytd])))
      (is (= 49 (get-in body [:metrics :direct_fan_outreach_total])))
      (is (= 3 (get-in body [:metrics :direct_fan_outreach_ytd])))
      (is (= 200 (get-in body [:goals :gigs_lifetime])))
      (is (= 240 (get-in body [:goals :solo_practice_minutes_weekly])))
      (is (= 2000 (get-in body [:goals :practice_hours_lifetime])))
      (is (= 120 (get-in body [:goals :originals_live_lifetime])))
      (is (= 3000 (get-in body [:goals :direct_outreach_lifetime])))
      (is (= ["Song A" "Song B" "Song C"] (:focus_songs body))))))

(deftest home-x-route-available
  (with-redefs [home-x-metrics/fetch-home-x-metrics-from (fn [_] {})
                korma/exec-raw (fn [& _] [])
                direct-fan-outreach/fetch-direct-fan-outreach-from (fn [_]
                                                                     {:mailchimp_campaigns_sent 0
                                                                      :mailchimp_campaigns_sent_ytd 0
                                                                      :tiktok_posts 0
                                                                      :tiktok_posts_ytd 0
                                                                      :direct_fan_outreach_total 0
                                                                      :direct_fan_outreach_ytd 0})
                home-x-goals/fetch-home-x-goals-from (fn [_]
                                                       {:gigs_lifetime 200
                                                        :solo_practice_minutes_weekly 240
                                                        :practice_hours_lifetime 2000
                                                        :originals_live_lifetime 120
                                                        :direct_outreach_lifetime 3000})]
    (let [response (app (mock/request :get "/home-x"))]
      (is (= 200 (:status response))))))

(deftest home-x-local-route-available
  (with-redefs [home-x-metrics/fetch-home-x-metrics-from (fn [_] {})
                korma/exec-raw (fn [& _] [])
                home-x-goals/fetch-home-x-goals-from (fn [_]
                                                       {:gigs_lifetime 200
                                                        :solo_practice_minutes_weekly 240
                                                        :practice_hours_lifetime 2000
                                                        :originals_live_lifetime 120
                                                        :direct_outreach_lifetime 3000})]
    (let [response (app (mock/request :get "/home-x/local"))]
      (is (= 200 (:status response))))))

(deftest home-next-actions-route-available
  (with-redefs [song-plays/fetch-next-live-songs-by-frecency (fn [& _] [])
                song-plays/fetch-next-practice-songs-by-frecency (fn [& _] [])]
    (let [response (app (mock/request :get "/home-next-actions"))]
      (is (= 200 (:status response))))))

(deftest home-x-outreach-route-available
  (with-redefs [direct-fan-outreach/fetch-direct-fan-outreach-from (fn [_]
                                                                     {:mailchimp_campaigns_sent 0
                                                                      :mailchimp_campaigns_sent_ytd 0
                                                                      :tiktok_posts 0
                                                                      :tiktok_posts_ytd 0
                                                                      :direct_fan_outreach_total 0
                                                                      :direct_fan_outreach_ytd 0})]
    (let [response (app (mock/request :get "/home-x/outreach"))]
      (is (= 200 (:status response))))))

(deftest home-dashboard-returns-canonical-home-payload
  (with-redefs [home-x-metrics/fetch-home-x-metrics-from (fn [_]
                                                           {:gigs_ytd 3
                                                            :gigs_lifetime 83
                                                            :solo_practice_minutes_weekly 95
                                                            :practice_minutes_ytd 318
                                                            :practice_minutes_lifetime 2400
                                                            :songs_performed_live_ytd 12
                                                            :songs_performed_live_lifetime 57
                                                            :sessions_ytd 10})
                korma/exec-raw (fn [& args]
                                 (let [[sql] (if (vector? (first args))
                                               (first args)
                                               (second args))]
                                   (cond
                                     (re-find #"with latest as" sql)
                                     [{:performancedate (java.sql.Date/valueOf "2026-02-01")
                                       :venue "The Dignity"
                                       :song_id "Song A"
                                       :setlistposition 1}
                                      {:performancedate (java.sql.Date/valueOf "2026-02-01")
                                       :venue "The Dignity"
                                       :song_id "Song B"
                                       :setlistposition 2}]
                                     (re-find #"from view_next_songs_to_perform_live" sql)
                                     [{:song_id "Song A"} {:song_id "Song B"}]
                                     :else [])))
                live-gigs-by-year-data (fn [] [{:year 2026 :gigs 2 :estimated_minutes 40}])
                sessions-by-year-data (fn []
                                        [{:year 2026
                                          :sessions 6
                                          :minimum_minutes 540
                                          :actual_minutes 480
                                          :effective_minutes 480
                                          :practice_minutes 300
                                          :performance_minutes 180}])
                song-plays/fetch-next-live-songs-by-frecency (fn [& _] [{:title "Song A"}])
                song-plays/fetch-next-practice-songs-by-frecency (fn [& _] [{:title "Song B"}])
                recent-sessions/fetch-recent-sessions-from (fn [& _]
                                                            [{:session_type "solo_practice"
                                                              :session_id 4
                                                              :session_date (java.sql.Date/valueOf "2026-02-02")
                                                              :session_created_at (java.sql.Timestamp/valueOf "2026-02-04 11:00:00")
                                                              :session_label "Warmup"
                                                              :song_count 1
                                                              :minimum_minutes 30
                                                              :actual_minutes 35
                                                              :effective_minutes 35}])
                direct-fan-outreach/fetch-direct-fan-outreach-from (fn [_]
                                                                     {:mailchimp_campaigns_sent 12
                                                                      :mailchimp_campaigns_sent_ytd 3
                                                                      :tiktok_posts 37
                                                                      :tiktok_posts_ytd 0
                                                                      :direct_fan_outreach_total 49
                                                                      :direct_fan_outreach_ytd 3})
                home-x-goals/fetch-home-x-goals-from (fn [_]
                                                       {:gigs_lifetime 200
                                                        :solo_practice_minutes_weekly 240
                                                        :practice_hours_lifetime 2000
                                                        :originals_live_lifetime 120
                                                        :direct_outreach_lifetime 3000})]
    (let [response (app (mock/request :get "/home-dashboard"))
          body (json/read-str (:body response) :key-fn keyword)]
      (is (= 200 (:status response)))
      (is (= "2026-02-01" (get-in body [:last_gig :lastGigDate])))
      (is (= 1 (count (get-in body [:home_next_actions :next_live]))))
      (is (= 1 (count (:recent_sessions body))))
      (is (= 1 (count (:live_gigs_by_year body))))
      (is (= 1 (count (:sessions_by_year body))))
      (is (= 95 (get-in body [:home_x :metrics :solo_practice_minutes_weekly])))
      (is (= 49 (get-in body [:home_x :metrics :direct_fan_outreach_total]))))))

(deftest update-home-x-goal-updates-known-goal
  (with-redefs [home-x-goals/update-home-x-goal-from (fn [_ goal-key target]
                                                       {:goal_key goal-key :target_value target})]
    (let [body (json/write-str {:target 3500})
          response (app (-> (mock/request :put "/home-x-goals/direct_outreach_lifetime" body)
                            (mock/content-type "application/json")))
          response-body (json/read-str (:body response) :key-fn keyword)]
      (is (= 200 (:status response)))
      (is (= "direct_outreach_lifetime" (:goal_key response-body)))
      (is (= 3500 (:target_value response-body))))))

(deftest update-home-x-goal-rejects-unknown-goal
  (let [body (json/write-str {:target 10})
        response (app (-> (mock/request :put "/home-x-goals/not_a_goal" body)
                          (mock/content-type "application/json")))]
    (is (= 400 (:status response)))
    (is (re-find #"goal key is invalid" (:body response)))))

(deftest update-home-x-goal-rejects-invalid-target
  (let [body (json/write-str {:target "abc"})
        response (app (-> (mock/request :put "/home-x-goals/gigs_lifetime" body)
                          (mock/content-type "application/json")))]
    (is (= 400 (:status response)))
    (is (re-find #"target is invalid" (:body response)))))

(deftest live-gigs-by-year-returns-stats
  (with-redefs [korma/exec-raw (fn [& _]
                                 [{:year 2023 :gigs 5 :estimated_minutes 120}
                                  {:year 2022 :gigs 2 :estimated_minutes 45}])]
    (let [response (live-gigs-by-year)
          body (json/read-str (:body response) :key-fn keyword)]
      (is (= 2 (count body)))
      (is (= {:year 2023 :gigs 5 :estimated_minutes 120} (first body))))))

(deftest live-gigs-by-year-query-has-no-plus
  (let [captured (atom nil)]
    (with-redefs [korma/exec-raw (fn [& args]
                                   (let [[sql] (if (vector? (first args))
                                                 (first args)
                                                 (second args))]
                                     (reset! captured sql)
                                     []))]
      (live-gigs-by-year)
      (is (re-find #"from performances" @captured))
      (is (not (re-find #"\\+\\s*left join" @captured))))))

(deftest sessions-by-year-returns-stats
  (with-redefs [korma/exec-raw (fn [& _]
                                 [{:year 2026
                                   :sessions 4
                                   :minimum_minutes 300
                                   :actual_minutes 420
                                   :effective_minutes 420
                                   :practice_minutes 240
                                   :performance_minutes 180}
                                  {:year 2025
                                   :sessions 2
                                   :minimum_minutes 120
                                   :actual_minutes 90
                                   :effective_minutes 120
                                   :practice_minutes 120
                                   :performance_minutes 0}])]
    (let [response (sessions-by-year)
          body (json/read-str (:body response) :key-fn keyword)]
      (is (= 2 (count body)))
      (is (= {:year 2026
              :sessions 4
              :minimum_minutes 300
              :actual_minutes 420
              :effective_minutes 420
              :practice_minutes 240
              :performance_minutes 180}
             (first body))))))

(deftest sessions-by-year-uses-recent-sessions-view
  (let [captured (atom nil)]
    (with-redefs [korma/exec-raw (fn [& args]
                                   (let [[sql] (if (vector? (first args))
                                                 (first args)
                                                 (second args))]
                                     (reset! captured sql)
                                     []))]
      (sessions-by-year)
      (is (re-find #"from view_recent_sessions" @captured))
      (is (re-find #"group by year" @captured))
      (is (re-find #"order by year desc" @captured)))))

(deftest replace-performance-setlist-validates-songs
  (let [body (json/write-str {:songs ["Song A" ""]})
        response (replace-performance-setlist "1" (-> (mock/request :put "/performances/1/setlist" body)
                                                      (mock/content-type "application/json")))]
    (is (= 400 (:status response)))
    (is (re-find #"songs must be a non-empty list" (:body response)))))

(deftest delete-performance-deletes-row
  (with-redefs [with-transaction (fn [f] (f))
                korma/exec-raw (fn [& args]
                                 (let [[sql _] (if (vector? (first args))
                                                (first args)
                                                (second args))]
                                   (if (re-find #"delete from performances" sql)
                                     [{:id 9}]
                                     :ok)))]
    (let [response (delete-performance "9")
          body (json/read-str (:body response) :key-fn keyword)]
      (is (= 200 (:status response)))
      (is (= 9 (:id body))))))

(deftest update-practice-session-upserts-songs
  (let [calls (atom [])]
    (with-redefs [with-transaction (fn [f] (f))
                  korma/exec-raw (fn [& args]
                                   (let [[sql params with-results?] (if (vector? (first args))
                                                                      [(first args) (second (first args)) (second args)]
                                                                      [(second args) (second (second args)) (nth args 2 nil)])]
                                     (swap! calls conj {:sql (first sql) :params params})
                                     (cond
                                       (re-find #"update practice_sessions" (first sql)) [{:id 42
                                                                                           :practiced_on "2026-02-01"
                                                                                           :total_minutes 30}]
                                       :else :ok)))]
      (let [body (json/write-str {:date "2026-02-01"
                                  :total_minutes 30
                                  :songs [{:title "Song A" :minutes 10}
                                          {:title "Song B" :minutes 20}]})
            response (update-practice-session "42" (-> (mock/request :put "/practice-sessions/42" body)
                                                        (mock/content-type "application/json")))
            response-body (json/read-str (:body response) :key-fn keyword)]
        (is (= 200 (:status response)))
        (is (= 42 (:id response-body)))
        (is (some #(re-find #"delete from practice_session_songs" (:sql %)) @calls))
        (let [inserts (filter #(re-find #"insert into practice_session_songs" (:sql %)) @calls)]
          (is (= 2 (count inserts)))
          (is (every? #(re-find #"song_id" (:sql %)) inserts))
          (is (= [42 "Song A" "Song A" 1 10] (:params (first inserts))))
          (is (= [42 "Song B" "Song B" 2 20] (:params (second inserts)))))))))
