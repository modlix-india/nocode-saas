FROM openjdk:21-ea-jdk-oracle

COPY target/entity-collector-1.1.0.jar entity-collector-1.1.0.jar

EXPOSE 8008

VOLUME [ "/logs" ]

ENV INSTANCE_ID=default

ENTRYPOINT ["java", "-Dlogging.file.name=/logs/entity-collector-${INSTANCE_ID}.log", "-jar", "entity-collector-1.1.0.jar"]
