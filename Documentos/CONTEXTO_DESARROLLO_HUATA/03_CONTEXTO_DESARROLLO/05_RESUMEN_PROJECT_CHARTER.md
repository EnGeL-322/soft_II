# 05 — RESUMEN FIEL DEL PROJECT CHARTER 2026

Fuente: `ProjectCharter2026.pdf` (archivo original consultado durante el proyecto; este ZIP incluye un resumen porque el PDF original no está montado actualmente como archivo local).

## Proyecto

**Desarrollo e Implementación de dos sistemas web y móvil para el acopio de leche en Huata.**

Organización ejecutora: JPK-RESOLUTION S.A.C.

## Tecnologías declaradas

- Angular: frontend web.
- Flutter: aplicación móvil.
- Spring Boot: backend.
- PostgreSQL: base de datos.
- SQLite: almacenamiento móvil offline.
- API REST.
- JWT.

## Problema

El proceso actual depende de papel, libretas y hojas de cálculo, provocando pérdida de información, errores de liquidación y falta de trazabilidad.

## Alcance principal

- plataforma web administrativa;
- app móvil para acopiadores;
- gestión de productores, acopiadores, rutas y centros de acopio;
- recojo de leche offline-first;
- control de calidad: densidad, acidez, temperatura;
- cálculo de liquidaciones;
- reportes/dashboard;
- autenticación y usuarios/roles;
- capacitación;
- despliegue;
- manual de usuario y manual técnico;
- soporte inicial.

## Objetivos específicos

- reducir al menos 74% el uso de procesos manuales;
- registrar recojos sin conexión;
- mejorar trazabilidad;
- automatizar liquidaciones basadas en cantidad/calidad;
- generar reportes por ruta, productor y periodo.

## Fechas

- Inicio: 19/08/2026.
- Término indicado por Charter: 14/10/2026.
- Duración aproximada: 8 Sprints.

## Exclusiones importantes

- solo Android vía APK;
- no pasarelas de pago/transacciones financieras;
- el Charter indica que los precios de leche no se incluyen en esa versión;
- no marketing;
- no mejoras físicas de Internet/dispositivos/energía;
- no soporte más allá del primer mes inicial;
- no funcionalidades ajenas a la cadena de acopio.

## Riesgos destacados

- caídas del servidor;
- errores en registro offline;
- compatibilidad móvil;
- mala capacitación;
- uso incorrecto;
- retraso del cliente en información;
- cambios frecuentes en requisitos;
- acceso no autorizado;
- pérdida de datos en migración;
- caída de Internet en Huata;
- baja adopción.

## Factores críticos de éxito

- información oportuna de productores/acopiadores;
- interfaz intuitiva;
- offline y sincronización correctos;
- adopción de usuarios;
- estabilidad, seguridad y rendimiento en campo.
