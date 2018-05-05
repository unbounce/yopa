FROM clojure:lein-2.8.1-alpine
COPY . /usr/src/app
WORKDIR /usr/src/app/target
CMD ["java", "-jar", "uberjar.jar"]
