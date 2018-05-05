(ns com.unbounce.yopa.s3-server
  (:require [clojure.tools.logging :as log])
  (:import  org.jruby.Ruby
            org.jruby.javasupport.JavaEmbedUtils
            org.jruby.runtime.builtin.IRubyObject))

(def ^:private ^:dynamic jruby (atom nil))
(def ^:private ^:dynamic server (atom nil))

(defn- has-status? [status]
  (some
    #(and
      (= (.getName %) "@status")
      (= (->> % .getValue .toString) status))
    (.getVariableList @server)))

(defn- ponder-for-status [status]
  (while
    (not (has-status? status))
    (Thread/sleep 1000)))

(defn- make-init-script
  [host bind-address port data-dir]
  (str
    "require 'fakes3' \n"
    "store = FakeS3::FileStore.new('" data-dir "', false) \n"
    "webrick_config = { :BindAddress => '" bind-address "'"
                     ", :Port        => " port
                     ", :AccessLog   => [] } \n"
    "webrick = WEBrick::HTTPServer.new(webrick_config) \n"
    "webrick.mount '/', FakeS3::Servlet, store, '" host "' \n"
    "webrick"))

(defn- make-s3-server
  [host bind-address port data-dir]
  (.executeScript
    @jruby
    (make-init-script host bind-address port data-dir)
    "init.rb"))

(defn- call-server-method [method]
  (JavaEmbedUtils/invokeMethod
    @jruby
    @server
    method
    nil
    IRubyObject))

(defn start [host bind-address port data-dir]
  (reset! jruby (Ruby/getGlobalRuntime))
  (reset! server (make-s3-server
                   host
                   bind-address
                   port
                   data-dir))
  (future
    ;; the following hijacks the thread hence the future
    (call-server-method "start"))

  (ponder-for-status "Running")

  (log/info
    (format
      "Active S3 endpoint: http://%s:%d with data dir: %s"
      bind-address
      port
      data-dir)))

(defn stop []
  (when @jruby
    (log/info "Shutting down S3 server..")
    (call-server-method "shutdown")
    (ponder-for-status "Stop")
    (.tearDown @jruby)
    (reset! server nil)
    (reset! jruby nil)))
