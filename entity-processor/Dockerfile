FROM openjdk:24-ea-jdk-oracle

COPY target/entity-processor-1.1.0.jar entity-processor-1.1.0.jar

EXPOSE 8009

VOLUME [ "/logs" ]

ENV INSTANCE_ID=default

ENTRYPOINT ["java", "-Dlogging.file.name=/logs/entity-processor-${INSTANCE_ID}.log", "-jar", "entity-processor-1.1.0.jar"]
