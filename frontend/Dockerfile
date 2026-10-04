# syntax=docker/dockerfile:1

# ---------------------------------------------------------------- build
FROM node:22-alpine AS build
WORKDIR /build

# Dependências primeiro: enquanto o lockfile não mudar, esta camada é reaproveitada.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# O build roda o tsc antes do Vite, então um erro de tipo derruba a imagem em vez de virar
# bundle quebrado em produção.
RUN npm run build

# ---------------------------------------------------------------- runtime
FROM nginx:1.27-alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY seguranca.conf /etc/nginx/snippets/seguranca.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --quiet --spider http://localhost/ || exit 1
