FROM openjdk:21-ea-jdk-oracle

COPY target/multi-2.0.0.jar multi-2.0.0.jar

EXPOSE 8006

VOLUME [ "/logs" ]

ENTRYPOINT ["java", "-Dlogging.file.name=/logs/multi.log", "-jar", "multi-2.0.0.jar"]
