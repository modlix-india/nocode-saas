FROM openjdk:21-ea-jdk-oracle

COPY target/security-1.1.0.jar security-1.1.0.jar

EXPOSE 8001

VOLUME [ "/logs" ]

ENV INSTANCE_ID=default

ENTRYPOINT ["java", "-Dlogging.file.name=/logs/security-${INSTANCE_ID}.log", "-jar", "security-1.1.0.jar"]
