(ns manul-backend.data.direct-fan-outreach
  (:require [clojure.data.json :as json]
            [clojure.string :as s]))

(defprotocol DirectFanOutreachStore
  (fetch-direct-fan-outreach [store]))

(def nu-config-path
  "/Users/farhan/Library/Application Support/nushell/config.nu")

(defn- lookup-nu-config-value
  [key]
  (try
    (when (.exists (java.io.File. nu-config-path))
      (let [content (slurp nu-config-path)
            pattern (re-pattern (str "\\$env\\." key "\\s*=\\s*\"([^\"]*)\""))
            m (re-find pattern content)]
        (when (and (vector? m) (second m))
          (second m))))
    (catch Exception _
      nil)))

(defn- getenv*
  [key]
  (let [value (System/getenv key)]
    (if (s/blank? value)
      (lookup-nu-config-value key)
      value)))

(defn- read-json-url
  [url headers]
  (let [conn (.openConnection (java.net.URL. url))]
    (.setConnectTimeout conn 10000)
    (.setReadTimeout conn 15000)
    (.setRequestProperty conn "User-Agent" "manul-backend/1.0")
    (doseq [[k v] headers]
      (.setRequestProperty conn k v))
    (with-open [r (java.io.InputStreamReader. (.getInputStream conn) "UTF-8")]
      (json/read r :key-fn keyword))))

(defn- parse-mailchimp-server-prefix
  [api-key]
  (when (and api-key (s/includes? api-key "-"))
    (second (s/split api-key #"-" 2))))

(defn- mailchimp-auth-header
  [api-key]
  (let [token (.encodeToString (java.util.Base64/getEncoder)
                               (.getBytes (str "anystring:" api-key) "UTF-8"))]
    {"Authorization" (str "Basic " token)}))

(defn- current-year
  []
  (.getValue (java.time.Year/now)))

(defn- year-of-iso-datetime
  [s]
  (try
    (.getYear (java.time.OffsetDateTime/parse (str s)))
    (catch Exception _
      nil)))

(defn- fetch-mailchimp-campaigns-sent
  []
  (let [api-key (getenv* "MAILCHIMP_API_KEY")
        prefix (or (getenv* "MAILCHIMP_SERVER_PREFIX")
                   (parse-mailchimp-server-prefix api-key))]
    (if (or (s/blank? api-key) (s/blank? prefix))
      {:mailchimp_campaigns_sent 0
       :mailchimp_campaigns_sent_ytd 0}
      (loop [offset 0
             total 0
             ytd 0]
        (let [url (str "https://" prefix ".api.mailchimp.com/3.0/reports?count=100&offset=" offset)
              body (read-json-url url (mailchimp-auth-header api-key))
              reports (or (:reports body) [])
              total-items (or (:total_items body) 0)
              subtotal (count reports)
              ytd-subtotal (count (filter #(= (year-of-iso-datetime (:send_time %))
                                              (current-year))
                                          reports))
              next-offset (+ offset subtotal)]
          (if (or (empty? reports) (>= next-offset total-items))
            {:mailchimp_campaigns_sent (+ total subtotal)
             :mailchimp_campaigns_sent_ytd (+ ytd ytd-subtotal)}
            (recur next-offset (+ total subtotal) (+ ytd ytd-subtotal))))))))

(defn- fetch-tiktok-post-count
  []
  (let [username (getenv* "TIKTOK_USERNAME")]
    (if (s/blank? username)
      0
      (let [url (str "https://www.tiktok.com/@" username)
            conn (.openConnection (java.net.URL. url))]
        (.setConnectTimeout conn 10000)
        (.setReadTimeout conn 15000)
        (.setRequestProperty conn "User-Agent" "Mozilla/5.0")
        (with-open [r (java.io.BufferedReader.
                       (java.io.InputStreamReader. (.getInputStream conn) "UTF-8"))]
          (let [content (apply str (line-seq r))
                m (re-find #"\"videoCount\":(\d+)" content)]
            (if (and (vector? m) (second m))
              (Integer/parseInt (second m))
              0)))))))

(defrecord HttpDirectFanOutreachStore []
  DirectFanOutreachStore
  (fetch-direct-fan-outreach [_]
    (let [mailchimp (try
                      (fetch-mailchimp-campaigns-sent)
                      (catch Exception _ {:mailchimp_campaigns_sent 0
                                          :mailchimp_campaigns_sent_ytd 0}))
          tiktok (try
                   (fetch-tiktok-post-count)
                   (catch Exception _ 0))]
      {:mailchimp_campaigns_sent (:mailchimp_campaigns_sent mailchimp)
       :mailchimp_campaigns_sent_ytd (:mailchimp_campaigns_sent_ytd mailchimp)
       :tiktok_posts tiktok
       :tiktok_posts_ytd 0
       :direct_fan_outreach_total (+ (:mailchimp_campaigns_sent mailchimp) tiktok)
       :direct_fan_outreach_ytd (+ (:mailchimp_campaigns_sent_ytd mailchimp) 0)})))

(def db-store (->HttpDirectFanOutreachStore))

(defn fetch-direct-fan-outreach-from
  [store]
  (fetch-direct-fan-outreach store))
