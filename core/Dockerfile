FROM openjdk:21-ea-jdk-oracle

COPY target/core-1.1.0.jar core-1.1.0.jar

EXPOSE 8005

VOLUME [ "/logs" ]

ENV INSTANCE_ID=default

ENTRYPOINT ["java", "-Dlogging.file.name=/logs/core-${INSTANCE_ID}.log", "-jar", "core-1.1.0.jar"]