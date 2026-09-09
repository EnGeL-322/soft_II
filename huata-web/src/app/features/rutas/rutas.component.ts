import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  Acopiador,
  AsignacionRuta,
  CentroAcopio,
  PlanRutaDia,
  Recojo,
  Ruta,
  UbicacionGps,
} from '../../core/models/models';
import { AcopiadoresService } from '../../core/services/acopiadores.service';
import { ExportacionesService } from '../../core/services/exportaciones.service';
import { RecojoService } from '../../core/services/recojo.service';
import { RutasService } from '../../core/services/rutas.service';
import { UbicacionesService } from '../../core/services/ubicaciones.service';
import { MapaComponent, PuntoMapa } from '../../shared/ui/mapa.component';

type Tab = 'gps' | 'plan' | 'rutas' | 'centros';

function fechaLocal(): string {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
}

function relativo(fechaIso: string | undefined): string {
  if (!fechaIso) return '—';
  const ms = Date.now() - new Date(fechaIso).getTime();
  const min = Math.round(ms / 60_000);
  if (min < 1) return 'hace instantes';
  if (min < 60) return `hace ${min} min`;
  const horas = Math.round(min / 60);
  if (horas < 24) return `hace ${horas} h`;
  return `hace ${Math.round(horas / 24)} d`;
}

/**
 * Rutas y movilidad: seguimiento GPS real (Leaflet + posiciones reportadas por el navegador del
 * acopiador, sin simulación) y plan operativo con datos reales de Ruta + AsignacionRuta + Recojo
 * — reemplaza la "movilidad de testeo" y el mapa esquemático DEMO del prototipo por lo que el
 * sistema realmente sabe.
 */
@Component({
  selector: 'app-rutas',
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe, MapaComponent],
  templateUrl: './rutas.component.html',
})
export class RutasComponent implements OnInit {
  readonly tab = signal<Tab>('gps');
  readonly rutas = signal<Ruta[]>([]);
  readonly centros = signal<CentroAcopio[]>([]);
  readonly acopiadores = signal<Acopiador[]>([]);
  readonly plan = signal<PlanRutaDia[]>([]);
  readonly asignacionesHoy = signal<AsignacionRuta[]>([]);
  readonly ultimasUbicaciones = signal<UbicacionGps[]>([]);
  readonly eventosRecorrido = signal<UbicacionGps[]>([]);
  readonly zonasRecorridasSignal = signal<string[]>([]);

  readonly hoy = fechaLocal();
  fecha = fechaLocal();

  readonly rutaSeleccionadaId = signal<number | undefined>(undefined);
  readonly verTodos = signal(true);

  readonly editandoRutaId = signal<number | null>(null);
  formAsignar: { acopiadorId?: number; movilidad?: string } = {};
  formEditar: { movilidad?: string; observacion?: string } = {};
  observacionEdicion = '';

  nuevaRuta = { nombre: '', zona: '', descripcion: '' };
  nuevoCentro: { nombre: string; ubicacion: string; rutaId?: number } = { nombre: '', ubicacion: '' };

  readonly relativo = relativo;

  readonly completadas = computed(() => this.plan().filter((p) => p.estado === 'CON_REGISTROS').length);
  readonly enRecorrido = computed(() => this.plan().filter((p) => p.estado === 'ASIGNADA').length);
  readonly litrosTotales = computed(() => this.plan().reduce((s, p) => s + p.litros, 0));

  readonly gpsActivos = computed(
    () => this.ultimasUbicaciones().filter((u) => Date.now() - new Date(u.fecha).getTime() < 30 * 60_000).length,
  );
  readonly ultimaActualizacionGps = computed(() => {
    const lista = [...this.ultimasUbicaciones()].sort((a, b) => (a.fecha < b.fecha ? 1 : -1));
    return lista[0]?.fecha;
  });

  readonly rutaSeleccionada = computed(() => this.plan().find((p) => p.rutaId === this.rutaSeleccionadaId()) ?? null);
  readonly acopiadorAsignado = computed(() => this.rutaSeleccionada()?.acopiadorId ?? null);
  readonly asignacionSeleccionada = computed(
    () => this.asignacionesHoy().find((a) => a.rutaId === this.rutaSeleccionadaId()) ?? null,
  );

  readonly entidadSeleccionada = computed(() => {
    const r = this.rutaSeleccionada();
    if (!r) return this.verTodos() ? 'Todas las rutas' : 'Selecciona una ruta';
    return r.acopiadorNombre ?? 'Sin asignar';
  });

  readonly ultimaUbicacionSeleccionada = computed(() => {
    const acopiadorId = this.acopiadorAsignado();
    if (!acopiadorId) return null;
    return this.ultimasUbicaciones().find((u) => u.acopiadorId === acopiadorId) ?? null;
  });

  readonly gpsVigente = computed(() => {
    const u = this.ultimaUbicacionSeleccionada();
    return !!u && Date.now() - new Date(u.fecha).getTime() < 30 * 60_000;
  });

