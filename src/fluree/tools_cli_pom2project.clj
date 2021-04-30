(ns fluree.tools-cli-pom2project
  (:require [nevam.pom2proj :refer [process-pom]]
            [clojure.java.io :as io]
            [clojure.edn :as edn])
  (:gen-class))

(defn- find-injections []
  ;; NB: This relies on undefined behavior, so it may break with a future
  ;; Clojure release. Specifically custom keys in a deps.edn alias (like
  ;; :pom2project/injections) aren't guaranteed to always make it all the way
  ;; into the :resolve-args value in the basis. But this was the simplest way
  ;; to make this work for now, and hopefully it will keep working for awhile.
  (some-> "clojure.basis"
          System/getProperty
          io/file
          slurp
          edn/read-string
          :resolve-args
          :pom2project/injections))

(defn -main
  "Generate a project.clj file from an existing pom.xml"
  [& args]
  (let [dir  (System/getProperty "user.dir")
        pom  (io/file (str dir "/pom.xml"))
        deps (io/file (str dir "/deps.edn"))]
    (if (.exists pom)
      (do
        (if-let [injections (find-injections)]
          (process-pom pom dir injections)
          (process-pom pom dir)))
      (println "pom.xml file does not exist; run `clj -Spom` first"))))
