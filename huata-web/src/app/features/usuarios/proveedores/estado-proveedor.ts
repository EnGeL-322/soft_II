import { EstadoProductor } from '../../../core/models/models';
import { NombreIcono } from '../../../shared/ui/icono.component';

/**
 * Destinos que la máquina de estados de 7.3 permite desde cada estado. Es una copia de
 * EstadoProductor.java y solo sirve para no ofrecer botones que el backend va a rechazar: quien
 * valida la transición es el service, siempre.
 */
const TRANSICIONES: Record<EstadoProductor, EstadoProductor[]> = {
  PENDIENTE_APROBACION: ['ACTIVO', 'INACTIVO'],
  ACTIVO: ['EN_REVISION', 'INACTIVO', 'RETIRADO'],
  EN_REVISION: ['ACTIVO', 'INACTIVO', 'RETIRADO'],
  INACTIVO: ['ACTIVO'],
  RETIRADO: [],
};

const VERBO: Record<EstadoProductor, string> = {
  PENDIENTE_APROBACION: 'Dejar pendiente',
  ACTIVO: 'Aprobar / activar',
  EN_REVISION: 'Poner en revisión',
  INACTIVO: 'Inactivar',
  RETIRADO: 'Retirar',
};

const ICONO: Record<EstadoProductor, NombreIcono> = {
  PENDIENTE_APROBACION: 'produccion',
  ACTIVO: 'activar',
  EN_REVISION: 'buscar',
  INACTIVO: 'pausa',
  RETIRADO: 'bloquear',
};

/** Opciones del filtro de estado, en el orden del ciclo de vida. */
export const ESTADOS_PROVEEDOR: { valor: EstadoProductor; etiqueta: string }[] = [
  { valor: 'PENDIENTE_APROBACION', etiqueta: 'Pendientes de aprobar' },
  { valor: 'ACTIVO', etiqueta: 'Activos' },
  { valor: 'EN_REVISION', etiqueta: 'En revisión' },
  { valor: 'INACTIVO', etiqueta: 'Inactivos' },
  { valor: 'RETIRADO', etiqueta: 'Retirados' },
];

export const destinosDe = (estado: EstadoProductor): EstadoProductor[] => TRANSICIONES[estado] ?? [];
export const verboDe = (estado: EstadoProductor): string => VERBO[estado];
export const iconoDe = (estado: EstadoProductor): NombreIcono => ICONO[estado];
