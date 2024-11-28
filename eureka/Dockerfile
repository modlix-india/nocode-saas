FROM openjdk:21-ea-jdk-oracle

COPY target/eureka-1.1.0.jar eureka-1.1.0.jar

EXPOSE 9999

VOLUME [ "/logs" ]

ENTRYPOINT ["java", "-Dlogging.file.name=/logs/eureka.log", "-jar", "eureka-1.1.0.jar"]