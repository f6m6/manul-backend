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
  (let [song-perf-insert (atom nil)]
    (with-redefs [with-transaction (fn [f] (f))
                  korma/values (fn [& args] (first args))
                  korma/insert (fn [entity & clauses]
                                 (cond
                                   (identical? entity performances) {:id 123}
                                   (identical? entity song_performances) (do
                                                                          (reset! song-perf-insert (first clauses))
                                                                          :ok)
                                   :else :ok))]
      (let [body (json/write-str {:venue "The Place" :songs ["Song A" "Song B"]})
            response (create-performance (-> (mock/request :post "/create-performance" body)
                                             (mock/content-type "application/json")))
            response-body (json/read-str (:body response) :key-fn keyword)]
        (is (= 200 (:status response)))
        (is (= 123 (:performance_id @song-perf-insert)))
        (is (= 123 (:performanceId response-body)))
        (is (= 2 (:songs response-body)))))))

(deftest create-venue-requires-name
  (let [response (app (-> (mock/request :post "/create-venue" "{}")
                          (mock/content-type "application/json")))]
    (is (= 400 (:status response)))
    (is (re-find #"venuename is required" (:body response)))))

(deftest create-song-requires-title
  (let [response (app (-> (mock/request :post "/create-song" "{}")
                          (mock/content-type "application/json")))]
    (is (= 400 (:status response)))
    (is (re-find #"title is required" (:body response)))))
