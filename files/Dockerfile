FROM openjdk:21-ea-jdk-oracle

COPY target/files-2.0.0.jar files-2.0.0.jar

EXPOSE 8003

VOLUME [ "/logs" ]

ENV INSTANCE_ID=default

ENTRYPOINT ["java", "-Dlogging.file.name=/logs/files-${INSTANCE_ID}.log", "-jar", "files-2.0.0.jar"]
