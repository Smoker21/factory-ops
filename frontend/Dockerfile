# ======================================================
# Factory Ops Frontend — Production Dockerfile
# Multi-stage: Node build → nginx static file serving
# ======================================================

# Stage 1: Build
FROM node:20-alpine AS build

WORKDIR /app

# Copy package files first for better layer caching
COPY package.json package-lock.json ./
RUN npm ci --prefer-offline

# Copy source and build
# VITE_API_BASE_URL=/api/v1 routes through nginx proxy to backend.
# This avoids baking a hostname into the image.
COPY . .
ARG VITE_API_BASE_URL=/api/v1
ARG VITE_USE_MSW=false
RUN VITE_API_BASE_URL=${VITE_API_BASE_URL} VITE_USE_MSW=${VITE_USE_MSW} npm run build

# Stage 2: Serve with nginx
FROM nginx:1.27-alpine AS runtime

# Remove default nginx config
RUN rm /etc/nginx/conf.d/default.conf

# Copy SPA nginx config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy built assets
COPY --from=build /app/dist /usr/share/nginx/html

# Non-root user (nginx image already has nginx user)
RUN chown -R nginx:nginx /usr/share/nginx/html && \
    chown -R nginx:nginx /var/cache/nginx && \
    chown -R nginx:nginx /var/log/nginx && \
    touch /var/run/nginx.pid && \
    chown nginx:nginx /var/run/nginx.pid

USER nginx

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://localhost:80/ || exit 1
