#!/bin/sh
# Sobe o banco e então o backend, nesta ordem.
#
# O Playwright inicia o webServer antes do globalSetup, então deixar o banco a cargo do
# globalSetup fazia o backend tentar conectar num PostgreSQL que ainda não existia. Aqui a
# ordem é explícita e vale igual na máquina de quem desenvolve e no CI.
#
# Com E2E_SEM_DOCKER=1 o banco é considerado responsabilidade de quem chama — um serviço do
# CI, por exemplo.
set -e

if [ "${E2E_SEM_DOCKER}" != "1" ]; then
  echo "[e2e] subindo o PostgreSQL com docker compose…"
  docker compose up -d --wait
fi

exec ./mvnw -q -B spring-boot:run -Dspring-boot.run.profiles=dev
