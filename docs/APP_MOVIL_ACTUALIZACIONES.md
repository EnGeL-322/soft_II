# App móvil: parches y versiones nuevas sin reinstalar a mano

La app se entrega como **APK directo** (sin Play Store). Hay dos formas de que un cambio llegue a los
teléfonos, según qué se cambió:

| Qué cambió | Cómo llega | ¿La persona hace algo? |
|---|---|---|
| Código Dart: pantallas, textos, reglas, colores, la mayoría de los cambios | **Parche de Shorebird** | No. Se baja solo al abrir la app y se aplica la próxima vez que se abre. |
| Algo nativo: un plugin nuevo, permisos de Android, versión de Flutter, ícono | **APK nuevo** publicado en el servidor | Toca "Actualizar" en la app y confirma la instalación. |

> Shorebird solo puede parchar una app que se instaló desde un APK hecho con `shorebird release`.
> Los teléfonos que hoy tienen un APK hecho con `flutter build apk` tienen que instalar **una vez**
> el primer APK de Shorebird. Desde ahí, los cambios de código llegan solos.

---

## 1. Preparación (una sola vez)

Esto lo tiene que hacer una persona del equipo: requiere crear una cuenta en Shorebird.

```powershell
# Instalar la herramienta de Shorebird (PowerShell)
Set-ExecutionPolicy RemoteSigned -Scope CurrentUser
iwr -UseBasicParsing 'https://raw.githubusercontent.com/shorebirdtech/install/main/install.ps1' | iex

# Crear la cuenta / iniciar sesión (abre el navegador)
shorebird login

# Registrar la app. Crea huata_app/shorebird.yaml con el app_id: se versiona en git.
cd huata_app
shorebird init
shorebird doctor
```

`shorebird doctor` avisa si la versión de Flutter instalada no es compatible con Shorebird.

## 2. Publicar una versión nueva del APK (cambios nativos, o la primera vez)

1. Sube la versión en `huata_app/pubspec.yaml` (`version: 1.1.0+2`: el número antes del `+`).
2. Compila con Shorebird (reemplaza la URL por la del servidor real):

   ```powershell
   cd huata_app
   shorebird release android --artifact apk -- --dart-define=HUATA_API_BASE_URL=https://TU-DOMINIO/api/v1
   ```

3. Copia el APK que genera (`build\app\outputs\flutter-apk\app-release.apk`) a la carpeta
   `descargas\` de la raíz del proyecto con el nombre `huata.apk`. El servicio `frontend` la sirve en
   `https://TU-DOMINIO/descargas/huata.apk`.
4. En el `.env` del servidor:

   ```
   HUATA_APP_VERSION_ACTUAL=1.1.0
   HUATA_APP_URL_DESCARGA=https://TU-DOMINIO/descargas/huata.apk
   HUATA_APP_NOTAS=Mejoras en la ruta del día.
   # Solo si la versión vieja ya no debe usarse (p. ej. cambió algo del backend que la rompe):
   HUATA_APP_VERSION_MINIMA=1.1.0
   ```

5. `docker compose up -d backend` para que tome las variables.

Al abrir la app (o al volver a ella), quien tenga una versión menor ve la franja **"Hay una versión
nueva"** y el botón **Actualizar** en *Mi cuenta*. Si su versión es menor que la mínima, la app
muestra **"Actualiza la aplicación"** y no deja registrar nada hasta instalarla. Lo que tenga por
enviar sigue guardado en el teléfono y sale después de actualizar.

## 3. Publicar un parche (cambios solo de código Dart)

Sin tocar la versión de `pubspec.yaml`:

```powershell
cd huata_app
shorebird patch android -- --dart-define=HUATA_API_BASE_URL=https://TU-DOMINIO/api/v1
```

Usa siempre el mismo `--dart-define` que tuvo el release. Los teléfonos con ese release bajan el
parche la próxima vez que abren la app y lo aplican al abrirla otra vez. No hay que tocar `.env` ni
`descargas\`.

Si un parche sale mal, se puede revertir desde la consola de Shorebird (console.shorebird.dev) y los
teléfonos vuelven al código del release.

## Qué NO cubre

- **iOS:** este flujo está pensado para Android con APK directo. En iPhone los cambios nativos pasan
  por App Store/TestFlight.
- **Seguridad:** el endpoint `/api/v1/public/app-movil/version` es público a propósito (se consulta
  antes del login) y solo devuelve números de versión y la URL del APK. No pongas en `HUATA_APP_NOTAS`
  nada que no deba ver cualquiera.
