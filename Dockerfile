FROM openjdk:8-jre-alpine

ARG jar_file=target/yopa-1.0.0-SNAPSHOT-standalone.jar
ARG yopa_config=yopa-config-example.yml

RUN mkdir -p /opt/app

COPY ${jar_file} /opt/app/app.jar
COPY ${yopa_config} /opt/app/config/yopa-config.yml
WORKDIR /opt/app

ENTRYPOINT ["java", "-jar", "app.jar"]

CMD ["-c", "/opt/app/config/yopa-config.yml"]