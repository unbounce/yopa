(ns com.unbounce.yopa.core
  (:require [com.unbounce.yopa.sqs-server :as sqs-server]
            [com.unbounce.yopa.http-server :as http-server]
            [com.unbounce.yopa.config :as config]
            [com.unbounce.yopa.ec2-metadata-server :as ec2-metadata-server]
            [com.unbounce.yopa.s3-server :as s3-server]
            [com.unbounce.yopa.aws-client :as aws]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [clojure.pprint :refer [print-table]])
  (:gen-class :main true))

(defonce ^:const stanza "\n\n_/\\/\\____/\\/\\____/\\/\\/\\/\\____/\\/\\/\\/\\/\\________/\\/\\_____\n_/\\/\\____/\\/\\__/\\/\\____/\\/\\__/\\/\\____/\\/\\____/\\/\\/\\/\\___\n___/\\/\\/\\/\\____/\\/\\____/\\/\\__/\\/\\/\\/\\/\\____/\\/\\____/\\/\\_\n_____/\\/\\______/\\/\\____/\\/\\__/\\/\\__________/\\/\\/\\/\\/\\/\\_\n_____/\\/\\________/\\/\\/\\/\\____/\\/\\__________/\\/\\____/\\/\\_\n________________________________________________________\n")

(defn- build-meta []
  (or
    (->
      (eval 'com.unbounce.yopa.core)
      .getPackage
      .getImplementationVersion)
    "YOPA Dev Build"))

(defn usage [options-summary]
  (string/join
    \newline
    ["YOPA is Your Own Personal AWS"
     ""
     "Usage: lein run [options]"
     ""
     "Options:"
     options-summary
     ""
     "Please refer to the manual page for more information."
     ""
     (build-meta)]))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn exit [status msg]
  (log/info msg)
  (System/exit status))

(defn default-override-file []
  (io/file (System/getProperty "java.io.tmpdir") "aws-regions-override.xml"))

(def cli-options
  [["-c" "--config CONFIG_FILE" "YAML configuration file"
    :parse-fn #(io/as-file %)
    :validate [#(.isFile %) "File must exist"]]
   ["-o" "--output REGIONS_FILE" "XML regions override file for the Java AWS SDK"
    :default (default-override-file)
    :parse-fn #(io/as-file %)]
   ["-h" "--help"]])

(defn- fill-data-array
  ([rows ar]
    (fill-data-array rows ar 0))
  ([rows ar i]
    (if (empty? rows)
      ar
      (let [[r & rs] rows]
        (aset ar i 0 (:type (str r)))
        (aset ar i 1 (:name (str r)))
        (aset ar i 2 (:arn r))
        (fill-data-array rs ar (inc i))))))

(defn- output-entities [entities]
  (print-table entities)
  (println))

(defn- persisted-entities []
  (map
    (fn [b] {:type :s3-bucket :name (:name b)})
    (aws/list-buckets)))

(defn- start [servers-config]
  (let [{:keys [host
                bind-address
                sqs-port
                sns-port
                s3-port
                s3-data-dir]} servers-config]

    (log/info "Starting up...")
    (sqs-server/start host bind-address sqs-port)
    (http-server/start host bind-address sns-port)
    (s3-server/start host bind-address s3-port s3-data-dir)
    (output-entities
      (concat
        (config/setup)
        (persisted-entities)))))

(defn stop []
  (log/info "Shutting down...")
  (s3-server/stop)
  (http-server/stop)
  (sqs-server/stop)
  (log/info "Bye!"))

(defn init-and-start [config-file output-file]
  (let [servers-config (config/init config-file output-file)]
    (aws/init servers-config)
    (ec2-metadata-server/init servers-config)
    (start servers-config)
    (.addShutdownHook (Runtime/getRuntime) (Thread. stop))
    (log/info "YOPA is running!" stanza)))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)
        config-file (:config options)
        output-file (:output options)]
    (cond
      (:help options) (exit 0 (usage summary))
      (nil? config-file) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))
    (log/info (build-meta) "is starting!")
    (init-and-start config-file output-file)))
