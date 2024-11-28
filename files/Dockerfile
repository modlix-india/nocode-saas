FROM openjdk:21-ea-jdk-oracle

COPY target/files-2.0.0.jar files-2.0.0.jar

EXPOSE 8003

VOLUME [ "/logs" ]

ENTRYPOINT ["java", "-Dlogging.file.name=/logs/files.log", "-jar", "files-2.0.0.jar"]