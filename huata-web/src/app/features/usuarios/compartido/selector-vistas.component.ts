import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, input, output, signal } from '@angular/core';
import { ModuloAcceso, VistaAcceso } from '../../../core/models/models';
import { contarPantallas } from './catalogos';

/** Encender o apagar un permiso. Quien recibe decide qué significa (rol o persona). */
export interface CambioPermiso {
  permiso: string;
  activo: boolean;
}

/**
 * Cuánto se puede hacer en una vista. "parcial" = solo algunas acciones (p. ej. vender pero no anular);
 * no se elige con un botón, se llega ajustando las acciones.
 */
export type Nivel = 'no' | 'ver' | 'todo' | 'parcial';

interface GrupoVistas {
  modulo: string;
  vistas: VistaAcceso[];
}

/**
 * Cada vista con tres botones: No · Ver · Todo (No · Ver si la vista no tiene nada más que ver). Así se
 * entiende sin conocer los permisos; el detalle de acciones (vender sí, anular no) queda a un clic en
 * "Ajustar", y se abre solo cuando la vista ya está a medida. Dos usos:
 *  - Ficha de una persona (`base` = lo que dan sus roles): arriba las vistas del rol; abajo, plegadas,
 *    las demás para dárselas solo a ella. Marca lo "extra", lo "quitado" y lo "ajustado".
 *  - Editor de un rol (`base` = null): todas las vistas en una sola lista.
 *
 * No decide nada: emite qué encender o apagar y quien lo usa aplica su semántica. Encender una acción
 * enciende la vista; apagar la vista apaga sus acciones, para no dejar permisos que no se pueden usar.
 */
@Component({
  selector: 'app-selector-vistas',
  standalone: true,
  imports: [NgTemplateOutlet],
  templateUrl: './selector-vistas.component.html',
  styleUrl: './selector-vistas.component.css',
})
export class SelectorVistasComponent {
  readonly catalogo = input.required<ModuloAcceso[]>();
  /** Lo que dan los roles elegidos. null = se está editando el rol mismo. */
  readonly base = input<ReadonlySet<string> | null>(null);
  /** Lo que tendrá de verdad (roles + extras − quitados). */
  readonly seleccion = input.required<ReadonlySet<string>>();
  readonly soloLectura = input(false);
  readonly cambios = output<CambioPermiso[]>();

  readonly verOtras = signal(false);
  /** Vistas con el detalle de acciones abierto a pedido. */
  private readonly abiertas = signal<ReadonlySet<string>>(new Set());

  readonly pantallas = computed(() => contarPantallas(this.catalogo(), this.seleccion()));
  readonly totalPantallas = computed(() =>
    contarPantallas(this.catalogo(), this.catalogo().flatMap((m) => m.vistas.map((v) => v.permisoAcceso))),
  );
  readonly modoRol = computed(() => this.base() === null);

  /**
   * Vistas que algún rol elegido deja abrir. Una vista de la que el rol solo trae una acción suelta
   * (sin poder entrar) va con las demás: no se ve en el menú, así que no es "del rol".
   */
  readonly delRol = computed(() => this.agrupar((v) => this.vieneDelRol(v)));
  readonly otras = computed(() => this.agrupar((v) => !this.vieneDelRol(v)));
  readonly extrasActivas = computed(() =>
    this.otras().reduce((n, g) => n + g.vistas.filter((v) => this.tiene(v.permisoAcceso)).length, 0),
  );
  readonly quitadas = computed(() =>
    this.delRol().reduce((n, g) => n + g.vistas.filter((v) => !this.tiene(v.permisoAcceso)).length, 0),
  );

  tiene(permiso: string): boolean {
    return this.seleccion().has(permiso);
  }

  private vieneDelRol(v: VistaAcceso): boolean {
    const base = this.base();
    return base === null || base.has(v.permisoAcceso);
  }

  /** Acciones además de entrar a la vista. */
  acciones(v: VistaAcceso) {
    return v.acciones.filter((a) => a.permiso !== v.permisoAcceso);
  }

  nivel(v: VistaAcceso): Nivel {
    if (!this.tiene(v.permisoAcceso)) return 'no';
    const acciones = this.acciones(v);
    const activas = acciones.filter((a) => this.tiene(a.permiso)).length;
    if (activas === 0) return 'ver';
    return activas === acciones.length ? 'todo' : 'parcial';
  }

  /** Solo vale la pena ajustar acción por acción cuando hay más de una. */
  ajustable(v: VistaAcceso): boolean {
    return this.acciones(v).length > 1;
  }

  detalleAbierto(v: VistaAcceso): boolean {
    return this.ajustable(v) && (this.abiertas().has(v.codigo) || this.nivel(v) === 'parcial');
  }

  alternarDetalle(v: VistaAcceso): void {
    const nuevas = new Set(this.abiertas());
    if (nuevas.has(v.codigo)) nuevas.delete(v.codigo);
    else nuevas.add(v.codigo);
    this.abiertas.set(nuevas);
  }

  /** Resumen para la persona: en qué difiere esta vista de lo que da su rol. */
  marca(v: VistaAcceso): 'extra' | 'quitada' | 'ajustada' | null {
    const base = this.base();
    if (base === null) return null;
    const enBase = base.has(v.permisoAcceso);
    const ahora = this.tiene(v.permisoAcceso);
    if (enBase && !ahora) return 'quitada';
    if (!enBase && ahora) return 'extra';
    if (ahora && this.acciones(v).some((a) => base.has(a.permiso) !== this.tiene(a.permiso))) return 'ajustada';
    return null;
  }

  esExtra(permiso: string): boolean {
    const base = this.base();
    return base !== null && this.tiene(permiso) && !base.has(permiso);
  }

  esQuitado(permiso: string): boolean {
    const base = this.base();
    return base !== null && !this.tiene(permiso) && base.has(permiso);
  }

  fijarNivel(v: VistaAcceso, nivel: 'no' | 'ver' | 'todo'): void {
    if (this.soloLectura() || this.nivel(v) === nivel) return;
    const cambios: CambioPermiso[] = [];
    const poner = (permiso: string, activo: boolean) => {
      if (this.tiene(permiso) !== activo) cambios.push({ permiso, activo });
    };
    poner(v.permisoAcceso, nivel !== 'no');
    this.acciones(v).forEach((a) => poner(a.permiso, nivel === 'todo'));
    if (cambios.length) this.cambios.emit(cambios);
  }

  alternarVista(v: VistaAcceso): void {
    this.fijarNivel(v, this.tiene(v.permisoAcceso) ? 'no' : 'ver');
  }

  alternarAccion(v: VistaAcceso, permiso: string): void {
    if (this.soloLectura()) return;
    const activo = !this.tiene(permiso);
    const cambios: CambioPermiso[] = [{ permiso, activo }];
    if (activo && !this.tiene(v.permisoAcceso)) cambios.push({ permiso: v.permisoAcceso, activo: true });
    this.cambios.emit(cambios);
  }

  private agrupar(incluir: (v: VistaAcceso) => boolean): GrupoVistas[] {
    return this.catalogo()
      .map((m) => ({ modulo: m.nombre, vistas: m.vistas.filter(incluir) }))
      .filter((g) => g.vistas.length > 0);
  }
}
