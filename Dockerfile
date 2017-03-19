FROM java:8
COPY ./target/yopa-1.0.0-SNAPSHOT-standalone.jar /yopa.jar
CMD ["java", "-jar", "/yopa.jar", "-c", "/config/yopa.yml"]