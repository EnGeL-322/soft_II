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

# curl es lo unico que se agrega a la imagen: lo usa el healthcheck del docker-compose contra
# /actuator/health, que es lo que hace que el frontend espere a que el backend este realmente
# listo (depends_on: condition: service_healthy) y no solo a que el contenedor exista.
RUN apt-get update && apt-get install --no-install-recommends -y curl && rm -rf /var/lib/apt/lists/*

# No corre como root.
RUN useradd --system --create-home --uid 1001 huata
COPY --from=build --chown=huata:huata /app/target/app.jar app.jar
USER huata

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
