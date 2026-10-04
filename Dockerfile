# syntax=docker/dockerfile:1

# ---------------------------------------------------------------- build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# As dependências vêm antes do código: enquanto o pom não mudar, esta camada é reaproveitada
# e o build não baixa o mundo de novo.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# O jar executável do Spring Boot é expandido em camadas para que dependências e código da
# aplicação fiquem em camadas separadas: uma troca de código não invalida as dependências.
RUN mkdir -p target/extraido \
    && java -Djarmode=tools -jar target/*.jar extract --layers --destination target/extraido \
    # Nome fixo para o ENTRYPOINT não depender da versão no nome do arquivo.
    && mv target/extraido/application/*.jar target/extraido/application/app.jar

# ---------------------------------------------------------------- runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

# Nada é instalado: a imagem do Temurin já traz o banco de fusos (a aplicação opera em horário
# de Brasília, e RN-04 e RN-06 dependem do relógio) e o busybox já traz wget e adduser.
RUN addgroup -S eventos && adduser -S -G eventos eventos

WORKDIR /app

COPY --from=build --chown=eventos:eventos /build/target/extraido/dependencies/ ./
COPY --from=build --chown=eventos:eventos /build/target/extraido/spring-boot-loader/ ./
COPY --from=build --chown=eventos:eventos /build/target/extraido/snapshot-dependencies/ ./
COPY --from=build --chown=eventos:eventos /build/target/extraido/application/ ./

# As fotos de evento vivem aqui; em produção monte um volume neste caminho (seção 13.3).
RUN mkdir -p /app/uploads && chown eventos:eventos /app/uploads
VOLUME /app/uploads

USER eventos
EXPOSE 8080

ENV TZ=America/Sao_Paulo \
    UPLOAD_DIR=/app/uploads \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --quiet --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
