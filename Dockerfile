# ==========================================
# 1. Etapa de Compilación (Builder)
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Instalar bash para compatibilidad con mvnw
RUN apk add --no-cache bash

# Copiar wrapper, pom y código fuente
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src src

# Compilar empaquetando el JAR ejecutable
RUN ./mvnw clean package -DskipTests

# Extraer el JAR en capas (lib/ y app.jar) con Spring Boot Jar Tools
RUN java -Djarmode=tools -jar target/nexofood-api-0.0.1-SNAPSHOT.jar extract --destination /build/extracted

# ==========================================
# 2. Etapa de Ejecución (Runtime)
# ==========================================
FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

# Crear usuario y grupo sin privilegios por seguridad
RUN addgroup -S spring && adduser -S spring -G spring

# Copiar librerías externas (capa pesada y estable)
COPY --from=builder --chown=spring:spring /build/extracted/lib /app/lib

# Copiar artefacto ejecutable de la aplicación (capa ligera)
COPY --from=builder --chown=spring:spring /build/extracted/nexofood-api-0.0.1-SNAPSHOT.jar /app/app.jar

USER spring:spring

EXPOSE 8080

# Opciones de JVM optimizadas para contenedores (limite de memoria cgroup, etc.)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

# Healthcheck nativo usando Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=35s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Ejecutar con exec para que Java reciba directamente las señales de parada (SIGTERM)
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
