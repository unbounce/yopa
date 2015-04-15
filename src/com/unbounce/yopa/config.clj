(ns com.unbounce.yopa.config
  (:require [clojure.java.io :as io]
            [clj-yaml.core :as yaml]
            [clojure.tools.logging :as log]
            [com.unbounce.yopa.aws-client :as aws])
  (:import com.amazonaws.regions.RegionUtils
           java.net.URI
           java.io.File
           javax.xml.transform.TransformerFactory
           [javax.xml.transform.stream StreamSource StreamResult]))

(def ^:dynamic region (atom nil))
(def ^:dynamic sqs-endpoints (atom #{}))
(def ^:dynamic sns-endpoints (atom #{}))
(def ^:dynamic sns-subscriptions (atom #{}))

(defn- bail [reason]
  (throw (IllegalArgumentException. reason)))

(defn- as-uri [in]
  (cond
    (string? in) (URI/create in)
    (keyword? in) (as-uri (subs (str in) 1))
    :else (bail (str "Can't build a URI out of: " in))))

(defn- validate-hash-subscription [hs]
  (assert
    (and
      (contains? hs :endpoint)
      (contains? hs :rawDelivery)))
  hs)

(defn- normalize-subscription [in]
  (cond
    (string? in) { :endpoint in :rawDelivery false }
    (map? in) (validate-hash-subscription in)
    :else (bail (str "Can't normalize subscription: " in))))

(defn- init-string-endpoint [endpoint-config]
  (let [uri (as-uri endpoint-config)
        scheme (.getScheme uri)
        host (.getHost uri)]
    (case scheme
      "sns" (swap! sns-endpoints conj host)
      "sqs" (swap! sqs-endpoints conj host)
      "http" :no-op
      "https" :no-op)
    {:endpoint-type scheme :endpoint-name host}))

(defn- init-sns-subscriptions [sns-endpoint subscriptions]
  (doall
    (for [raw-sub subscriptions]
      (let [norm-sub (normalize-subscription raw-sub)
            sub-endpoint (:endpoint norm-sub)
            sub-meta (init-string-endpoint sub-endpoint)]
        (swap!
          sns-subscriptions
          conj (merge sub-meta (assoc norm-sub :source sns-endpoint)))))))

(defn- init-hash-endpoint [endpoint-config]
  (let [first-key (first (keys endpoint-config))
        {:keys [endpoint-type endpoint-name]} (init-string-endpoint first-key)
        subscriptions (get-in endpoint-config [first-key] ())]
    (case endpoint-type
      "sns" (init-sns-subscriptions endpoint-name subscriptions))))

(defn- init-endpoint [endpoint-config]
  (cond
    (string? endpoint-config) (init-string-endpoint endpoint-config)
    (map? endpoint-config) (init-hash-endpoint endpoint-config)
    :else (bail "Messaging endpoint should either be a string or a map")))

(defn- init-messaging [messaging-config]
  (dorun
    (for [endpoint-config messaging-config]
      (init-endpoint endpoint-config))))

(defn- generate-regions-override
  [output-file region host sqs-port sns-port s3-port]
  (let [source (StreamSource.
                 (.getResourceAsStream RegionUtils
                   "/com/amazonaws/regions/regions.xml"))
        target (StreamResult. output-file)
        xsl (StreamSource.
              (io/input-stream
                (io/resource "inject-yopa-config.xsl")))
        xslt (.newTransformer
               (TransformerFactory/newInstance) xsl)]
    (doto xslt
      (.setParameter "region" region)
      (.setParameter "host" host)
      (.setParameter "sqs-port" sqs-port)
      (.setParameter "sns-port" sns-port)
      (.setParameter "s3-port" s3-port)
      (.transform source target))

    (log/info "Generated AWS regions override file: "
      (.getAbsolutePath output-file))))

(defn- init-storage
  [s3-data-dir storage-config]
  (dorun
    (for [bucket (:buckets storage-config)]
      (->
        (File. (str s3-data-dir bucket))
        (.mkdirs)))))

(defn init [config-file output-file]
  (log/info "Loading config file: " (.getAbsolutePath config-file))
  (let [config (yaml/parse-string (slurp config-file))
        region* (get config :region "yopa-local")
        host (get config :host "localhost")
        bind-address (get config :bindAddress "0.0.0.0")
        sqs-port (get config :sqsPort 47195)
        sns-port (get config :snsPort 47196)
        s3-port (get config :s3Port 47197)
        s3-data-dir (get config :s3DataDir "/tmp/yopa-fake-s3/")
        messaging-config (get config :messaging [])
        storage-config (get config :storage {})]

    (reset! region region*)

    (generate-regions-override
      output-file
      region*
      host
      sqs-port
      sns-port
      s3-port)

    (init-messaging messaging-config)
    (init-storage s3-data-dir storage-config)

    {:region @region
     :host host
     :bind-address bind-address
     :sqs-port sqs-port
     :sns-port sns-port
     :s3-port s3-port
     :s3-data-dir s3-data-dir}))

; create and configure AWS entities

(defn- configure-queue [queue-name]
  (let [queue-url (aws/create-queue queue-name)
        queue-arn (aws/queue-url->arn queue-url)]
    {:type :sqs-queue
     :name queue-name
     :url queue-url
     :arn queue-arn}))

(defn- configure-queues []
  (doall
    (map configure-queue @sqs-endpoints)))

(defn- configure-topics []
  (doall
    (map
      (fn [e]
        {:type :sns-topic
         :name e
         :arn (aws/create-topic e)})
      @sns-endpoints)))

(defn- configure-subscription [subscription]
  (let [{:keys [endpoint endpoint-type endpoint-name source rawDelivery]} subscription
        topic-arn (aws/make-arn "sns" source)
        endpoint (if (= endpoint-type "sqs") (aws/make-arn "sqs" endpoint-name) endpoint)
        subscription-arn (aws/subscribe endpoint endpoint-type topic-arn)]
    (when rawDelivery
      (aws/set-raw-delivery subscription-arn))
    {:type :sns-subscription :topic-arn topic-arn :endpoint endpoint :arn subscription-arn}))

(defn- configure-subscriptions []
  (doall
    (map configure-subscription @sns-subscriptions)))

(defn setup []
  (concat
    (configure-queues)
    (configure-topics)
    (configure-subscriptions)))
