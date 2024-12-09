FROM openjdk:21-ea-jdk-oracle

COPY target/gateway-1.1.0.jar gateway-1.1.0.jar

EXPOSE 8080

ENV INSTANCE_ID=default

VOLUME [ "/logs" ]

ENTRYPOINT ["java", "-Dlogging.file.name=/logs/gateway-${INSTANCE_ID}.log", "-jar", "gateway-1.1.0.jar"]
