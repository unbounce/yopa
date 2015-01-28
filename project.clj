(def aws-sdk-version "1.9.17")
(def ring-version "1.3.2")

(defproject com.unbounce/yopa "1.0.0-SNAPSHOT"
  :description "YOPA is Your Own Personal Aws"
  :url "https://www.github.com/unbounce/yopa"
  :license {:name "The MIT License (MIT)"
            :url "http://opensource.org/licenses/MIT"
            :comments "Copyright (c) 2014-2015 Unbounce Marketing Solutions Inc."}

  :main com.unbounce.yopa.core
  :aot [com.unbounce.yopa.core]

  :uberjar-merge-with {#"\.conf$" [slurp str spit]}

  :dependencies
  [
   [org.clojure/clojure "1.6.0"]
   [org.clojure/data.xml "0.0.8"]
   [org.clojure/data.json "0.2.5"]
   [org.clojure/tools.cli "0.3.1"]

   [circleci/clj-yaml "0.5.3"]
   [de.ubercode.clostache/clostache "1.4.0"]

   [org.elasticmq/elasticmq-rest-sqs_2.11 "0.8.2"]
   [amazonica "0.3.13" :exclusions [com.amazonaws/aws-java-sdk
                                    com.amazonaws/amazon-kinesis-client
                                    joda-time
                                    org.apache.httpcomponents/httpclient]]
   [com.amazonaws/aws-java-sdk-sns ~aws-sdk-version :exclusions [joda-time]]
   [com.amazonaws/aws-java-sdk-sqs ~aws-sdk-version :exclusions [joda-time]]
   [com.amazonaws/aws-java-sdk-s3  ~aws-sdk-version :exclusions [joda-time]]

   [clj-http "1.0.1"]
   [ring/ring-core ~ring-version]
   [ring/ring-jetty-adapter ~ring-version]

   [org.clojure/tools.logging "0.3.1"]
   [org.slf4j/slf4j-log4j12 "1.7.7"]
  ]

  :repositories
  [
   ["softwaremill-releases" "https://nexus.softwaremill.com/content/repositories/releases"]
   ["spray-releases"        "http://repo.spray.io"]
  ]
)
