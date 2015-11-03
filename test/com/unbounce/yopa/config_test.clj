(ns com.unbounce.yopa.config-test
  (:require [clojure.java.io :as io]
            [com.unbounce.yopa.core :as yopa]
            [com.unbounce.yopa.config :as config]
            [clojure.test :refer :all]))

(defonce config-file
  (io/file "yopa-config-example.yml"))

(deftest init-test
  (is (= (config/init config-file
               (yopa/default-override-file))
         {:region "yopa-local"
          :host "localhost"
          :bind-address "0.0.0.0"
          :sqs-port 47195
          :sns-port 47196
          :s3-port 47197
          :s3-data-dir "/tmp/yopa-fake-s3/"})))

(deftest resolve-config-test
  (testing "defaults"
    (is (=
         (config/resolve-config {})
         {:region "yopa-local"
          :host "localhost"
          :bind-address "0.0.0.0"
          :sqs {:port 47195 :https false}
          :sns {:port 47196 :https false}
          :s3 {:port 47197 :https false :data-dir "/tmp/yopa-fake-s3/"}
          :messaging []
          :storage {}})))

  (testing "overrides"
    (is (=
         (config/resolve-config {:sqs {:port 1}
                                 :sns {:port 2}
                                 :s3 {:port 3 :data-dir "/foo"}
                                 :bind-address "1.2.3.4"
                                 :host "foo"
                                 :region "foo-local"})
         {:region "foo-local"
          :host "foo"
          :bind-address "1.2.3.4"
          :sqs {:port 1 :https false}
          :sns {:port 2 :https false}
          :s3 {:port 3 :https false :data-dir "/foo"}
          :messaging []
          :storage {}})))

  (testing "rewriting 'legacy' config"
    (is (=
         (config/resolve-config {:sqsPort 1
                                 :snsPort 2
                                 :s3Port 3
                                 :s3DataDir "/foo"
                                 :bindAddress "1.2.3.4"})
         {:region "yopa-local"
          :host "localhost"
          :bind-address "1.2.3.4"
          :sqs {:port 1 :https false}
          :sns {:port 2 :https false}
          :s3 {:port 3 :https false :data-dir "/foo"}
          :messaging []
          :storage {}}))))
