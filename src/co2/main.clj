(ns co2.main
 (:require [co2.core :refer [start-server]])
 (:gen-class))

(defn -main [& args]
 (start-server {:ip "127.0.0.1" :port 8080}))


