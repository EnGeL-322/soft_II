# PAQUETE DE CONTEXTO PARA DESARROLLO — HUATA LÁCTEOS

Este ZIP está preparado para entregar el proyecto a otro desarrollador o a otra IA sin perder el contexto acumulado.

## Orden de autoridad de las fuentes

1. **`01_FUENTE_PRINCIPAL/Huata_Cronograma_y_Requerimientos_FINAL.xlsx`**
   - Es la fuente de implementación principal.
   - Contiene interesados, reglas de negocio en lenguaje amigable, requerimientos, casos de prueba, trazabilidad y cronograma EDT.
   - Si otro archivo contradice a este, primero revisar `03_CONTEXTO_DESARROLLO/02_DECISIONES_Y_PENDIENTES.md`.

2. **`02_FUENTES_OFICIALES/Cronograma_2026-2.xlsx`** y **`Cronograma_EDT_Historias_Usuario.txt`**
   - Son la referencia de fechas, Sprints, EDT, tareas e Historias de Usuario.
   - No mover fechas o HU sin control de cambio.

3. **`02_FUENTES_OFICIALES/Requerimientos_base_oficial.xlsx`**
   - Fuente base del conjunto oficial de RF/RNF antes de la expansión operativa.

4. **`03_CONTEXTO_DESARROLLO/*`**
   - Explica decisiones, flujos, arquitectura, UI/UX y datos pendientes de confirmar.

5. **`04_HISTORICO_REFERENCIA/*`**
   - Solo referencia histórica. **NO debe reemplazar al Excel final**.

## Regla importante para otra IA

No inventar valores internos de Huata. Cuando un dato esté marcado como “POR CONFIRMAR HUATA”, “POR DEFINIR HUATA” o equivalente, mantenerlo parametrizable y pedir validación al cliente.

## Conflicto conocido de alcance

El Project Charter original excluye “precios de leche” de la versión inicial y también excluye pasarelas de pago. Sin embargo, durante el levantamiento operativo se trabajó con S/ 1.75/L para leche normal y S/ 1.30/L para leche observada/baja calidad. En la versión final estos precios se conservan **como parámetros configurables pendientes de confirmación**, no como constantes de código.

## Fechas del proyecto

- Inicio: 19/08/2026
- Sprint 1: 19/08–25/08
- Sprint 2: 26/08–01/09
- Sprint 3: 02/09–08/09
- Sprint 4: 09/09–15/09
- Sprint 5: 16/09–22/09
- Sprint 6: 23/09–29/09
- Sprint 7: 30/09–06/10
- Sprint 8: 07/10–13/10
- Entrega: 14/10/2026
- Cierre administrativo: 15/10/2026

## Tecnologías objetivo

- Web: Angular
- Móvil: Flutter / Android APK
- Backend: Java 21 + Spring Boot + API REST
- Base de datos servidor: PostgreSQL
- Base local móvil: SQLite
- Seguridad: Spring Security + JWT + HTTPS
- Notificaciones móviles previstas: Firebase Cloud Messaging
- Control de versiones: Git / GitFlow
