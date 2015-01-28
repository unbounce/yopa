(ns com.unbounce.yopa.http-server
  (:require [ring.adapter.jetty :as jetty]
            [ring.util.response :refer [not-found response]]
            [com.unbounce.yopa.sns-server :as sns-server]
            [clojure.tools.logging :as log]))

(def ^:private ^:dynamic server (atom nil))

(defn- log-request [request]
  (log/info "Request logger received: " request)
  (response ""))

(defn- request-router [request]
  (let [path (:uri request)]
    (case path
      "/" (sns-server/app request)
      "/request-logger" (log-request request)
      (not-found
        (str "No resource found for path: " path)))))

(defn- make-server [host port]
  (jetty/run-jetty
    request-router
    {:join? false :host host :port port}))

(defn start [host bind-address port]
  (reset! server (make-server bind-address port))
  (log/info (format "Active SNS endpoint: http://%s:%d/" bind-address port)))

(defn stop []
  (when @server
    (log/info "Shutting down HTTP server..")
    (.stop @server)
    (reset! server nil)))
