(ns com.unbounce.yopa.sqs-server
  (:require [amazonica.core :as aws]
            [amazonica.aws.sqs :as sqs]
            [clojure.tools.logging :as log])
  (:import org.elasticmq.rest.sqs.SQSRestServerBuilder
           org.elasticmq.NodeAddress))

(def ^:dynamic server (atom nil))

(defn- make-server [host bind-address port]
  (let [address (NodeAddress. "http", host, port, "")]
  (-> (SQSRestServerBuilder/withPort port)
    (.withInterface bind-address)
    (.withPort port)
    (.withServerAddress address)
    (.start))))

(defn start [host bind-address port]
  (reset! server (make-server host bind-address port))
  (.waitUntilStarted @server)
  (log/info (format "Active SQS endpoint: http://%s:%d" bind-address port)))

(defn stop []
  (when @server
    (.stopAndWait @server)
    (reset! server nil)))
