# Staging/prod — Nginx public :80 (CSR static + article HTML via BFF).
#
# Browser → Nginx:80
#   /news/:slug.html → proxy_pass → service.news render
#   /news/:slug      → 301 to .html
#   other routes     → static index.html (CSR)
#   *.js / *.css     → static from disk

# ---- Build Angular (browser bundle only; no Angular SSR) ----
FROM node:18 AS builder

ARG TARGET_ENV=build

WORKDIR /app

COPY package.json yarn.lock ./
RUN yarn install

COPY . .

ENV NODE_OPTIONS="--max-old-space-size=2048"
RUN yarn $TARGET_ENV

# ---- Runtime: Nginx only ----
FROM node:18-slim AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends nginx \
    && rm -rf /var/lib/apt/lists/* \
    && rm -f /etc/nginx/sites-enabled/default /etc/nginx/conf.d/default.conf

WORKDIR /app

COPY --from=builder /app/dist/mt-ticket /usr/share/nginx/html/mt-ticket

COPY nginx.conf /etc/nginx/conf.d/default.conf

COPY docker/entrypoint.sh /entrypoint.sh
RUN sed -i 's/\r$//' /entrypoint.sh \
    && chmod +x /entrypoint.sh

ENV NODE_ENV=production

EXPOSE 80

CMD ["sh", "/entrypoint.sh"]
