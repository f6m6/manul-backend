(ns manul-backend.handler-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [clojure.data.json :as json]
            [korma.core :as korma]
            [manul-backend.handler :refer :all]))

(deftest create-performance-requires-venue-and-songs
  (let [response (app (-> (mock/request :post "/create-performance" "{}")
                          (mock/content-type "application/json")))]
    (is (= 400 (:status response)))
    (is (re-find #"venue and songs are required" (:body response)))))

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

(deftest create-venue-requires-name
  (let [response (app (-> (mock/request :post "/create-venue" "{}")
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