  readonly zonasRecorridas = computed(() => this.zonasRecorridasSignal());

  readonly puntosMapa = computed<PuntoMapa[]>(() => {
    const base = this.verTodos()
      ? this.ultimasUbicaciones()
      : this.ultimasUbicaciones().filter((u) => u.acopiadorId === this.acopiadorAsignado());
    return base.map((u) => ({
      id: u.acopiadorId,
      lat: u.latitud,
      lng: u.longitud,
      etiqueta: u.acopiadorNombre,
      detalle: 'Actualizado: ' + new Date(u.fecha).toLocaleString(),
    }));
  });

  constructor(
    private readonly rutasService: RutasService,
    private readonly acopiadoresService: AcopiadoresService,
    private readonly ubicacionesService: UbicacionesService,
    private readonly recojoService: RecojoService,
    private readonly exportacionesService: ExportacionesService,
  ) {}

  ngOnInit(): void {
    this.acopiadoresService.listar('', 0, 200).subscribe((pg) => this.acopiadores.set(pg.content));
    this.cargarRutas();
    this.cargarCentros();
    this.cargarTodo();
  }

  cargarTodo(): void {
    this.rutasService.asignaciones(this.fecha).subscribe((a) => this.asignacionesHoy.set(a));
    this.ubicacionesService.ultimas().subscribe((u) => this.ultimasUbicaciones.set(u));
    this.rutasService.planOperativo(this.fecha).subscribe((p) => {
      this.plan.set(p);
      // Recien aqui "rutaSeleccionada()" ve el plan del dia correcto (evita leer el plan anterior
      // mientras la peticion todavia esta en vuelo).
      this.seleccionarRuta();
    });
  }

  cambiarRutaSeleccionada(id: number | undefined): void {
    this.rutaSeleccionadaId.set(id);
    this.seleccionarRuta();
  }

  seleccionarRuta(): void {
    this.observacionEdicion = this.asignacionSeleccionada()?.observacion ?? '';
    const acopiadorId = this.acopiadorAsignado();
    if (!acopiadorId) {
      this.eventosRecorrido.set([]);
      this.zonasRecorridasSignal.set([]);
      return;
    }
    const desde = new Date(this.fecha + 'T00:00:00').toISOString();
    const hasta = new Date(this.fecha + 'T23:59:59').toISOString();
    this.ubicacionesService.historial(acopiadorId, desde, hasta).subscribe((eventos) => this.eventosRecorrido.set(eventos));
    this.recojoService.listar({ acopiadorId, desde, hasta, size: 100 }).subscribe((pg) => {
      const zonas = new Set<string>();
      pg.content.forEach((r: Recojo) => {
        if (r.rutaNombre) zonas.add(r.rutaNombre);
      });
      this.zonasRecorridasSignal.set([...zonas]);
    });
  }

  guardarObservacion(asig: AsignacionRuta): void {
    this.rutasService.actualizarAsignacion(asig.id, { observacion: this.observacionEdicion }).subscribe(() => this.cargarTodo());
  }

  alternarEdicion(p: PlanRutaDia): void {
    if (this.editandoRutaId() === p.rutaId) {
      this.editandoRutaId.set(null);
      return;
    }
    this.editandoRutaId.set(p.rutaId);
    this.formAsignar = {};
    this.formEditar = { movilidad: p.movilidad, observacion: p.observacion };
  }

  asignar(p: PlanRutaDia): void {
    if (!this.formAsignar.acopiadorId) return;
    this.rutasService
      .asignar({ rutaId: p.rutaId, acopiadorId: this.formAsignar.acopiadorId, fecha: this.fecha, movilidad: this.formAsignar.movilidad })
      .subscribe(() => {
        this.editandoRutaId.set(null);
        this.cargarTodo();
      });
  }

  guardarPlan(p: PlanRutaDia): void {
    if (!p.asignacionId) return;
    this.rutasService.actualizarAsignacion(p.asignacionId, this.formEditar).subscribe(() => {
      this.editandoRutaId.set(null);
      this.cargarTodo();
    });
  }

  exportar(): void {
    this.exportacionesService.descargar('Rutas').subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'Huata_Rutas.xlsx';
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  private cargarRutas(): void {
    this.rutasService.listar().subscribe((pg) => this.rutas.set(pg.content));
  }

  private cargarCentros(): void {
    this.rutasService.listarCentros().subscribe((pg) => this.centros.set(pg.content));
  }

  crearRuta(): void {
    this.rutasService.crear(this.nuevaRuta).subscribe(() => {
      this.nuevaRuta = { nombre: '', zona: '', descripcion: '' };
      this.cargarRutas();
      this.cargarTodo();
    });
  }

  crearCentro(): void {
    this.rutasService.crearCentro(this.nuevoCentro).subscribe(() => {
      this.nuevoCentro = { nombre: '', ubicacion: '' };
      this.cargarCentros();
    });
  }
}
