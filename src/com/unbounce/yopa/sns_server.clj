(ns com.unbounce.yopa.sns-server
  (:require [ring.middleware.params :as params]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [base64-clj.core :as b64]
            [clj-http.client :as http]
            [clojure.tools.logging :as log]
            [com.unbounce.yopa.aws-client :as aws]
            [clojure.data.xml :refer [element emit-str]])
  (:import  java.text.SimpleDateFormat
            java.util.Calendar
            java.util.UUID))

(def ^:const http-base-path "/")

(def ^:private ^:const xml-ns "http://sns.amazonaws.com/doc/2010-03-31/")
(def ^:const confirmable-subscription-protocols #{"http" "https"})
(def ^:const subscription-protocols #{"http" "https" "sqs"})
(def ^:const subscription-schemes #{"http" "https" "arn"})
(def ^:const http-timeout-millis 5000)

(def ^:private ^:dynamic topics (atom {}))
(def ^:private ^:dynamic subscriptions (atom {}))
(def ^:private ^:dynamic *action* "n/a")

(defrecord Topic [name arn subscription-arns attributes])
(defrecord Subscription [arn endpoint protocol raw-delivery topic-arn])

;; Supporting Functions

(defn- uuid [] (str (UUID/randomUUID)))

(defn- get-current-iso-8601-date []
  (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    (.getTime (Calendar/getInstance))))

(defn- bail [type status message]
  (throw (ex-info message {:type type :status status :cause message})))

(defn- bail-client [status message]
  (log/warn message)
  (bail "Sender" status message))

(defn- bail-server [status message]
  (log/error message)
  (bail "Receiver" status message))

(defn- respond [status xml]
  {:status status
   :headers {"Content-Type" "application/xml"}
   :body (emit-str xml)})

(defn- build-response
  ([]
    (build-response nil))
  ([body]
    (let [response-type (str *action* "Response")
          result-type (str *action* "Result")]
      (element response-type {:xmlns xml-ns}
        (when body
          (element result-type {}
            body))
        (element "ResponseMetadata" {}
          (element "RequestId" {} (uuid)))))))

(defn- build-error-response [type message]
  (element "ErrorResponse" {:xmlns xml-ns}
    (element "Error" {}
      (element "Type" {} type)
      (element "Message" {} message))
    (element "RequestId" {} (uuid))))

(defn- req-param [param request]
  (or
    (get (:form-params request) param)
    (get (:query-params request) param)))

;; Create Topic

(defn- handle-create-topic [request]
  (let [topic-name (req-param "Name" request)
        topic-arn (aws/make-arn "sns" topic-name)]
    (when-not (contains? @topics topic-arn)
      (swap! topics assoc topic-arn
             (Topic.
               topic-name
               topic-arn
               #{}
               {"DisplayName" topic-name "TopicArn" topic-arn "SubscriptionsConfirmed" 0})))
    (respond 200
      (build-response
        (element "TopicArn" {} topic-arn)))))

;; List Topics

(defn- handle-list-topics [request]
  (let [members (map #(element "member" {} (element "TopicArn" {} %)) (keys @topics))]
    (respond 200
      (build-response
        (element "Topics" {} members)))))

;; Get Topic Attributes

(defn- handle-get-topic-attributes [request]
  (let [topic-arn (req-param "TopicArn" request)
        attributes (:attributes (get @topics topic-arn))]
    (if attributes
      (respond 200
        (build-response
          (element "Attributes" {}
            (map #(element "entry" {}
                    (element "key" {} (first %))
                    (element "value" {} (str (second %))))
                 attributes)
            )))
      (bail-client 404 (str "No topic found for ARN: " topic-arn)))))

;; Subscribe

(defn- subscribe [endpoint protocol topic]
  (let [topic-arn (:arn topic)
        subscription-arn (str topic-arn ":" (uuid))
        subscription (Subscription.
                       subscription-arn
                       endpoint
                       protocol
                       false
                       topic-arn)]
    (swap! topics assoc (:arn topic)
           (-> topic
             (update-in [:subscription-arns] #(conj % subscription-arn))
             (update-in [:attributes "SubscriptionsConfirmed"] inc)))
    (swap! subscriptions assoc subscription-arn subscription)
    subscription-arn))

(defn- handle-subscribe [request]
  (let [endpoint (req-param "Endpoint" request)
        protocol (req-param "Protocol" request)
        topic-arn (req-param "TopicArn" request)
        topic (get @topics topic-arn)]
    (when-not topic
      (bail-client 404 (str "No topic found for ARN: " topic-arn)))
    (when-not (contains? subscription-protocols protocol)
      (bail-client 400 (str "Unsupported subscription protocol: " protocol)))
    (when-not (contains? subscription-schemes (first (str/split endpoint #":")))
      (bail-client 400 (str "Unsupported subscription endpoint: " endpoint)))
    (respond 200
      (build-response
        (element "SubscriptionArn" {} (subscribe endpoint protocol topic))))))


;; Unsubscribe

(defn- remove-subscription-from-topics
  [topics subscription-arn]
  (reduce-kv
    (fn [result topic-arn topic]
      (assoc result
        topic-arn
        (update-in
          topic
          [:subscription-arns]
          #(disj % subscription-arn))
        ))
    {}
    topics))

(defn- handle-unsubscribe [request]
  (let [subscription-arn (req-param "SubscriptionArn" request)
        subscription (get @subscriptions subscription-arn)]
    (when-not subscription
      (bail-client 404
        (str "No subscription found for ARN: " subscription-arn)))
    (swap! topics
           remove-subscription-from-topics subscription-arn)
    (swap! subscriptions
           dissoc subscription-arn)
    (respond 200 (build-response))))


;; Set Subscription Attributes

(defn- handle-set-subscription-attributes [request]
  (let [attribute-name (req-param "AttributeName" request)
        attribute-value (req-param "AttributeValue" request)
        subscription-arn (req-param "SubscriptionArn" request)
        subscription (get @subscriptions subscription-arn)]
    (when-not subscription
      (bail-client 404
        (str "No subscription found for ARN: " subscription-arn)))
    (when-not (= attribute-name "RawMessageDelivery")
      (bail-client 400
        (str "Unsupported attribute name: " attribute-name)))
    (when (str/blank? attribute-value)
      (bail-client 400
        (str "Attribute value can not be blank")))
    (swap! subscriptions
           assoc subscription-arn
           (update-in subscription
             [:raw-delivery]
             (constantly (boolean attribute-value))))
    (respond 200 (build-response))))


;; Confirm Subscription (basically a no-op when valid params are provided)

(defn- handle-confirm-subscription [request]
  (let [token (req-param "Token" request)
        subscription-arn (b64/decode token)
        subscription (get @subscriptions subscription-arn)
        topic-arn (req-param "TopicArn" request)
        topic (get @topics topic-arn)]
    (when-not topic
      (bail-client 404 (str "No topic found for ARN: " topic-arn)))
    (when-not subscription
      (bail-client 400 (str "Invalid token refers to unknown subscription: " subscription-arn)))
    (respond 200
      (build-response
        (element "SubscriptionArn" {} subscription-arn)))))


;; List Subscriptions By Topic

(defn- subscription-list-entry [topic-arn subscription-arn]
  (when-let [subscription (get @subscriptions subscription-arn)]
    (element "member" {}
      (element "TopicArn" {} topic-arn)
      (element "Protocol" {} (:protocol subscription))
      (element "SubscriptionArn" {} (:arn subscription))
      (element "Endpoint" {} (:endpoint subscription)))))

(defn- subscription-list [topic]
  (map
    (partial
      subscription-list-entry
      (:arn topic))
    (:subscription-arns topic)))

(defn- handle-list-subscriptions []
  (respond 200
    (build-response
      (element "Subscriptions" {}
        (flatten
          (map subscription-list (vals @topics)))))))

(defn- handle-list-subscriptions-by-topic [request]
  (let [topic-arn (req-param "TopicArn" request)
        topic (get @topics topic-arn)]
    (when-not topic
      (bail-client 404 (str "No topic found for ARN: " topic-arn)))
    (respond 200
      (build-response
        (element "Subscriptions" {}
          (subscription-list topic))))))


;; Verify Subscription
;; (custom action that triggers a confirmation of the specified HTTP/S subscription)

(defn- handle-verify-subscription [request]
  (let [subscription-arn (req-param "SubscriptionArn" request)
        subscription (get @subscriptions subscription-arn)
        protocol (:protocol subscription)
        topic-arn (:topic-arn subscription)
        message-id (uuid)]

    (when-not subscription
      (bail-client 404 (str "No subscription found for ARN: " subscription-arn)))

    (when-not
      (contains?
        confirmable-subscription-protocols
        protocol)
      (bail-client 400 (str
                         "Subscription: " subscription-arn
                         " doesn't have a confirmable protocol: " protocol)))
    (log/info
      (format "Verifying subscription: %s" subscription-arn))

    (http/post (:endpoint subscription)
               {:headers {:Content-Type "application/json"
                          "x-amz-sns-message-type" "SubscriptionConfirmation"
                          "x-amz-sns-message-id" message-id
                          "x-amz-sns-topic-arn" topic-arn}
                :body (json/write-str
                        {:Type "Notification"
                         :MessageId message-id
                         :TopicArn topic-arn
                         :SubscribeURL (str
                                         (name (:scheme request))
                                         "://"
                                         (get-in request [:headers "host"])
                                         "/?Action=ConfirmSubscription"
                                         "&TopicArn=" topic-arn
                                         "&Token=" (b64/encode subscription-arn))
                         :Message (str
                                    "You have chosen to subscribe to the topic "
                                    topic-arn
                                    ".\nTo confirm the subscription, visit the SubscribeURL included in this message.")
                         :Timestamp (get-current-iso-8601-date)})
                :throw-exceptions true
                :socket-timeout http-timeout-millis
                :conn-timeout http-timeout-millis})

    (respond 200 (build-response))))


;; Publish

(defn- route-with-http
  [message message-id uri topic-arn subscription-arn]
  (try
    (let [res (http/post uri
                         {:headers {:Content-Type "application/json"
                                    "x-amz-sns-message-type" "Notification"
                                    "x-amz-sns-message-id" message-id
                                    "x-amz-sns-topic-arn" topic-arn
                                    "x-amz-sns-subscription-arn" subscription-arn}
                          :body message
                          :throw-exceptions true
                          :socket-timeout http-timeout-millis
                          :conn-timeout http-timeout-millis})]
      (log/info
        (format "Received status code: %s when routing SNS message ID: %s to: %s"
          (:status res) message-id uri)))
    (catch Throwable t
      (log/error
        (format "Caught exception: %s when routing SNS message ID: %s to: %s"
          (.getMessage t) message-id uri)))))

(defn- route-with-sqs [message message-id queue-arn]
  (let [res (aws/send-sqs-message queue-arn message)]
    (log/info
      (format "Received SQS message ID: %s when routing SNS message ID: %s to: %s"
        (:message-id res) message-id queue-arn))))

(defn- wrap-delivery [topic-arn message-id subject message]
  (json/write-str
    {:Type "Notification"
     :MessageId message-id
     :TopicArn topic-arn
     :Subject subject
     :Message message
     :Timestamp (get-current-iso-8601-date)}))

(defn- route-to-subscription [topic-arn message-id subject message subscription]
  (let [message* (if (:raw-delivery subscription)
                   message
                   (wrap-delivery topic-arn message-id subject message))
        endpoint (:endpoint subscription)
        protocol (:protocol subscription)
        subscription-arn (:arn subscription)]

    (log/debug
      (format "Routing message ID: %s from topic: %s to endpoint: %s (protocol: %s)"
        message-id topic-arn endpoint protocol))

    (case protocol
      "http" (route-with-http
               message*
               message-id
               endpoint
               topic-arn
               subscription-arn)

      "https" (route-with-http
                message*
                message-id
                endpoint
                topic-arn
                subscription-arn)

      "sqs" (route-with-sqs
              message*
              message-id
              endpoint)

      (log/error
        (format "Ignoring endpoint with invalid protocol: %s when routing message ID: %s from topic: %s to endpoint: %s."
          protocol message-id topic-arn endpoint)))))

(defn- route-to-subscriptions [subject message topic]
  (let [topic-arn (:arn topic)
        subscription-arns (:subscription-arns topic)
        message-id (uuid)]
    (if (empty? subscription-arns)
      (log/info
        (format "Topic: %s has no subscription, dropping message: %s"
          (:name topic) message))
      (do
        (log/debug
          (format
            "Routing message ID: %s to topic: %s %d subscription(s)."
            message-id (:name topic) (count subscription-arns)))
        (dorun
          (for [subscription-arn subscription-arns]
            (if-let [subscription (get @subscriptions subscription-arn)]
              (future
                (route-to-subscription topic-arn message-id subject message subscription))
              (log/warn
                (format "Nil subscription found for arn: %s in: %s"
                  subscription-arn @subscriptions)))))))
    message-id))

(defn- handle-publish [request]
  (let [topic-arn (req-param "TopicArn" request)
        subject (req-param "Subject" request)
        message (req-param "Message" request)
        topic (get @topics topic-arn)]
    (when-not topic
      (bail-client 404 (str "No topic found for ARN: " topic-arn)))
    (let [message-id (route-to-subscriptions subject message topic)]
      (respond 200
        (build-response
          (element "MessageId" {} message-id))))))


;; General Handling Machinery

(defn- handle-unsupported-request [request]
  (bail-client 400 (str "Unsupported action: " *action*)))

(defn- request-handler [request]
  (binding [*action* (req-param "Action" request)]
    (case *action*
      "CreateTopic"               (handle-create-topic request)
      "ListTopics"                (handle-list-topics request)
      "GetTopicAttributes"        (handle-get-topic-attributes request)
      "Subscribe"                 (handle-subscribe request)
      "Unsubscribe"               (handle-unsubscribe request)
      "SetSubscriptionAttributes" (handle-set-subscription-attributes request)
      "ConfirmSubscription"       (handle-confirm-subscription request)
      "ListSubscriptions"         (handle-list-subscriptions)
      "ListSubscriptionsByTopic"  (handle-list-subscriptions-by-topic request)
      "Publish"                   (handle-publish request)

      ;; custom actions
      "VerifySubscription"       (handle-verify-subscription request)

      ;; catch all
      (handle-unsupported-request request))))

(defn- standard-errors [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo ei
        (let [{:keys [type status cause]} (ex-data ei)]
          (respond status (build-error-response type cause))))
      (catch Throwable t
        (log/error t "Failed to handle request: " request)
        (respond 500 (build-error-response "Receiver" (.getMessage t)))))))

(def handle-ring-request
  (-> request-handler
    (standard-errors)
    (params/wrap-params)))
