FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml .
COPY eureka-server/pom.xml eureka-server/
COPY api-gateway/pom.xml api-gateway/
COPY customer-service/pom.xml customer-service/
COPY inventory-service/pom.xml inventory-service/
COPY order-service/pom.xml order-service/

RUN mvn dependency:go-offline -pl inventory-service -am -q

COPY inventory-service/src inventory-service/src

RUN mvn package -pl inventory-service -am -DskipTests

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /workspace/inventory-service/target/inventory-service-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
