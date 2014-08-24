(defproject datomic-linklist "0.1.0-SNAPSHOT"
  :description "An implementation of linked lists for Datomic"
  :url "https://github.com/pellucidanalytics/datomic-linklist"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.datomic/datomic-free "0.9.4880.6"
                  :exclusions [org.clojure/clojure]]])
