FROM openjdk:21-ea-jdk-oracle

COPY target/notification-1.0.0.jar notification-1.0.0.jar

EXPOSE 8007

VOLUME [ "/logs" ]

ENV INSTANCE_ID=default

ENTRYPOINT ["java", "-Dlogging.file.name=/logs/notification-${INSTANCE_ID}.log", "-jar", "notification-1.0.0.jar"]
