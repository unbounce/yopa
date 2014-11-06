(ns com.unbounce.yopa.sqs-server
  (:require [amazonica.core :as aws]
            [amazonica.aws.sqs :as sqs]
            [clojure.tools.logging :as log])
  (:import org.elasticmq.rest.sqs.SQSRestServerBuilder
           org.elasticmq.NodeAddress))

(def ^:dynamic server (atom nil))

(defn- make-server [host port]
  (let [address (NodeAddress. "http", host, port, "")]
  (-> (SQSRestServerBuilder/withPort port)
    (.withInterface host)
    (.withPort port)
    ;(.withServerAddress address)
    (.start))))

(defn start [host port]
  (reset! server (make-server host port))
  (.waitUntilStarted @server)
  (log/info (format "Active SQS endpoint: http://%s:%d" host port)))

(defn stop []
  (when @server
    (.stopAndWait @server)
    (reset! server nil)))
