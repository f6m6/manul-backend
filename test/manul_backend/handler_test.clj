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
                  korma/exec-raw (fn [& args]
                                   (let [[sql params with-results?] (if (vector? (first args))
                                                                      [(first args) (second (first args)) (second args)]
                                                                      [(second args) (second (second args)) (nth args 2 nil)])]
                                     (cond
                                       (re-find #"insert into performances" (first sql)) [{:id 123}]
                                       (re-find #"insert into song_performances" (first sql)) (do
                                                                                                (reset! song-perf-insert sql)
                                                                                                :ok)
                                       :else :ok)))]
      (let [body (json/write-str {:venue "The Place" :songs ["Song A" "Song B"]})
            response (create-performance (-> (mock/request :post "/create-performance" body)
                                             (mock/content-type "application/json")))
            response-body (json/read-str (:body response) :key-fn keyword)]
        (is (= 200 (:status response)))
        (is (= 123 (second (second @song-perf-insert))))
        (is (= 123 (:performanceId response-body)))
        (is (= 2 (:songs response-body)))))))

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

(deftest create-song-trims-and-inserts-defaults
  (let [inserted (atom nil)]
    (with-redefs [korma/exec-raw (fn [& args]
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
        (is (= ["Song A" false true nil nil false] (:params @inserted)))
        (is (= "Song A" (:title response-body)))))))
