FROM openjdk:21-ea-jdk-oracle

COPY target/ui-1.1.0.jar ui-1.1.0.jar

EXPOSE 8002

VOLUME [ "/logs" ]

ENTRYPOINT ["java", "-Dlogging.file.name=/logs/ui.log", "-jar", "ui-1.1.0.jar"]
