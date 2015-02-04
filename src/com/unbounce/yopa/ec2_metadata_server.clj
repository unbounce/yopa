(ns com.unbounce.yopa.ec2-metadata-server
  (:require [ring.util.response :refer [not-found response]]
            [clostache.parser :refer [render-resource]]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log])
  (:import  java.net.InetAddress))

(def ^:const http-base-path "/ec2-metadata")

(def ^:private ^:dynamic metadata (atom nil))

(defn init [servers-config]
  (let [context (assoc servers-config
                  :hostname
                  (.getCanonicalHostName
                    (InetAddress/getLocalHost)))
        json-data (render-resource
                    "ec2-metadata-template.json"
                    context)]

    (log/info "Initializing EC2 Metadata service")
    (reset! metadata (json/read-str json-data
                                    :key-fn keyword))))

(defn- path->keys [path]
  (map keyword (str/split path #"/")))

(defn handle-ring-request [request]
  (let [path (:uri request)
        ; drop / and base-path
        map-path (vec (drop 2 (path->keys path)))
        value (get-in @metadata map-path)]
    (if
      (nil? value)
      (not-found
        (str "No EC2 Metadata at path: " path))
      (response value))))
