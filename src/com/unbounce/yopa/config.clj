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

(def ^:private default-config
  {:region "yopa-local"
   :host "localhost"
   :bind-address "0.0.0.0"
   :messaging []
   :storage {}
   :sqs
   {:port 47195
    :https false}
   :sns
   {:port 47196
    :https false}
   :s3
   {:port 47197
    :https false
    :data-dir "/tmp/yopa-fake-s3/"}})

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
  [output-file {:keys [host region] :as config}]
  (let [source (StreamSource. (io/input-stream (io/resource "aws_regions.xml")))
        target (StreamResult. output-file)
        xsl (StreamSource.
              (io/input-stream
                (io/resource "inject-yopa-config.xsl")))
        xslt (.newTransformer
               (TransformerFactory/newInstance) xsl)]
    (doto xslt
      (.setParameter "region" region)
      (.setParameter "sqs-port" (get-in config [:sqs :port]))
      (.setParameter "sqs-host" (get-in config [:sqs :host]
                                        (str host ":" (get-in config [:sqs :port]))))
      (.setParameter "sqs-https" (get-in config [:sqs :https]))
      (.setParameter "sns-port" (get-in config [:sns :port]))
      (.setParameter "sns-host" (get-in config [:sns :host]
                                        (str host ":" (get-in config [:sns :port]))))
      (.setParameter "sns-https" (get-in config [:sns :https]))
      (.setParameter "s3-port" (get-in config [:s3 :port]))
      (.setParameter "s3-host" (get-in config [:s3 :host]
                                       (str host ":" (get-in config [:s3 :port]))))
      (.setParameter "s3-https" (get-in config [:s3 :https]))
      (.transform source target))

    (log/info "Generated AWS regions override file: "
      (.getAbsolutePath output-file))))

(defn- init-storage
  [s3-data-dir storage-config]
  (dorun
    (for [bucket (:buckets storage-config)]
      (.mkdirs
        (File.
          (str s3-data-dir bucket))))))

(defn- deep-merge
  "Recursively merge, ignoring nil values"
  [& xs]
  (let [xs (remove nil? xs)]
    (if (every? map? xs)
      (apply merge-with deep-merge xs)
      (last xs))))

(defn- rewrite-config-as-nested
  [config]
  (let [rewritten-config
        (deep-merge
         {:bind-address (:bindAddress config)
          :sqs
          {:port (:sqsPort config)}
          :sns
          {:port (:snsPort config)}
          :s3
          {:port (:s3Port config)
           :data-dir (:s3DataDir config)}}
         config)]
    (dissoc rewritten-config
            :bindAddress
            :sqsPort
            :snsPort
            :s3Port
            :s3DataDir)))

(defn resolve-config
  [config-from-file]
  (deep-merge
   default-config
   (rewrite-config-as-nested config-from-file)))

(defn init [config-file output-file]
  (log/info "Loading config file: " (.getAbsolutePath config-file))
  (let [config-from-file (yaml/parse-string (slurp config-file))
        config (resolve-config config-from-file)]
    (reset! region (:region config))

    (generate-regions-override output-file config)

    (init-messaging (:messaging config))
    (init-storage (get-in config [:s3 :data-dir]) (:storage config))

    (assoc (select-keys config [:region :host :bind-address])
           :sqs-port (get-in config [:sqs :port])
           :sns-port (get-in config [:sns :port])
           :s3-port (get-in config [:s3 :port])
           :s3-data-dir (get-in config [:s3 :data-dir]))))

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
    {:type :sns-subscription
     :name source
     :url endpoint
     :arn subscription-arn}))

(defn- configure-subscriptions []
  (doall
    (map configure-subscription @sns-subscriptions)))

(defn setup []
  (concat
    (configure-queues)
    (configure-topics)
    (configure-subscriptions)))
