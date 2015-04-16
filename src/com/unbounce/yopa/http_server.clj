(ns com.unbounce.yopa.http-server
  (:require [ring.adapter.jetty :as jetty]
            [ring.util.response :refer [not-found response]]
            [com.unbounce.yopa.sns-server :as sns-server]
            [com.unbounce.yopa.ec2-metadata-server :as ec2-metadata-server]
            [clojure.tools.logging :as log]))

(def ^:private ^:dynamic server (atom nil))

(defn- log-request [request]
  (log/info
    "Request logger received:"
    (update-in request [:body] slurp))
  (response ""))

(defn- request-router [request]
  (let [base-path (last (re-matches #"^(/[^/]*).*" (:uri request)))]
    (condp = base-path
      sns-server/http-base-path
        (sns-server/handle-ring-request request)

      ec2-metadata-server/http-base-path
        (ec2-metadata-server/handle-ring-request request)

      "/request-logger"
        (log-request request)

      (not-found
        (str "No resource found for: " base-path)))))

(defn- make-server [host port]
  (jetty/run-jetty
    request-router
    {:join? false :host host :port port}))

(defn start [host bind-address port]
  (reset! server (make-server bind-address port))
  (log/info
    (format
      "Active SNS endpoint: http://%s:%d%s"
      bind-address port sns-server/http-base-path))
  (log/info
    (format
      "Active EC2 Metadata endpoint: http://%s:%d%s"
      bind-address port ec2-metadata-server/http-base-path)))

(defn stop []
  (when @server
    (log/info "Shutting down HTTP server..")
    (.stop @server)
    (reset! server nil)))
