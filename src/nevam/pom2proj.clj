(ns nevam.pom2proj
  "Code adapted from lein-nevam by Tom Hickey (https://github.com/thickey/lein-nevam)."
  (:require [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zx]
            [clojure.pprint :as pprint]))

(defn- text-attrs
  [loc ks]
  (map (fn [k]
         (zx/xml1-> loc k zx/text))
       ks))

(defn- attr-map
  [loc ks]
  (reduce (fn [m k]
            (assoc m k (zx/xml1-> loc k zx/text)))
          {} ks))

(defn- map-by-attr
  [locs k-key v-key]
  (reduce (fn [m loc]
            (let [[k v] (text-attrs loc [k-key v-key])]
              (assoc m k v)))
          {} locs))

(defn- artifact-sym
  [{:keys [groupId artifactId]}]
  (symbol (if (or (not groupId) (= groupId artifactId))
            artifactId
            (str groupId "/" artifactId))))

(defn- exclusions
  [dep-loc]
  (map #(attr-map % [:groupId :artifactId])
       (zx/xml-> dep-loc :exclusions :exclusion)))

(defn- dependency
  [dep-loc]
  (let [dep (attr-map dep-loc [:groupId :artifactId :version])
        exclusions (exclusions dep-loc)]
    (if (empty? exclusions)
      dep
      (assoc dep :exclusions exclusions))))

(defn- dependencies
  [pom-zip]
  (zx/xml-> pom-zip :dependencies :dependency dependency))

(defn- parent
  [pom-zip]
  (let [parent (zx/xml1-> pom-zip :parent)]
    (if parent
      (attr-map parent
                [:groupId :artifactId :version :relativePath])
      {})))

(defn- project
  [pom-zip]
  (attr-map pom-zip
            [:groupId :artifactId :version :description :url]))

(defn- repositories
  [pom-zip]
  (map-by-attr (zx/xml-> pom-zip :repositories :repository)
               :id :url))

(defn- deploy-repositories
  [pom-zip]
  (when-let [dist (zx/xml1-> pom-zip :distributionManagement)]
    {:releases (zx/xml1-> dist :repository :url zx/text)
     :snapshots (zx/xml1-> dist :snapshotRepository :url zx/text)}))

(defn- build
  [pom-zip]
  (when-let [bld (zx/xml1-> pom-zip :build)]
    (let [lein-bld {:source-paths   [(zx/xml1-> bld :sourceDirectory zx/text)]
                    :test-paths     (when-let [tp (zx/xml1-> bld :testSourceDirectory zx/text)]
                                      [tp])
                    :resource-paths (let [rps (into [] (zx/xml-> bld :resources :resource :directory zx/text))]
                                      (not-empty rps))}]
      (reduce-kv (fn [m k v]
                   (if (nil? v)
                     m
                     (assoc m k v)))
                 {} lein-bld))))

(defn- lein-project-info
  [xz]
  (let [attrs {:dependencies (dependencies xz)
               :repositories (repositories xz)
               :deploy-repositories (deploy-repositories xz)
               :parent (parent xz)}
        attrs (filter (fn [[k v]]
                        (if (coll? v) (not-empty v) v))
                      attrs)]
    (into (merge (project xz) (build xz))
          attrs)))

(defn- format-dependencies
  [deps]
  (into [] (map (fn [dep]
                  (let [d [(artifact-sym dep) (:version dep)]
                        ex (:exclusions dep)]
                    (if ex
                      (conj d :exclusions
                            (into [] (map artifact-sym ex)))
                      d)))
                deps)))

(defn- format-parent
  [parent]
  (let [rel (:relativePath parent)
        p [(artifact-sym parent) (:version parent)]]
    (if rel
      (conj p :relative-path rel)
      p)))

(defn- format-lein-project
  [{:keys [dependencies parent] :as project} injections]
  (let [info (dissoc project :groupId :artifactId :version)
        info (assoc info
               :dependencies (format-dependencies dependencies)
               :parent (if parent (format-parent parent) {}))
        info (into {} (filter (fn [[k v]]
                                (if (coll? v) (not-empty v) v))
                              info))
        pf `(~'defproject ~(artifact-sym project) ~(:version project)
              ~@(mapcat (fn [[k v]] [k v]) info)
              ~@(mapcat (fn [[k v]] [k v]) injections))]
    pf))

(defn- pprint-project [project]
  (pprint/with-pprint-dispatch pprint/code-dispatch
                               (pprint/cl-format true "~<(~;~a ~a ~s~^ ~1i~_~@<~@{~<~w~^ ~w~:>~^ ~_~}~:>~;)~:>~%"
                                                 (concat (take 3 project)
                                                         (list (partition 2 (drop 3 project)))))))

(defn process-pom
  ([from-file to-dir injections]
   (let [xz (zip/xml-zip (xml/parse from-file))
         project-info (lein-project-info xz)
         f (format-lein-project project-info injections)]
     (spit (str to-dir "/project.clj")
           (with-out-str (pprint-project f)))))
  ([from-file to-dir]
   (process-pom from-file to-dir {}))
  ([from]
   (process-pom from from)))
