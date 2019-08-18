(ns co2.core-test
  (:require [clojure.test :refer :all]
            [co2.core :refer :all]
            [org.httpkit.client :as http]
            [clojure.data.json :as json])
  (:import [java.util UUID]))

(deftest ppm-level-test
  (is (over-limit? 2001))
  (is (over-limit? 2020))
  (is (not (over-limit? 2000)))
  (is (not (over-limit? 679))))

(defn meas [value]
  {:time "Sun, 18 Aug 2019 17:04:21 GMT"
   :co2  value})

(deftest add-measurement-test
  (let [process (fn [measurements] (reduce add-measurement new-sensor measurements))]
    (is
     (= {:status :OK :measurements [2000]}
        (process [(meas 2000)])))
    (is
     (= {:status :WARN :measurements [2001]}
        (process [(meas 2001)])))
    (is
     (= {:status :WARN :measurements [2002 2001]}
        (process [(meas 2001) (meas 2002)])))
    (is
     (= {:status :ALERT :measurements [2002 2003 2001]}
        (process [(meas 2001) (meas 2003) (meas 2002)])))
    (is
     (= {:status :ALERT :measurements [2000 2002 2003]}
        (process [(meas 2001) (meas 2003) (meas 2002) (meas 2000)])))
    (is
     (= {:status :ALERT :measurements [1998 2000 2002]}
        (process [(meas 2001) (meas 2003) (meas 2002) (meas 2000) (meas 1998)])))
    (is
     (= {:status :OK :measurements [1999 1998 2000]}
        (process
         [(meas 2001) (meas 2003) (meas 2002) (meas 2000) (meas 1998) (meas 1999)])))))

(deftest notify-gate-test
  (is (notify? :WARN :ALERT))
  (is (notify? :ALERT :OK))
  (is (not (notify? :OK :WARN)))
  (is (not (notify? :UNDEFINED :OK)))
  (is (not (notify? :UNDEFINED :WARN))))

(deftest add-measurement-test
  (let [state {:sensors (ref {})
               :events  (ref [])}]
    (register-measurement state "asdf" (meas 2020))
    (is (= [] @(:events state)))
    (register-measurement state "asdf" (meas 2020))
    (is (= [] @(:events state)))
    (register-measurement state "asdf" (meas 2020))
    (is
     (= [{:sensor-id "asdf", :status :ALERT, :time "Sun, 18 Aug 2019 17:04:21 GMT"}]
        @(:events state)))
    (flush-statuses! (:events state) identity)
    (is (= [] @(:events state)))
    (register-measurement state "asdf" (meas 800))
    (register-measurement state "asdf" (meas 900))
    (register-measurement state "asdf" (meas 1999))
    (is
     (= [{:sensor-id "asdf", :status :OK, :time "Sun, 18 Aug 2019 17:04:21 GMT"}]
        @(:events state)))))

(deftest server-http-test
  (try
    (let [port        (+ 30000 (rand-int 30000))
          a-sensor-id (str (UUID/randomUUID))
          sensor-url  #(str "http://localhost:" port "/api/v1/sensors/" %)
          meas-url    #(str (sensor-url %) "/measurements")
          post-meas   (fn [co2]
                        @(http/post (meas-url a-sensor-id)
                          {:body (json/write-str
                                  {:co2  2001,
                                   :time "2019-02-01T18:55:47+00:00"})}))
          get-sensor  (fn [sid]
                        (-> @(http/get (sensor-url sid))
                            :body
                            (json/read-str :key-fn keyword)))]
      (stop-server)
      (reset-state)
      (start-server {:ip "127.0.0.1" :port port})
      (is (= {:status "UNKNOWN"} (get-sensor a-sensor-id)))
      (post-meas 2001)
      (is (= {:status "WARN"} (get-sensor a-sensor-id)))
      (post-meas 2001)
      (post-meas 2001)
      (is (= {:status "ALERT"} (get-sensor a-sensor-id))))
    (finally
      (stop-server))))



(comment
  (run-tests))
