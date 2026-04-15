FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml .
COPY eureka-server/pom.xml eureka-server/
COPY api-gateway/pom.xml api-gateway/
COPY customer-service/pom.xml customer-service/
COPY inventory-service/pom.xml inventory-service/
COPY order-service/pom.xml order-service/

RUN mvn dependency:go-offline -pl api-gateway -am -q

COPY api-gateway/src api-gateway/src

RUN mvn package -pl api-gateway -am -DskipTests

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /workspace/api-gateway/target/api-gateway-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
