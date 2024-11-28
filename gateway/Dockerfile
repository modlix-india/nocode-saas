FROM openjdk:21-ea-jdk-oracle

COPY target/gateway-1.1.0.jar gateway-1.1.0.jar

EXPOSE 8080

VOLUME [ "/logs" ]

ENTRYPOINT ["java", "-Dlogging.file.name=/logs/gateway.log", "-jar", "gateway-1.1.0.jar"]
