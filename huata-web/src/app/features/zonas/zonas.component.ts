import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ZonaAsignacion } from '../../core/models/models';
import { RutasService } from '../../core/services/rutas.service';
import { recargarEnVivo } from '../../core/services/cambios-tiempo-real.service';

interface ZonaAsignada {
  nombre: string;
  rutas: ZonaAsignacion[];
  proveedores: ZonaAsignacion['proveedores'];
  acopiadores: ZonaAsignacion['acopiadores'];
}

/** Lo que se acaba de guardar en Usuarios: se abre su zona y se resaltan su ruta y la persona. */
interface Recien {
  zona: string;
  rutaId: number;
  acopiadorId?: number;
  proveedorId?: number;
  texto: string;
}

@Component({
  selector: 'app-zonas',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './zonas.component.html',
  styleUrl: './zonas.component.css',
})
export class ZonasComponent implements OnInit {
  /** Tiempo real: si alguien guarda algo de rutas, proveedores, acopiadores, esta pantalla se actualiza sola. */
  private readonly _enVivo = recargarEnVivo(['rutas', 'proveedores', 'acopiadores'], () => this.rutasService.zonasAsignadas().subscribe({ next: (a) => this.asignaciones.set(a) }));
  private readonly ruta = inject(ActivatedRoute);
  readonly asignaciones = signal<ZonaAsignacion[]>([]);
  /** ?ruta= con &acopiador= o &proveedor= : llega desde su ficha en Usuarios al crearlo o editarlo. */
  readonly recien = signal<Recien | null>(null);
  readonly error = signal('');
  zonaAbierta = '';
  busqueda = '';

  constructor(private readonly rutasService: RutasService) {}

  ngOnInit(): void {
    this.rutasService.zonasAsignadas().subscribe({
      next: (asignaciones) => {
        this.asignaciones.set(asignaciones);
        this.mostrarRecien();
        this.abrirPrimeraZona();
      },
      error: () => this.error.set('No se pudieron cargar las asignaciones por zona.'),
    });
  }

  get zonas(): ZonaAsignada[] {
    const texto = this.busqueda.trim().toLocaleLowerCase('es');
    const nombres = [...new Set(this.asignaciones().map((asignacion) => asignacion.zona))].sort((a, b) =>
      a.localeCompare(b, 'es'),
    );
    return nombres
      .map((nombre) => {
        const rutas = this.asignaciones().filter((asignacion) => asignacion.zona === nombre);
        return {
          nombre,
          rutas,
          proveedores: rutas
            .flatMap((ruta) => ruta.proveedores)
            .sort((a, b) => (a.ordenEnRuta ?? 999999) - (b.ordenEnRuta ?? 999999)),
          acopiadores: rutas.flatMap((ruta) => ruta.acopiadores),
        };
      })
      .filter(
        (zona) =>
          !texto ||
          zona.nombre.toLocaleLowerCase('es').includes(texto) ||
          zona.rutas.some((ruta) => ruta.rutaNombre.toLocaleLowerCase('es').includes(texto)) ||
          zona.proveedores.some((proveedor) =>
            `${proveedor.codigo} ${proveedor.nombres} ${proveedor.apellidos}`
              .toLocaleLowerCase('es')
              .includes(texto),
          ) ||
          zona.acopiadores.some((acopiador) =>
            `${acopiador.nombres} ${acopiador.apellidos}`.toLocaleLowerCase('es').includes(texto),
          ),
      );
  }

  get totalProveedores(): number {
    return this.asignaciones().reduce((total, ruta) => total + ruta.proveedores.length, 0);
  }

  get totalAcopiadores(): number {
    return this.asignaciones().reduce((total, ruta) => total + ruta.acopiadores.length, 0);
  }

  alternar(nombre: string): void {
    this.zonaAbierta = this.zonaAbierta === nombre ? '' : nombre;
  }

  private mostrarRecien(): void {
    const params = this.ruta.snapshot.queryParamMap;
    const rutaId = Number(params.get('ruta'));
    const acopiadorId = Number(params.get('acopiador')) || undefined;
    const proveedorId = Number(params.get('proveedor')) || undefined;
    const asignacion = this.asignaciones().find((a) => a.rutaId === rutaId);
    if (!asignacion) return;
    const lugar = `${asignacion.zona} · ${asignacion.rutaNombre}`;
    const acopiador = asignacion.acopiadores.find((a) => a.id === acopiadorId);
    const proveedor = asignacion.proveedores.find((p) => p.id === proveedorId);
    this.recien.set({
      zona: asignacion.zona,
      rutaId,
      acopiadorId,
      proveedorId,
      texto: acopiador
        ? `${acopiador.nombres} ${acopiador.apellidos} quedó asignado a ${lugar}.`
        : proveedor
          ? `${proveedor.nombres} ${proveedor.apellidos} quedó en ${lugar}. Ubícalo en el mapa para darle su orden de visita.`
          : `Ruta ${lugar}.`,
    });
    this.zonaAbierta = asignacion.zona;
    // Tras pintar la zona abierta, se lleva la vista a la ruta resaltada.
    setTimeout(() => document.getElementById(`ruta-${rutaId}`)?.scrollIntoView({ block: 'center', behavior: 'smooth' }));
  }

  private abrirPrimeraZona(): void {
    if (!this.zonaAbierta) {
      this.zonaAbierta = this.zonas[0]?.nombre ?? '';
    }
  }
}
