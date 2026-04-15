FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml .
COPY eureka-server/pom.xml eureka-server/
COPY api-gateway/pom.xml api-gateway/
COPY customer-service/pom.xml customer-service/
COPY inventory-service/pom.xml inventory-service/
COPY order-service/pom.xml order-service/

RUN mvn dependency:go-offline -pl eureka-server -am -q

COPY eureka-server/src eureka-server/src

RUN mvn package -pl eureka-server -am -DskipTests

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /workspace/eureka-server/target/eureka-server-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
