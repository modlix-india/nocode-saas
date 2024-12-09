FROM openjdk:21-ea-jdk-oracle

COPY target/ui-1.1.0.jar ui-1.1.0.jar

EXPOSE 8002

VOLUME [ "/logs" ]

ENV INSTANCE_ID=default

ENTRYPOINT ["java", "-Dlogging.file.name=/logs/ui-${INSTANCE_ID}.log", "-jar", "ui-1.1.0.jar"]
