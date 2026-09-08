/**
 * URL relativa a propósito: en Docker, nginx (ver huata-web/nginx.conf) hace de proxy reverso de
 * "/api/" hacia el backend, y en desarrollo local "ng serve" hace lo mismo via proxy.conf.json.
 * Así el codigo del frontend es identico en ambos entornos (mismo origen, sin CORS).
 */
export const API_BASE_URL = '/api/v1';
