FROM eclipse-temurin:21-jre

WORKDIR /app

COPY /hazelcast/hazelcast-server.xml /opt/hazelcast/config_ext/hazelcast-server.xml

COPY /build/libs/*.jar hz-payment-service-demo.jar

EXPOSE 8080 8081 8082

ENTRYPOINT ["java", "-jar", "/app/hz-payment-service-demo.jar"]
