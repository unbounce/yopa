(ns com.unbounce.yopa.sqs-server
  (:require [amazonica.core :as aws]
            [amazonica.aws.sqs :as sqs]
            [clojure.tools.logging :as log])
  (:import org.elasticmq.rest.sqs.SQSRestServerBuilder
           org.elasticmq.server.ElasticMQServer
           org.elasticmq.server.ElasticMQServerConfig
           org.elasticmq.NodeAddress
           com.typesafe.config.ConfigObject
           com.typesafe.config.ConfigFactory
           com.typesafe.config.Config))

(def config-string "{
   \"storage\":  {
      \"type\" = \"in-memory\"
   },
   \"node-address\":{
      \"protocol\":\"http\",
      \"host\":\"localhost\",
      \"port\":\"47195\",
      \"context-path\":\"\"
   },
   \"rest-sqs\":{
      \"enabled\":\"true\",
      \"bind-port\":\"47195\",
      \"bind-hostname\":\"0.0.0.0\",
      \"sqs-limits\":\"strict\"
   }
}")

(def ^:private ^:dynamic server (atom nil))

(defn- make-main-server []
  (let [typesafe-config (ConfigFactory/parseString config-string)]
  (let [config (ElasticMQServerConfig. typesafe-config)]
  (-> (ElasticMQServer. config)
    (.start)))))

(defn- make-server [host bind-address port]
  (let [address (NodeAddress. "http", host, port, "")]
  (-> (SQSRestServerBuilder/withPort port)
    (.withInterface bind-address)
    (.withPort port)
    (.withServerAddress address)
    (.start))))

(defn start [host bind-address port]
  (reset! server (make-main-server))
  (log/info (format "Active SQS endpoint: http://%s:%d" bind-address port)))

(defn stop []
  (when @server
    (reset! server nil)))
