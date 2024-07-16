FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 hookdock
COPY --from=build /src/target/hookdock-*.jar /app/hookdock.jar
USER hookdock
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/hookdock.jar"]
