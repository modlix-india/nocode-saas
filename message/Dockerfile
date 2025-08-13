FROM openjdk:21-ea-jdk-oracle

COPY target/message-1.0.0.jar message-1.0.0.jar

EXPOSE 8010

VOLUME [ "/logs" ]

ENV INSTANCE_ID=default

ENTRYPOINT ["java", "-Dlogging.file.name=/logs/message-${INSTANCE_ID}.log", "-jar", "message-1.0.0.jar"]
