(ns com.unbounce.yopa.core
  (:require [com.unbounce.yopa.sqs-server :as sqs-server]
            [com.unbounce.yopa.sns-server :as sns-server]
            [com.unbounce.yopa.config :as config]
            [com.unbounce.yopa.aws-client :as aws]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [clojure.pprint :refer [print-table]])
  (:gen-class :main true))

(defonce ^:const stanza "\n\n_/\\/\\____/\\/\\____/\\/\\/\\/\\____/\\/\\/\\/\\/\\________/\\/\\_____\n_/\\/\\____/\\/\\__/\\/\\____/\\/\\__/\\/\\____/\\/\\____/\\/\\/\\/\\___\n___/\\/\\/\\/\\____/\\/\\____/\\/\\__/\\/\\/\\/\\/\\____/\\/\\____/\\/\\_\n_____/\\/\\______/\\/\\____/\\/\\__/\\/\\__________/\\/\\/\\/\\/\\/\\_\n_____/\\/\\________/\\/\\/\\/\\____/\\/\\__________/\\/\\____/\\/\\_\n________________________________________________________\n")

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
     "Please refer to the manual page for more information."]))

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
  ([rows ar] (fill-data-array rows ar 0))
  ([rows ar i]
    (if (empty? rows)
      ar
      (let [[r & rs] rows]
        (aset ar i 0 (:type (str r)))
        (aset ar i 1 (:name (str r)))
        (aset ar i 2 (:arn r))
        (fill-data-array rs ar (inc i))))))

(defn- output-setup [entities]
  (print-table entities)
  (println))

(defn- start [servers-config]
  (let [{:keys [host sqs-port sns-port]} servers-config]
    (log/info "Starting up...")
    (sqs-server/start host sqs-port)
    (sns-server/start host sns-port)
    (output-setup (config/setup))))

(defn stop []
  (log/info "Shutting down...")
  (sns-server/stop)
  (sqs-server/stop)
  (log/info "Bye!"))

(defn init-and-start [config-file output-file]
  (let [servers-config (config/init config-file output-file)]
    (aws/init servers-config)
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
    (init-and-start config-file output-file)))
