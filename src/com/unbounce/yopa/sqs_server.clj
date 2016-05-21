(ns com.unbounce.yopa.sqs-server
  (:require [amazonica.core :as aws]
            [amazonica.aws.sqs :as sqs]
            [clojure.tools.logging :as log])
  (:import org.elasticmq.server.ElasticMQServer
           org.elasticmq.server.ElasticMQServerConfig
           com.typesafe.config.ConfigFactory
           com.typesafe.config.Config))

(def config-string "{
    \"akka.http.server.parsing.illegal-header-warnings\": \"off\",
    \"akka\": {
        \"loggers\": [\"akka.event.slf4j.Slf4jLogger\"],
        \"loglevel\": \"DEBUG\",
        \"logging-filter\": \"akka.event.slf4j.Slf4jLoggingFilter\",
        \"log-dead-letters-during-shutdown\": false
    },
   \"akka.http.server.request-timeout\": \"21 s\",
   \"akka.http.server.parsing.max-uri-length\": \"256k\",
   \"storage\":  {
      \"type\": \"in-memory\"
   },
   \"node-address\": {
      \"protocol\": \"http\",
      \"host\": \"%s\",
      \"port\": \"%d\",
      \"context-path\": \"\"
   },
   \"rest-sqs\": {
      \"enabled\": true,
      \"bind-hostname\": \"%s\",
      \"bind-port\": \"%d\",
      \"sqs-limits\": \"relaxed\"
   },
   \"queues\": {}
}")

(def ^:private ^:dynamic server (atom nil))

(defn- make-main-server [host bind-address port]
  (let [formatted-config (format config-string host port bind-address port)]
  (log/info formatted-config)
  (let [typesafe-config (ConfigFactory/parseString formatted-config)]
  (let [config (ElasticMQServerConfig. typesafe-config)]
  (-> (ElasticMQServer. config)
    (.start))))))

(defn start [host bind-address port]
  (reset! server (make-main-server host bind-address port))
  (log/info (format "Active SQS endpoint: http://%s:%d" bind-address port)))

(defn stop []
  (when @server
    (reset! server nil)))
