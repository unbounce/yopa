(def project-version "1.0.0-SNAPSHOT")
(def build-meta (str "YOPA " project-version " - built on: " (java.util.Date.)))
(def ring-version "1.3.2")
(def aws-sdk-version "1.10.5.1")

(defproject com.unbounce/yopa project-version
  :description "YOPA is Your Own Personal Aws"
  :url "https://www.github.com/unbounce/yopa"
  :license {:name "The MIT License (MIT)"
            :url "http://opensource.org/licenses/MIT"
            :comments "Copyright (c) 2014-2015 Unbounce Marketing Solutions Inc."}

  :main com.unbounce.yopa.core
  :aot [com.unbounce.yopa.core]

  :uberjar-merge-with {#"\.conf$" [slurp str spit]}

  :uberjar-name "uberjar.jar"

  :manifest {"Implementation-Version" ~build-meta}

  :resource-paths ["resources" "rubygems"]

  :profiles {:dev {:plugins [[lein-kibit "0.0.8"]
                             [jonase/eastwood "0.2.1"]]}}

  :dependencies
  [
   [org.clojure/clojure "1.7.0"]
   [org.clojure/data.xml "0.0.8"]
   [org.clojure/data.json "0.2.6"]
   [org.clojure/tools.cli "0.3.1"]
   [base64-clj "0.1.1"]

   [org.clojure/tools.logging "0.3.1"]
   [org.slf4j/slf4j-log4j12 "1.7.12"]

   [circleci/clj-yaml "0.5.3"]
   [de.ubercode.clostache/clostache "1.4.0"]

   [org.elasticmq/elasticmq-rest-sqs_2.11 "0.8.8" :exclusions [joda-time]]
   [amazonica "0.3.29" :exclusions [com.amazonaws/aws-java-sdk]]
   [com.amazonaws/aws-java-sdk-sqs ~aws-sdk-version :exclusions [joda-time]]
   [com.amazonaws/aws-java-sdk-sns ~aws-sdk-version :exclusions [joda-time]]
   [com.amazonaws/aws-java-sdk-s3 ~aws-sdk-version :exclusions [joda-time]]
   ;; Amazonica has a weird dependency on cloudsearch and lambda
   [com.amazonaws/aws-java-sdk-cloudsearch ~aws-sdk-version :exclusions [joda-time]]
   [com.amazonaws/aws-java-sdk-lambda ~aws-sdk-version :exclusions [joda-time]]

   [clj-http "2.0.0" :exclusions [commons-logging
                                  cheshire
                                  org.apache.httpcomponents/httpclient]]
   [cheshire "5.5.0"]
   [ring/ring-core ~ring-version]
   [ring/ring-jetty-adapter ~ring-version]

   [org.jruby/jruby "9.0.0.0" :exclusions [com.github.jnr/jffi
                                           com.github.jnr/jnr-x86asm
                                           joda-time]]
   ])
