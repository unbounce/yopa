(ns com.unbounce.yopa.s3-server
  (:require [clojure.tools.logging :as log])
  (:import  org.jruby.Ruby))

(def ^:private ^:dynamic jruby (atom nil))

(defn- make-start-script [bind-address port data-dir]
  (str
    "require 'fakes3'\n"
    "store = FakeS3::FileStore.new('" data-dir "')\n"
    "server = FakeS3::Server.new('" bind-address "'," port ",store,'s3.amazonaws.com',nil,nil)\n"
    "server.serve"))

(defn start [host bind-address port data-dir]
  (let [runtime (reset! jruby (Ruby/getGlobalRuntime))]
    (future
      (log/info
        (format
          "Active S3 endpoint: http://%s:%d with data dir: %s"
          bind-address
          port
          data-dir))
      (.executeScript
        runtime
        (make-start-script bind-address port data-dir)
        "start.rb")
      (System/exit 0))))

(defn stop []
  (when @jruby
    (log/info "Shutting down S3 server..")
    (.tearDown @jruby)
    (reset! jruby nil)))
