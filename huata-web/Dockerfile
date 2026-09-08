# --- Etapa 1: build (Angular) ---
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci

COPY . .
RUN npm run build -- --configuration production

# --- Etapa 2: runtime (nginx sirve el estatico y hace de proxy hacia el backend) ---
FROM nginx:1.27-alpine
COPY --from=build /app/dist/huata-web/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
