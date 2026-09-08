# --- Etapa 1: build (Maven + JDK 17) ---
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Si el build de Maven necesita el mismo ajuste de certificados que en desarrollo local (ver
# PROMPT_ARQUITECTURA_HUATA.md), pasarlo como build-arg:
#   docker build --build-arg MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT .
ARG MAVEN_OPTS=""
ENV MAVEN_OPTS=${MAVEN_OPTS}

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src ./src
RUN ./mvnw -q -B -DskipTests package && \
    mv target/*.jar target/app.jar

# --- Etapa 2: runtime (solo JRE, imagen liviana) ---
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
