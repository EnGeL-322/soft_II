# --- Etapa 1: build (Flutter Web) ---
# Solo para poder navegar en un contenedor los flujos que NO dependen de plugins nativos (login,
# listas, formularios) — GPS real, cámara y OCR on-device solo se verifican compilando el APK/IPA
# en una máquina con Android Studio/Xcode, no en este build. Ver la nota larga sobre esto en el
# resumen de la fase movil.
FROM ghcr.io/cirruslabs/flutter:stable AS build
WORKDIR /app

COPY pubspec.yaml pubspec.lock* ./
RUN flutter pub get

COPY . .
RUN flutter build web --release

# --- Etapa 2: runtime (nginx sirve el estatico y hace de proxy hacia el backend) ---
FROM nginx:1.27-alpine
COPY --from=build /app/build/web /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
