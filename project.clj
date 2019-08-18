(defproject co2 "0.1.0-SNAPSHOT"
  :description "An interview task"
  :url "https://github.com/PetrGlad/interview-co2"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [http-kit "2.3.0"]
                 [compojure "1.6.1"]
                 [net.readmarks/compost "0.2.0"]
                 [org.clojure/data.json "0.2.6"]]
  :repl-options {:init-ns co2.core}
  :main co2.main)
