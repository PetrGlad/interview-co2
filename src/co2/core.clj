(ns co2.core
  (:require [compojure.route :refer [not-found]]
            [compojure.core :refer [defroutes GET POST DELETE ANY context]]
            [org.httpkit.server :refer [run-server]]
            [clojure.data.json :as json]
            [clojure.pprint :refer [pprint]]))

(def state
  {;; sensor-id -> sensor-state
    :sensors (ref {})
    ;; Pending events (status changes)
    :events  (ref [])})

(defn reset-state
  "Testing/development helper."
  []
  (dosync
   (ref-set (:sensors state) {})
   (ref-set (:events state) [])))

(def valid-status-levels #{:UNKNOWN :OK :WARN :ALERT})

(def new-sensor
  {:status       :UNKNOWN
   :measurements []})

(defn over-limit? [co2]
  (< 2000 co2))

(defn add-measurement [sensor measurement]
  (let [{status       :status
         measurements :measurements} sensor
        {time :time co2 :co2}   measurement
        new-measurements        (take 3 (conj measurements co2))
        over                    (-> (filter over-limit? new-measurements)
                                    (count))
        new-status              (cond
                                  (<= 3 over)       :ALERT
                                  (= 0 over)        :OK
                                  (= :ALERT status) :ALERT
                                  true              :WARN)]
    {:status       new-status
     :measurements new-measurements}))

(defn notify? [old-status new-status]
  (and (not= old-status new-status)
       (case new-status
         :OK    (= old-status :ALERT)
         :ALERT true
         false)))

(defn register-measurement [{sensors :sensors events :events} sensor-id measurement]
  (println "Register" (str "#" sensor-id) measurement)
  (dosync
   (let [sensor         (get @sensors sensor-id new-sensor)
         updated-sensor (add-measurement sensor measurement)]
     (when (notify? (:status sensor) (:status updated-sensor))
       (alter events conj
              {:sensor-id sensor-id
               :status    (:status updated-sensor)
               :time      (:time measurement)}))
     (alter sensors assoc sensor-id updated-sensor))))

(defn take-queue! [events]
  (dosync
   (let [queue @events]
     (ref-set events [])
     queue)))

(defn flush-statuses! [events status-logger]
  (doseq [event (take-queue! events)]
    (status-logger event)))

(defn record-status [new-status]
  (println "New status" new-status))

(defn json-response [data]
  {:status  200
   :headers {"Content-Type" "application/json; charset=UTF-8"}
   ; application/json
   :body    (json/write-str data)})


(defn landing-page [req]
  {:status  200
   :headers {"Content-Type" "text/plain"}
   :body    "C02 monitoring server. API is at\n  /api/v1/"})

(defn get-status [sensor-id]
  (-> state
      :sensors
      deref
      (get sensor-id new-sensor)
      (select-keys [:status])))

(defn parse-json [in-stream]
  (json/read (clojure.java.io/reader in-stream) :key-fn keyword))

(defroutes app
  (GET "/" [] landing-page)
  (context "/api/v1/sensors/:sensor-id{(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}}"
           [sensor-id]
           (GET "/" []
                (-> (get-status sensor-id)
                    (json-response)))
           (POST "/measurements" {body :body}
                 (->> (parse-json body)
                      (register-measurement state sensor-id)
                      (json-response)))
           ; (GET "/metrics" (sensor-metrics (:sensors state) sensor-id)) ;; Not implemented
           (not-found "Resoure is not found.")))

(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    (println "Stopping server.")
    (@server :timeout 100)
    (reset! server nil)))

(defn start-server [params]
  (println "Starting server" params)
  (reset! server (run-server #'app params)))



(comment
  (require
   '[org.httpkit.client :as http]
   '[clojure.datafy :refer [datafy]])
  (import
   [java.util UUID])

  (stop-server)
  (-main)
  (def a-sensor-id (.toUpperCase (str (UUID/randomUUID))))
  (pprint
   @(http/post
     (str "http://localhost:8080/api/v1/sensors/" a-sensor-id "/measurements")
     {:body (json/write-str
             {:co2  2001,
              :time "2019-02-01T18:55:47+00:00"})}))
  (println "Status changes")
  (flush-statuses! (:events state) println)
  (pprint
   @(http/get
     (str "http://localhost:8080/api/v1/sensors/" a-sensor-id))))
