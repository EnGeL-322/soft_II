import { DatePipe, DecimalPipe } from '@angular/common';
import {
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Subject, catchError, forkJoin, interval, map, of, takeUntil } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { DialogoService } from '../../core/notificaciones/dialogo.service';
import { REGLAS } from '../../core/config/reglas-datos';
import { PERMISO } from '../../core/config/navegacion';
import { MODOS_RECORRIDO, PreferenciasMapaService } from '../../core/config/preferencias-mapa';
import {
  Acopiador,
  AsignacionRuta,
  PlanRutaDia,
  Productor,
  PuntoEstablecido,
  ProveedorDeRuta,
  Recojo,
  Ruta,
  RutaPunto,
  TrazadoAcopiador,
  UbicacionGps,
  TipoUbicacion,
  ZonaPoligono,
  ZonaPoligonoVersion,
  ZonaPoligonoPunto,
} from '../../core/models/models';
import { AcopiadorRequest, AcopiadoresService } from '../../core/services/acopiadores.service';
import { ProductoresService } from '../../core/services/productores.service';
import { ExportacionesService } from '../../core/services/exportaciones.service';
import { RecojoService } from '../../core/services/recojo.service';
import { RutasService } from '../../core/services/rutas.service';
import { PuntosEstablecidosService } from '../../core/services/puntos-establecidos.service';
import { TrazadosService } from '../../core/services/trazados.service';
import { UbicacionesService } from '../../core/services/ubicaciones.service';
import {
  EstadoSocket,
  UbicacionesTiempoRealService,
} from '../../core/services/ubicaciones-tiempo-real.service';
import { MapaComponent, PoligonoMapa, PuntoMapa } from '../../shared/ui/mapa.component';
import { BuscadorComponent, OpcionBuscador } from '../../shared/ui/buscador.component';
import { recargarEnVivo } from '../../core/services/cambios-tiempo-real.service';

type Tab = 'gps' | 'territorio' | 'puntos';
type ProveedorEnMapa = ProveedorDeRuta & { rutaId: number; rutaNombre: string };
export type ModoMapa = 'navegar' | 'vivienda' | 'ruta' | 'acopio' | 'zona';
export type SeccionPaso3 = 'vivienda' | 'trazado' | 'acopio' | 'recorridos';

/** Lo que compone una zona: su territorio dibujado, sus rutas y quién acopia en ellas. */
interface ResumenZona {
  zona: string;
  poligono?: ZonaPoligono;
  rutas: Ruta[];
  acopiadores: Acopiador[];
  proveedores: Productor[];
}

/**
 * "Hoy" en la zona operativa de Huata, no en la del navegador.
 *
 * El backend corta la jornada a medianoche de Lima y compara contra `LocalDate`, así que un
 * navegador en otra zona horaria pedía el plan, las asignaciones y los recorridos pintados de un día
 * distinto del que el servidor considera hoy, y la pantalla salía vacía sin explicación. El resto de
 * la aplicación (panel, calidad, conciliación) ya usaba Lima; esta pantalla era la que faltaba.
 */
function fechaLocal(): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Lima',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
}

/** Grupo para rutas que se quedaron sin zona; no es una zona real del negocio. */
const SIN_ZONA = '— Sin zona —';

/**
 * Antigüedad máxima de un punto GPS para considerar al acopiador "conectado". Pasado este tiempo sin
 * recibir posición deja de pintarse en el mapa. Mientras comparte GPS, la app reenvía su posición
 * cada 2 minutos aunque esté parado (latido en ruta_dia_screen.dart), así que una vuelta de horas
 * sigue visible; si cierra la app o apaga el GPS, desaparece a los pocos minutos.
 */
const VENTANA_GPS_VIVO_MS = 6 * 60_000;

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
  imports: [FormsModule, DatePipe, DecimalPipe, MapaComponent, BuscadorComponent],
  templateUrl: './rutas.component.html',
})
export class RutasComponent implements OnInit, OnDestroy {
  /** Tiempo real: si alguien guarda algo de rutas, acopio, proveedores, acopiadores, esta pantalla se actualiza sola. */
  private readonly _enVivo = recargarEnVivo(['rutas', 'acopio', 'proveedores', 'acopiadores'], () => {
    // Mientras se edita una ruta o un punto, recargar movería el mapa bajo el mouse.
    if (this.editandoRutaZonaId() != null || this.editandoPuntoEstablecidoId() != null) return;
    this.cargarRutas();
    this.cargarTodo();
    this.cargarPuntosEstablecidos();
  });
  /** Mismas reglas de DNI y nombre que el backend (ver reglas-datos.ts). */
  protected readonly reglas = REGLAS;

  readonly tab = signal<Tab>('gps');
  readonly rutas = signal<Ruta[]>([]);
  readonly acopiadores = signal<Acopiador[]>([]);
  /** Todo el padrón activo: de aquí salen los proveedores de cada zona y los que se pueden traer. */
  readonly proveedores = signal<Productor[]>([]);
  readonly plan = signal<PlanRutaDia[]>([]);
  readonly buscandoRuta = signal('');
  readonly cargandoPlan = signal(false);
  readonly errorPlan = signal('');
  readonly cargandoSeguimiento = signal(false);
  readonly errorSeguimiento = signal('');
  private consultaSeguimiento = 0;
  readonly rutasVisibles = computed(() => {
    const texto = this.buscandoRuta().trim().toLocaleLowerCase('es');
    return this.plan().filter((p) =>
      `${p.rutaNombre} ${p.zona ?? ''} ${p.acopiadorNombre ?? ''}`
        .toLocaleLowerCase('es')
        .includes(texto),
    );
  });
  readonly asignacionesHoy = signal<AsignacionRuta[]>([]);
  readonly ultimasUbicaciones = signal<UbicacionGps[]>([]);
  readonly eventosRecorrido = signal<UbicacionGps[]>([]);
  readonly zonasRecorridasSignal = signal<string[]>([]);
  readonly estadoSocket = signal<EstadoSocket>('desconectado');
  /** Reloj que hace re-evaluar la vigencia de los puntos GPS aunque no llegue ningún mensaje. */
  private readonly ahora = signal(Date.now());
  readonly trazadoSeguimiento = signal<RutaPunto[]>([]);
  readonly zonaPoligonos = signal<ZonaPoligono[]>([]);
  readonly proveedoresSeguimiento = signal<ProveedorEnMapa[]>([]);
  readonly cargandoProveedoresSeguimiento = signal(false);
  private readonly destruir$ = new Subject<void>();

  readonly hoy = fechaLocal();
  fecha = fechaLocal();

  readonly rutaSeleccionadaId = signal<number | undefined>(undefined);
  readonly verTodos = signal(true);

  observacionEdicion = '';

  // ---- Geometría de la ruta y proveedores en orden (2.1.3-2.1.6) ----
  readonly rutaPuntosId = signal<number | undefined>(undefined);
  readonly puntosRuta = signal<RutaPunto[]>([]);
  readonly proveedoresRuta = signal<ProveedorDeRuta[]>([]);
  readonly guardandoPuntos = signal(false);
  readonly mensajePuntos = signal('');
  readonly cargandoPuntos = signal(false);
  readonly puntosCargados = signal(false);
  readonly pintandoRuta = signal(false);

  // ---- Punto de acopio dinámico + reordenamiento por cercanía (vecino más cercano) ----
  nuevoPuntoLat: number | null = null;
  nuevoPuntoLng: number | null = null;
  readonly agregandoPuntoAcopio = signal(false);
  readonly reordenando = signal(false);

  puedeEditar() {
    return this.auth.puede(PERMISO.rutasWrite);
  }

  /** Mover proveedores es escribir sobre el padrón, no sobre las rutas: tiene su propio permiso. */
  puedeMoverProveedores() {
    return this.auth.puede(PERMISO.proveedoresWrite);
  }

  // ---- Polígono de zona: dibuja el territorio en el mapa de Rutas y movilidad ----
  readonly zonaPoligonoSeleccionada = signal<string | undefined>(undefined);
  readonly poligonoPuntos = signal<ZonaPoligonoPunto[]>([]);
  readonly pintandoPoligono = signal(false);
  readonly guardandoPoligono = signal(false);
  readonly mensajePoligono = signal('');
  poligonoColor = '#437862';
  nuevaZonaPoligono = '';

  // ---- Flujo territorial: zona -> acopiador -> trazado de su ruta ----
  readonly pasoTerritorio = signal<1 | 2 | 3>(1);
  readonly mensajeZona = signal('');
  readonly errorZona = signal('');
  readonly renombrandoZona = signal(false);
  readonly editandoRutaZonaId = signal<number | null>(null);
  readonly formRutaAbierto = signal(false);
  /** Traer a la zona un acopiador que ya existe. Las altas se hacen solo en Usuarios. */
  readonly formAcopiadorAbierto = signal(false);
  readonly guardandoZona = signal(false);
  nombreZonaEditado = '';
  formRutaZona: { nombre: string; descripcion: string } = { nombre: '', descripcion: '' };
  formRutaEdicion: { nombre: string; descripcion: string } = { nombre: '', descripcion: '' };
  rutaAcopiadorZonaId?: number;
  acopiadorExistenteId?: number;
  readonly formProveedorAbierto = signal(false);
  proveedorExistenteId?: number;
  rutaProveedorZonaId?: number;
  ordenProveedorZona?: number;

  /**
   * Una zona es el texto completo de Ruta.zona, igual que en el backend (zonasAsignadas y el
   * renombrado buscan por el valor entero). Partirlo por "/" convertía "COLLANA I/YASIN/HUANINA"
   * en tres zonas que después ninguna ruta reconocía como propias.
   */
  readonly zonasDisponibles = computed(() => {
    const zonas = new Set<string>();
    this.rutas().forEach((r) => {
      const z = (r.zona ?? '').trim();
      if (z) zonas.add(z);
    });
    this.zonaPoligonos().forEach((z) => zonas.add(z.zona));
    return [...zonas].sort((a, b) => a.localeCompare(b, 'es'));
  });

  readonly poligonoExistente = computed(() => {
    const zona = this.zonaPoligonoSeleccionada();
    return zona ? this.zonaPoligonos().find((z) => z.zona === zona) : undefined;
  });

  // ---- Flujo territorial: qué hay hoy en cada zona ----

  /** Una fila por zona con lo que la compone: su territorio, sus rutas y quién acopia en ellas. */
  readonly zonasResumen = computed<ResumenZona[]>(() => {
    const zonas = this.zonasDisponibles().map((zona) =>
      this.componerZona(zona, this.rutasDeLaZona(zona)),
    );
    // Ninguna ruta debería quedarse sin zona (aquí solo se crean dentro de una), pero si llega una
    // por API o por dato heredado tiene que verse: una ruta sin zona no admite acopiador y no
    // aparecería en ningún otro sitio desde que "Rutas" dejó de ser una pestaña aparte.
    const sinZona = this.rutas().filter((r) => !(r.zona ?? '').trim());
    return sinZona.length ? [...zonas, this.componerZona(SIN_ZONA, sinZona)] : zonas;
  });

  /** Cabecera de la zona abierta: sus cifras sin volver a calcularlas en la plantilla. */
  readonly resumenZonaSeleccionada = computed(() =>
    this.zonasResumen().find((z) => z.zona === this.zonaPoligonoSeleccionada()),
  );

  /** Indicadores de la jornada en Seguimiento, sobre el mismo plan que lista las rutas. */
  readonly resumenSeguimiento = computed(() => {
    const plan = this.plan();
    return {
      rutas: plan.length,
      conResponsable: plan.filter((p) => p.acopiadorId != null).length,
      recepciones: plan.reduce((total, p) => total + (p.recepciones ?? 0), 0),
      litros: plan.reduce((total, p) => total + (p.litros ?? 0), 0),
    };
  });

  private componerZona(zona: string, rutas: Ruta[]): ResumenZona {
    const idsRuta = new Set(rutas.map((r) => r.id));
    return {
      zona,
      poligono: this.zonaPoligonos().find((z) => z.zona === zona),
      rutas,
      acopiadores: this.acopiadores().filter((a) => a.rutaId != null && idsRuta.has(a.rutaId)),
      proveedores: this.proveedores().filter((p) => p.rutaId != null && idsRuta.has(p.rutaId)),
    };
  }

  /**
   * Los proveedores de la zona, igual que los acopiadores: pertenecen a ella a través de su ruta.
   * Ordenados como los visita el acopiador (ruta y orden), y los sin orden al final.
   */
  readonly proveedoresDeZonaSeleccionada = computed<Productor[]>(() => {
    const rutas = this.rutasDeZonaSeleccionada();
    const nombreRuta = new Map(rutas.map((r) => [r.id, r.nombre]));
    return this.proveedores()
      .filter((p) => p.rutaId != null && nombreRuta.has(p.rutaId))
      .sort(
        (a, b) =>
          (nombreRuta.get(a.rutaId!) ?? '').localeCompare(nombreRuta.get(b.rutaId!) ?? '', 'es') ||
          (a.ordenEnRuta ?? 999) - (b.ordenEnRuta ?? 999) ||
          `${a.nombres} ${a.apellidos}`.localeCompare(`${b.nombres} ${b.apellidos}`, 'es'),
      );
  });

  /** Proveedores de otras zonas (o sin ruta): los candidatos a traer a esta. */
  readonly proveedoresFueraDeZona = computed<Productor[]>(() => {
    const ids = new Set(this.rutasDeZonaSeleccionada().map((r) => r.id));
    return this.proveedores().filter((p) => p.rutaId == null || !ids.has(p.rutaId));
  });

  readonly rutasDeZonaSeleccionada = computed<Ruta[]>(() => {
    const zona = this.zonaPoligonoSeleccionada();
    return zona ? this.rutasDeLaZona(zona) : [];
  });

  readonly acopiadoresDeZonaSeleccionada = computed<Acopiador[]>(() => {
    const ids = new Set(this.rutasDeZonaSeleccionada().map((r) => r.id));
    return this.acopiadores().filter((a) => a.rutaId != null && ids.has(a.rutaId));
  });

  /** Acopiadores que todavía no trabajan en esta zona: los candidatos a asignarle. */
  readonly acopiadoresFueraDeZona = computed<Acopiador[]>(() => {
    const ids = new Set(this.rutasDeZonaSeleccionada().map((r) => r.id));
    return this.acopiadores().filter((a) => a.rutaId == null || !ids.has(a.rutaId));
  });

  private rutasDeLaZona(zona: string): Ruta[] {
    if (zona === SIN_ZONA) return this.rutas().filter((r) => !(r.zona ?? '').trim());
    const nombre = zona.trim().toLocaleLowerCase('es');
    return this.rutas().filter((r) => (r.zona ?? '').trim().toLocaleLowerCase('es') === nombre);
  }

  /** El grupo de rutas huérfanas no es una zona de verdad: no se renombra ni se le dibuja territorio. */
  esSinZona(): boolean {
    return this.zonaPoligonoSeleccionada() === SIN_ZONA;
  }

  // ---- Recorridos que pintaron los acopiadores en la app (V27) ----
  readonly trazadosPintados = signal<TrazadoAcopiador[]>([]);
  readonly trazadosPredeterminados = signal<TrazadoAcopiador[]>([]);
  readonly cargandoTrazados = signal(false);
  readonly mensajeTrazado = signal('');
  readonly errorTrazado = signal('');
  readonly guardandoTrazado = signal(false);
  /** Cuál de los recorridos pintados se está superponiendo en el mapa. */
  readonly trazadoVistoId = signal<number | null>(null);
  /** Fecha de los recorridos pintados que se listan; vacío = todas las fechas. */
  fechaTrazados = fechaLocal();

  /**
   * Los recorridos pintados de la zona seleccionada. Se filtra por las rutas de la zona y no por el
   * nombre de la zona del trazado: la zona es texto libre en `Ruta.zona` y el trazado la copia, así
   * que cruzar por ruta es lo que no se desincroniza si alguien renombra la zona.
   */
  readonly trazadosDeZona = computed<TrazadoAcopiador[]>(() => {
    const ids = new Set(this.rutasDeZonaSeleccionada().map((r) => r.id));
    return this.trazadosPintados().filter((t) => ids.has(t.rutaId));
  });

  readonly trazadoVisto = computed<TrazadoAcopiador | undefined>(() => {
    const id = this.trazadoVistoId();
    return id == null ? undefined : this.trazadosPintados().find((t) => t.id === id);
  });

  /** La línea punteada que se superpone al trazado de la ruta para poder compararlos. */
  readonly trazadoSecundarioMapa = computed<PuntoMapa[]>(() =>
    (this.trazadoVisto()?.puntos ?? []).map((punto) => ({
      id: `pintado-${punto.secuencia}`,
      lat: punto.latitud,
      lng: punto.longitud,
      etiqueta: `Pintado · punto ${punto.secuencia + 1}`,
    })),
  );

  /**
   * Qué se está mirando en el mapa del paso 3. Incluye el recorrido pintado que se superpone, y no
   * solo la ruta, porque pulsar "Ver en el mapa" **es** pasar a mirar otra cosa: sin esto el mapa
   * conservaba el encuadre y el trazado pintado podía quedar fuera de la vista, dando la impresión de
   * que el botón no hacía nada. Añadir o mover un punto sigue sin reencuadrar (ver `encuadrarEn`).
   */
  readonly claveEncuadreTrazado = computed(
    () => `ruta:${this.rutaPuntosId() ?? ''}|pintado:${this.trazadoVistoId() ?? ''}`,
  );

  /** El recorrido predeterminado de un acopiador, si administración ya le aprobó uno. */
  predeterminadoDe(acopiadorId: number): TrazadoAcopiador | undefined {
    return this.trazadosPredeterminados().find((t) => t.acopiadorId === acopiadorId);
  }

  esElTrazadoPredeterminado(trazado: TrazadoAcopiador): boolean {
    return this.predeterminadoDe(trazado.acopiadorId)?.origenTrazadoId === trazado.id;
  }

  // ---- Ubicar un proveedor de la ruta en el mapa (RF-59 desde la web) ----
  readonly proveedorAUbicar = signal<ProveedorDeRuta | null>(null);
  readonly guardandoUbicacion = signal(false);
  tipoUbicacion: TipoUbicacion = 'CASA';

  readonly modoMapa = signal<ModoMapa>('ruta');
  readonly seccionPaso3 = signal<SeccionPaso3>('vivienda');

  /** Proveedores de la ruta que ya tienen ubicación: se dibujan como referencia del recorrido. */
  readonly proveedoresEnMapa = computed<PuntoMapa[]>(() =>
    this.proveedoresRuta()
      .filter((p) => p.latitud != null && p.longitud != null)
      .map((p) => ({
        id: `prov-${p.id}`,
        lat: p.latitud as number,
        lng: p.longitud as number,
        etiqueta: `${p.codigo} · ${p.nombres} ${p.apellidos}`,
      })),
  );

  readonly puntosMapaRuta = computed<PuntoMapa[]>(() =>
    this.puntosRuta().map((p, i) => ({
      id: p.id ?? i,
      lat: p.latitud,
      lng: p.longitud,
      etiqueta: `Punto ${p.secuencia}`,
    })),
  );
  protected readonly modosRecorrido = MODOS_RECORRIDO;

  /** Marcadores combinados para el mapa único (proveedores + puntos establecidos + draft). */
  readonly marcadoresUnificadosMapa = computed<PuntoMapa[]>(() => {
    const lista: PuntoMapa[] = [];
    lista.push(...this.proveedoresEnMapa());
    const rutaId = this.rutaPuntosId();
    const pe = this.puntosEstablecidos().filter(
      (p) => p.activo && (!rutaId || p.rutaId === rutaId),
    );
    for (const p of pe) {
      lista.push({
        id: `pe-${p.id}`,
        lat: p.latitud,
        lng: p.longitud,
        etiqueta: `📍 ${p.nombre}`,
        detalle: [p.rutaNombre, p.acopiadorNombre, p.proveedorCodigo].filter(Boolean).join(' · '),
      });
    }
    lista.push(...this.puntoDraftMapa());
    return lista;
  });

  readonly mapaEsEditable = computed<boolean>(() => {
    if (!this.puedeEditar()) return false;
    if (
      this.cargandoPuntos() ||
      this.guardandoPuntos() ||
      this.guardandoUbicacion() ||
      this.guardandoPuntoEstablecido()
    ) {
      return false;
    }
    const modo = this.modoMapa();
    return (
      modo !== 'navegar' &&
      (modo === 'vivienda' ||
        modo === 'ruta' ||
        modo === 'acopio' ||
        modo === 'zona' ||
        this.pintandoRuta() ||
        !!this.proveedorAUbicar() ||
        this.agregandoModoPE())
    );
  });

  setModoMapa(modo: ModoMapa): void {
    this.modoMapa.set(modo);
    if (modo === 'vivienda') {
      this.pintandoRuta.set(false);
      this.seccionPaso3.set('vivienda');
      this.tipoUbicacion = 'CASA';
      if (!this.agregandoModoPE() && !this.proveedorAUbicar()) {
        this.activarModoAgregarPuntoEstablecido();
      }
    } else if (modo === 'ruta') {
      this.pintandoRuta.set(true);
      this.seccionPaso3.set('trazado');
      this.proveedorAUbicar.set(null);
    } else if (modo === 'acopio') {
      this.pintandoRuta.set(false);
      this.seccionPaso3.set('acopio');
      this.proveedorAUbicar.set(null);
    } else if (modo === 'zona') {
      this.pintandoPoligono.set(true);
      this.pintandoRuta.set(false);
      this.proveedorAUbicar.set(null);
    } else if (modo === 'navegar') {
      this.pintandoRuta.set(false);
      this.proveedorAUbicar.set(null);
      this.agregandoModoPE.set(false);
    }
  }

  cambiarSeccionPaso3(sec: SeccionPaso3): void {
    this.seccionPaso3.set(sec);
    if (sec === 'vivienda') {
      this.setModoMapa('vivienda');
    } else if (sec === 'trazado') {
      this.setModoMapa('ruta');
    } else if (sec === 'acopio') {
      this.setModoMapa('acopio');
    } else if (sec === 'recorridos') {
      this.setModoMapa('navegar');
    }
  }

  alCambiarTipoUbicacion(nuevoTipo: TipoUbicacion): void {
    this.tipoUbicacion = nuevoTipo;
    if (nuevoTipo === 'CASA') {
      this.modoMapa.set('vivienda');
    }
  }

  // ---- Puntos establecidos: referencia fija que administración crea a mano en el mapa ----
  readonly puntosEstablecidos = signal<PuntoEstablecido[]>([]);
  readonly cargandoPuntosEstablecidos = signal(false);
  readonly rutaPuntosEstablecidosId = signal<number | undefined>(undefined);
  readonly proveedoresDeRutaPE = signal<ProveedorDeRuta[]>([]);
  readonly agregandoModoPE = signal(false);
  readonly editandoPuntoEstablecidoId = signal<number | null>(null);
  readonly guardandoPuntoEstablecido = signal(false);
  readonly mensajePuntoEstablecido = signal('');
  readonly errorPuntoEstablecido = signal('');
  /** Punto de referencia o ubicación de un proveedor que ya existe (las altas son de Usuarios). */
  readonly modoAltaPunto = signal<'referencia' | 'existente'>('referencia');
  readonly revisionPuntoDraft = signal(0);
  readonly puntoSeleccionado = signal<number | null>(null);
  formPuntoEstablecido: {
    nombre: string;
    latitud: number | null;
    longitud: number | null;
    rutaId?: number;
    acopiadorId?: number;
    proveedorId?: number;
    observacion?: string;
  } = { nombre: '', latitud: null, longitud: null };

  readonly opcRutasTodas = computed<OpcionBuscador[]>(() =>
    this.rutas().map((r) => ({ valor: r.id, etiqueta: r.nombre, detalle: r.zona || undefined })),
  );

  readonly opcRutasTodasConTodas = computed<OpcionBuscador[]>(() => [
    { valor: undefined, etiqueta: 'Todas las rutas' },
    ...this.opcRutasTodas(),
  ]);

  readonly opcAcopiadoresConNinguno = computed<OpcionBuscador[]>(() => [
    { valor: undefined, etiqueta: 'Sin acopiador asignado' },
    ...this.opcAcopiadores(),
  ]);

  readonly opcProveedoresDeRutaPEConNinguno = computed<OpcionBuscador[]>(() => [
    { valor: undefined, etiqueta: 'Sin proveedor asociado' },
    ...this.proveedoresDeRutaPE().map((p) => ({
      valor: p.id,
      etiqueta: `${p.codigo} · ${p.nombres} ${p.apellidos}`,
    })),
  ]);

  /** Lo que se guarda al hacer clic en el mapa mientras se está agregando/editando un punto. */
  readonly puntoDraftMapa = computed<PuntoMapa[]>(() => {
    this.revisionPuntoDraft();
    return this.agregandoModoPE() &&
      this.formPuntoEstablecido.latitud != null &&
      this.formPuntoEstablecido.longitud != null &&
      Number.isFinite(this.formPuntoEstablecido.latitud) &&
      Number.isFinite(this.formPuntoEstablecido.longitud) &&
      Math.abs(this.formPuntoEstablecido.latitud) <= 90 &&
      Math.abs(this.formPuntoEstablecido.longitud) <= 180
      ? [
          {
            id: 'draft',
            lat: this.formPuntoEstablecido.latitud,
            lng: this.formPuntoEstablecido.longitud,
            etiqueta: this.formPuntoEstablecido.nombre || 'Nuevo punto (sin guardar)',
          },
        ]
      : [];
  });

  actualizarDraftMapa(): void {
    this.revisionPuntoDraft.update((v) => v + 1);
  }

  readonly nombreRutaPunto = computed(
    () => this.rutas().find((r) => r.id === this.rutaPuntosId())?.nombre ?? 'Selecciona una ruta',
  );

  seleccionarProveedorDelPunto(id: number | undefined): void {
    this.formPuntoEstablecido.proveedorId = id;
    const proveedor = this.proveedoresDeRutaPE().find((p) => p.id === id);
    if (proveedor) this.formPuntoEstablecido.nombre = `${proveedor.nombres} ${proveedor.apellidos}`;
    this.actualizarDraftMapa();
  }

  // ---- Recorrido de una ruta: se abre desde su fila en "Rutas y personas" ----

  /** La ruta cuyo recorrido está abierto para ver o editar (null = ninguno). */
  readonly recorridoAbiertoId = signal<number | null>(null);

  /**
   * El límite de la zona se abre en "Rutas y personas", igual que el recorrido de una ruta: desde
   * el icono de la cabecera de la zona o desde su menú ⋯. Solo uno de los dos paneles a la vez.
   */
  readonly limiteAbierto = signal(false);

  abrirLimite(): void {
    if (this.limiteAbierto() && this.seccion() === 'personas') return;
    this.siSePuedeDejarLaEdicion(() => {
      this.tab.set('territorio');
      this.pasoTerritorio.set(2);
      this.recorridoAbiertoId.set(null);
      this.limiteAbierto.set(true);
      this.cargarHistorialLimite();
    });
  }

  cerrarLimite(): void {
    this.siSePuedeDejarLaEdicion(() => {
      this.pintandoPoligono.set(false);
      this.limiteAbierto.set(false);
    });
  }

  abrirRecorrido(ruta: Ruta): void {
    if (this.recorridoAbiertoId() === ruta.id) return;
    this.siSePuedeDejarLaEdicion(() => {
      this.limiteAbierto.set(false);
      this.recorridoAbiertoId.set(ruta.id);
      if (this.rutaPuntosId() !== ruta.id) this.seleccionarRutaPuntos(ruta.id);
    });
  }

  cerrarRecorrido(): void {
    this.siSePuedeDejarLaEdicion(() => {
      this.pintandoRuta.set(false);
      this.recorridoAbiertoId.set(null);
    });
  }

  alternarDibujoRuta(): void {
    if (this.guardandoPuntoEstablecido()) return;
    this.cancelarFormPuntoEstablecido();
    this.proveedorAUbicar.set(null);
    this.pintandoRuta.set(!this.pintandoRuta());
  }

  readonly puntosMapaGestion = computed<PuntoMapa[]>(() => {
    // Mirando el límite basta el contorno; los vértices como marcadores solo al editarlo. En "Rutas
    // y personas" el mapa muestra el contorno y, si está abierto, el recorrido de la ruta.
    if (this.pasoTerritorio() === 1)
      return this.pintandoPoligono() ? this.puntosMapaPoligono() : [];
    if (this.pasoTerritorio() === 2)
      return this.limiteAbierto() && this.pintandoPoligono() ? this.puntosMapaPoligono() : [];
    const activos = this.puntosEstablecidos().filter((p) => p.activo);
    const enlazados = new Set(activos.map((p) => p.proveedorId));
    return [
      ...activos
        .filter((p) => p.id !== this.editandoPuntoEstablecidoId())
        .map((p) => ({
          id: `pe-${p.id}`,
          lat: p.latitud,
          lng: p.longitud,
          etiqueta: p.nombre,
          detalle: `${p.proveedorNombre || 'Referencia'} · ${p.rutaNombre}`,
        })),
      ...this.proveedoresRuta()
        .filter((p) => !enlazados.has(p.id) && p.latitud != null && p.longitud != null)
        .map((p) => ({
          id: `prov-${p.id}`,
          lat: p.latitud!,
          lng: p.longitud!,
          etiqueta: `${p.nombres} ${p.apellidos}`,
        })),
      ...this.puntoDraftMapa(),
    ];
  });

  readonly idsPuntosArrastrables = computed(() =>
    this.pasoTerritorio() === 3 &&
    this.puedeEditar() &&
    !this.guardandoPuntoEstablecido() &&
    !this.pintandoRuta()
      ? this.puntosMapaGestion()
          .filter(
            (p) =>
              this.puedeMoverProveedores() ||
              (!String(p.id).startsWith('prov-') &&
                !this.puntosEstablecidos().find((pe) => `pe-${pe.id}` === p.id)?.proveedorId),
          )
          .map((p) => p.id)
      : [],
  );

  async clicMapaGestion(coordenadas: { latitud: number; longitud: number }): Promise<void> {
    if (this.pasoTerritorio() === 1 || (this.limiteAbierto() && this.pintandoPoligono())) {
      this.agregarPuntoPoligono(coordenadas);
      return;
    }
    if (this.agregandoModoPE()) {
      this.clicMapaPuntoEstablecido(coordenadas);
      return;
    }
    if (this.proveedorAUbicar()) {
      await this.moverProveedorEnMapa(this.proveedorAUbicar()!, coordenadas);
      return;
    }
    if (this.pintandoRuta()) this.agregarPunto(coordenadas);
  }

  seleccionarMarcadorGestion(id: string | number): void {
    if (
      this.pasoTerritorio() !== 3 ||
      !this.puedeEditar() ||
      this.guardandoPuntoEstablecido() ||
      this.pintandoRuta()
    )
      return;
    const punto = this.puntosEstablecidos().find((p) => `pe-${p.id}` === id);
    if (punto) this.editarPuntoEstablecido(punto);
  }

  async moverMarcadorGestion(evento: {
    id: string | number;
    latitud: number;
    longitud: number;
  }): Promise<void> {
    if (!this.idsPuntosArrastrables().includes(evento.id)) return;
    if (evento.id === 'draft') {
      this.clicMapaPuntoEstablecido(evento);
      return;
    }
    const punto = this.puntosEstablecidos().find((p) => `pe-${p.id}` === evento.id);
    if (punto) {
      this.editarPuntoEstablecido(punto);
      this.clicMapaPuntoEstablecido(evento);
      await this.guardarPuntoEstablecido(() => this.deshacerMovimientoEnMapa());
      return;
    }
    const proveedor = this.proveedoresRuta().find((p) => `prov-${p.id}` === evento.id);
    if (proveedor) await this.moverProveedorEnMapa(proveedor, evento);
  }

  /** Un arrastre no aceptado: el marcador vuelve a donde estaba guardado. */
  private deshacerMovimientoEnMapa(): void {
    this.cancelarFormPuntoEstablecido();
    this.puntosEstablecidos.update((p) => [...p]);
    this.proveedoresRuta.update((p) => [...p]);
    this.mensajePuntoEstablecido.set('Cambio cancelado: el punto sigue donde estaba.');
  }

  private async moverProveedorEnMapa(
    proveedor: ProveedorDeRuta,
    coordenadas: { latitud: number; longitud: number },
  ): Promise<void> {
    if (!this.puedeEditar() || !this.puedeMoverProveedores() || this.guardandoPuntoEstablecido())
      return;
    const punto = this.puntosEstablecidos().find((p) => p.proveedorId === proveedor.id && p.activo);
    if (punto) this.editarPuntoEstablecido(punto);
    else {
      this.activarModoAgregarPuntoEstablecido();
      this.modoAltaPunto.set('existente');
      this.formPuntoEstablecido.nombre = `${proveedor.nombres} ${proveedor.apellidos}`;
      this.formPuntoEstablecido.proveedorId = proveedor.id;
    }
    this.clicMapaPuntoEstablecido(coordenadas);
    await this.guardarPuntoEstablecido(() => this.deshacerMovimientoEnMapa());
  }

  agregarProveedorConPunto(): void {
    if (!this.puedeMoverProveedores()) return;
    this.activarModoAgregarPuntoEstablecido();
    this.modoAltaPunto.set('existente');
  }

  readonly marcadoresPuntosEstablecidos = computed<PuntoMapa[]>(() =>
    this.puntosEstablecidos()
      .filter((p) => p.activo)
      .map((p) => ({
        id: `pe-${p.id}`,
        lat: p.latitud,
        lng: p.longitud,
        etiqueta: p.nombre,
        detalle: [p.rutaNombre, p.acopiadorNombre, p.proveedorCodigo].filter(Boolean).join(' · '),
      })),
  );

  /**
   * Recorrido GPS real del acopiador de la ruta elegida, en orden de captura. Antes esta pantalla
   * solo listaba los eventos; ahora también se dibujan, como línea o como puntos según prefiera
   * quien mira (la hora de cada punto aparece al pasar el mouse).
   */
  readonly recorridoMapa = computed<PuntoMapa[]>(() =>
    [...this.eventosRecorrido()]
      .sort((a, b) => (a.fecha < b.fecha ? -1 : a.fecha > b.fecha ? 1 : 0))
      .map((e, i) => ({
        id: `gps-${i}`,
        lat: e.latitud,
        lng: e.longitud,
        etiqueta: new Date(e.fecha).toLocaleTimeString('es-PE', {
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
        }),
      })),
  );

  readonly trazadoMapaSeguimiento = computed<PuntoMapa[]>(() =>
    this.trazadoSeguimiento().map((p) => ({
      id: `ruta-${p.id ?? p.secuencia}`,
      lat: p.latitud,
      lng: p.longitud,
      etiqueta: `Ruta · punto ${p.secuencia}`,
    })),
  );
  readonly poligonosMapa = computed<PoligonoMapa[]>(() =>
    this.zonaPoligonos().map((z) => ({
      id: z.id,
      etiqueta: z.zona,
      color: z.color,
      puntos: z.puntos.map((p) => ({ lat: p.latitud, lng: p.longitud })),
    })),
  );
  readonly puntosMapaPoligono = computed<PuntoMapa[]>(() =>
    this.poligonoPuntos().map((p, i) => ({
      id: i,
      lat: p.latitud,
      lng: p.longitud,
      etiqueta: `Vértice ${p.secuencia}`,
    })),
  );
  readonly poligonoDraftMapa = computed<PoligonoMapa[]>(() => {
    const puntos = this.poligonoPuntos();
    if (puntos.length < 3) return [];
    return [
      {
        id: 'draft',
        etiqueta: this.zonaPoligonoSeleccionada() ?? '',
        color: this.poligonoColor,
        puntos: puntos.map((p) => ({ lat: p.latitud, lng: p.longitud })),
      },
    ];
  });

  // ---- Opciones de los buscadores escribibles ----
  // Se arman aquí y no en la plantilla para que cada buscador reciba una lista ya lista y el
  // filtrado por texto trabaje sobre la etiqueta y el detalle que se ven en pantalla.

  readonly opcRutasDeZona = computed<OpcionBuscador[]>(() =>
    this.rutasDeZonaSeleccionada().map((r) => ({
      valor: r.id,
      etiqueta: r.nombre,
      detalle: r.descripcion || undefined,
    })),
  );

  readonly opcRutasDeZonaConTodas = computed<OpcionBuscador[]>(() => [
    { valor: undefined, etiqueta: 'Selecciona una ruta de esta zona…' },
    ...this.opcRutasDeZona(),
  ]);

  readonly opcAcopiadoresFuera = computed<OpcionBuscador[]>(() =>
    this.acopiadoresFueraDeZona().map((a) => ({
      valor: a.id,
      etiqueta: `${a.nombres} ${a.apellidos}`,
      detalle: a.zona || 'sin zona',
    })),
  );

  readonly opcProveedoresFuera = computed<OpcionBuscador[]>(() =>
    this.proveedoresFueraDeZona().map((p) => ({
      valor: p.id,
      etiqueta: `${p.nombres} ${p.apellidos}`,
      detalle: `${p.codigo} · ${p.zonaPrincipal || 'sin zona'}`,
    })),
  );

  readonly opcAcopiadores = computed<OpcionBuscador[]>(() =>
    this.acopiadores().map((a) => ({
      valor: a.id,
      etiqueta: `${a.nombres} ${a.apellidos}`,
      detalle: a.zona || 'sin zona',
    })),
  );

  readonly opcRutasDelPlan = computed<OpcionBuscador[]>(() => [
    { valor: undefined, etiqueta: 'Todas las rutas' },
    ...this.plan().map((p) => ({
      valor: p.rutaId,
      etiqueta: p.rutaNombre,
      detalle: p.zona || undefined,
    })),
  ]);

  readonly relativo = relativo;

  readonly completadas = computed(
    () => this.plan().filter((p) => p.estado === 'CON_REGISTROS').length,
  );
  readonly enRecorrido = computed(() => this.plan().filter((p) => p.estado === 'ASIGNADA').length);
  readonly litrosTotales = computed(() => this.plan().reduce((s, p) => s + p.litros, 0));

  /** Solo acopiadores que enviaron posición hace poco: los desconectados no se pintan. */
  readonly ubicacionesVivas = computed(() => {
    const ahora = this.ahora();
    return this.ultimasUbicaciones().filter(
      (u) => ahora - new Date(u.fecha).getTime() < VENTANA_GPS_VIVO_MS,
    );
  });

  readonly gpsActivos = computed(() => this.ubicacionesVivas().length);
  readonly ultimaActualizacionGps = computed(() => {
    const lista = [...this.ultimasUbicaciones()].sort((a, b) => (a.fecha < b.fecha ? 1 : -1));
    return lista[0]?.fecha;
  });

  readonly rutaSeleccionada = computed(
    () => this.plan().find((p) => p.rutaId === this.rutaSeleccionadaId()) ?? null,
  );
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
    return !!u && this.ahora() - new Date(u.fecha).getTime() < VENTANA_GPS_VIVO_MS;
  });

  readonly zonasRecorridas = computed(() => this.zonasRecorridasSignal());

  readonly puntosMapa = computed<PuntoMapa[]>(() => {
    const base = this.verTodos()
      ? this.ubicacionesVivas()
      : this.ubicacionesVivas().filter((u) => u.acopiadorId === this.acopiadorAsignado());
    return base.map((u) => ({
      id: u.acopiadorId,
      lat: u.latitud,
      lng: u.longitud,
      etiqueta: u.acopiadorNombre,
      detalle: 'Actualizado: ' + new Date(u.fecha).toLocaleString(),
    }));
  });

  /** Confirmación antes de eliminar o desactivar: nada se borra sin que la persona lo acepte. */
  private readonly dialogo = inject(DialogoService);
  private readonly host = inject(ElementRef<HTMLElement>);

  // ---- Menús desplegables (⋯ y opciones de mapa de cada ruta) ----

  /** Al elegir una opción, el menú se cierra. */
  cerrarMenu(evento: Event): void {
    (evento.target as HTMLElement | null)?.closest('details')?.removeAttribute('open');
  }

  /** Un clic fuera cierra los menús abiertos, y abrir uno cierra los demás. */
  @HostListener('document:click', ['$event'])
  cerrarMenusAlHacerClicFuera(evento: Event): void {
    const destino = evento.target as Node | null;
    this.host.nativeElement.querySelectorAll('details.rt-menu[open]').forEach((menu: Element) => {
      if (!destino || !menu.contains(destino)) menu.removeAttribute('open');
    });
  }

  // ---- Protección contra accidentes (pedido del cliente, 2026-10-25: borró un límite sin querer) ----

  /** Todo cambio en el mapa o en la organización de las rutas pide aceptar antes de guardarse. */
  private confirmarCambio(
    titulo: string,
    mensaje?: string,
    confirmar = 'Sí, guardar',
  ): Promise<boolean> {
    return this.dialogo.confirmar({
      titulo,
      mensaje,
      confirmar,
      cancelar: 'Cancelar',
      tipo: 'confirmacion',
    });
  }

  /**
   * Borrar o dar de baja exige escribir el nombre de lo que se borra: un clic de más ya no basta.
   * Si lo escrito no coincide no se hace nada y se avisa.
   */
  private async confirmarEliminacion(
    nombre: string,
    titulo: string,
    mensaje: string,
    confirmar: string,
  ): Promise<boolean> {
    const escrito = await this.dialogo.pedirTexto({
      titulo,
      mensaje: `${mensaje} Para confirmar escribe el nombre exacto: ${nombre}`,
      etiqueta: 'Nombre',
      placeholder: nombre,
      obligatorio: true,
      confirmar,
      cancelar: 'Cancelar',
      tipo: 'peligro',
    });
    if (escrito == null) return false;
    const coincide =
      escrito.trim().toLocaleLowerCase('es') === nombre.trim().toLocaleLowerCase('es');
    if (!coincide) {
      await this.dialogo.confirmar({
        titulo: 'No se eliminó nada',
        mensaje: `El nombre escrito no coincide con «${nombre}».`,
        confirmar: 'Entendido',
        tipo: 'aviso',
      });
    }
    return coincide;
  }

  /** Hay un dibujo o una ubicación a medio hacer en el mapa. */
  readonly edicionAbierta = computed(
    () =>
      this.pintandoPoligono() ||
      this.pintandoRuta() ||
      this.agregandoModoPE() ||
      !!this.proveedorAUbicar(),
  );

  /** Antes de cambiar de zona, ruta o sección con una edición abierta, pregunta si se descarta. */
  private async puedeDejarLaEdicion(): Promise<boolean> {
    if (this.guardandoPuntoEstablecido() || this.guardandoPoligono() || this.guardandoPuntos())
      return false;
    if (!this.edicionAbierta()) return true;
    const ok = await this.dialogo.confirmar({
      titulo: '¿Salir sin guardar?',
      mensaje: 'Tienes un cambio a medio hacer en el mapa. Si sales, no se guardará.',
      confirmar: 'Salir sin guardar',
      cancelar: 'Seguir editando',
      tipo: 'peligro',
    });
    if (ok) {
      this.pintandoPoligono.set(false);
      this.pintandoRuta.set(false);
      this.proveedorAUbicar.set(null);
      this.cancelarFormPuntoEstablecido();
      const zona = this.zonaPoligonoSeleccionada();
      if (zona) this.seleccionarPoligono(zona);
    }
    return ok;
  }

  /** La pantalla es una sola: lo que se ve al costado depende de esta sección. */
  readonly seccion = computed<'seguimiento' | 'limite' | 'personas' | 'puntos'>(() =>
    this.tab() === 'gps'
      ? 'seguimiento'
      : this.pasoTerritorio() === 1
        ? 'limite'
        : this.pasoTerritorio() === 2
          ? 'personas'
          : 'puntos',
  );

  /**
   * Cambiar de sección, zona o ruta con un dibujo a medio hacer pregunta antes; sin nada abierto
   * actúa en el acto, sin ventanas de por medio.
   */
  private siSePuedeDejarLaEdicion(accion: () => void): void {
    if (!this.edicionAbierta()) {
      if (!this.guardandoPuntoEstablecido() && !this.guardandoPoligono() && !this.guardandoPuntos())
        accion();
      return;
    }
    void this.puedeDejarLaEdicion().then((ok) => {
      if (ok) accion();
    });
  }

  abrirSeccion(seccion: 'seguimiento' | 'limite' | 'personas' | 'puntos'): void {
    if (seccion === this.seccion()) return;
    this.siSePuedeDejarLaEdicion(() => {
      if (seccion === 'seguimiento') {
        this.tab.set('gps');
        return;
      }
      this.tab.set('territorio');
      this.pasoTerritorio.set(seccion === 'limite' ? 1 : seccion === 'personas' ? 2 : 3);
      if (seccion === 'limite') this.cargarHistorialLimite();
    });
  }

  /** Vista general: todas las rutas, sin zona elegida. */
  elegirTodas(): void {
    this.siSePuedeDejarLaEdicion(() => {
      this.cambiarRutaSeleccionada(undefined);
      this.seleccionarZona(undefined);
      this.tab.set('gps');
    });
  }

  elegirZona(zona: string): void {
    this.siSePuedeDejarLaEdicion(() => {
      this.cambiarRutaSeleccionada(undefined);
      const tienePoligono = this.zonaPoligonos().some((z) => z.zona === zona);
      const paso = this.seccion() === 'puntos' ? 3 : tienePoligono ? 2 : 1;
      this.seleccionarZona(zona, paso);
      this.tab.set('territorio');
      this.cargarHistorialLimite();
    });
  }

  elegirRuta(ruta: Ruta): void {
    this.siSePuedeDejarLaEdicion(() => {
      const zona = (ruta.zona ?? '').trim() || SIN_ZONA;
      if (this.zonaPoligonoSeleccionada() !== zona) {
        // Elegir una ruta no abre el límite por su cuenta: eso se pide desde su menú o la zona.
        this.seleccionarZona(zona, this.pasoTerritorio() === 3 ? 3 : 2);
        this.cargarHistorialLimite();
      }
      this.seleccionarRutaPuntos(ruta.id);
      this.cambiarRutaSeleccionada(ruta.id);
    });
  }

  /** Lista lateral: zonas con sus rutas, filtradas por el buscador (zona, ruta o acopiador). */
  readonly zonasFiltradas = computed(() => {
    const texto = this.buscandoRuta().trim().toLocaleLowerCase('es');
    if (!texto) return this.zonasResumen();
    const incluye = (v?: string | null) => (v ?? '').toLocaleLowerCase('es').includes(texto);
    return this.zonasResumen()
      .map((z) => {
        if (incluye(z.zona)) return z;
        const rutas = z.rutas.filter(
          (r) => incluye(r.nombre) || incluye(this.planPorRuta().get(r.id)?.acopiadorNombre),
        );
        return rutas.length ? { ...z, rutas } : null;
      })
      .filter((z): z is ResumenZona => z !== null);
  });

  /** Lo que el plan del día sabe de cada ruta (acopiador, litros), para la lista lateral. */
  readonly planPorRuta = computed(() => new Map(this.plan().map((p) => [p.rutaId, p])));

  // ---- Historial del límite: restaurar lo que se cambió o borró por error ----

  readonly historialLimite = signal<ZonaPoligonoVersion[]>([]);
  readonly cargandoHistorial = signal(false);

  cargarHistorialLimite(): void {
    const zona = this.zonaPoligonoSeleccionada();
    if (!zona || this.esSinZona()) {
      this.historialLimite.set([]);
      return;
    }
    this.cargandoHistorial.set(true);
    this.rutasService.historialDeLimite(zona).subscribe({
      next: (h) => {
        if (this.zonaPoligonoSeleccionada() === zona) this.historialLimite.set(h);
        this.cargandoHistorial.set(false);
      },
      error: () => this.cargandoHistorial.set(false),
    });
  }

  async restaurarVersionLimite(version: ZonaPoligonoVersion): Promise<void> {
    if (!this.puedeEditar() || this.guardandoPoligono()) return;
    const ok = await this.confirmarCambio(
      `¿Restaurar este límite de «${version.zona}»?`,
      `Vuelve el límite de ${version.puntos.length} vértices guardado el ${new Date(version.fecha).toLocaleString('es-PE')}. ` +
        'El límite actual queda también en el historial.',
      'Sí, restaurar',
    );
    if (!ok) return;
    this.guardandoPoligono.set(true);
    this.rutasService.restaurarLimite(version.id).subscribe({
      next: (restaurado) => {
        this.guardandoPoligono.set(false);
        this.zonaPoligonos.update((zs) => [
          ...zs.filter((z) => z.zona !== restaurado.zona),
          restaurado,
        ]);
        this.seleccionarPoligono(restaurado.zona);
        this.mensajePoligono.set('Límite restaurado.');
        this.cargarHistorialLimite();
      },
      error: (e) => {
        this.guardandoPoligono.set(false);
        this.mensajePoligono.set(e.error?.message ?? 'No se pudo restaurar el límite.');
      },
    });
  }

  readonly motivoVersion: Record<ZonaPoligonoVersion['motivo'], string> = {
    MODIFICADO: 'Antes de un cambio',
    ELIMINADO: 'Antes de borrarlo',
    ZONA_ELIMINADA: 'Al eliminar la zona',
    RESTAURADO: 'Antes de restaurar otro',
  };

  constructor(
    protected readonly auth: AuthService,
    private readonly ruta: ActivatedRoute,
    private readonly rutasService: RutasService,
    private readonly acopiadoresService: AcopiadoresService,
    private readonly productoresService: ProductoresService,
    private readonly ubicacionesService: UbicacionesService,
    private readonly ubicacionesTiempoReal: UbicacionesTiempoRealService,
    private readonly recojoService: RecojoService,
    private readonly exportacionesService: ExportacionesService,
    private readonly trazadosService: TrazadosService,
    private readonly puntosEstablecidosService: PuntosEstablecidosService,
    protected readonly preferenciasMapa: PreferenciasMapaService,
  ) {}

  ngOnInit(): void {
    this.acopiadoresService.listar('', 0, 200).subscribe((pg) => this.acopiadores.set(pg.content));
    this.cargarProveedores().subscribe((p) => this.proveedores.set(p));
    this.cargarRutas();
    this.cargarTodo();
    this.cargarTrazados();
    this.cargarPuntosEstablecidos();
    this.rutasService.poligonosDeZona().subscribe((p) => {
      this.zonaPoligonos.set(p);
      this.abrirDesdeLaUrl();
    });
    interval(30_000)
      .pipe(takeUntil(this.destruir$))
      .subscribe(() => this.ahora.set(Date.now()));
    this.ubicacionesTiempoReal.estado$.subscribe((estado) => this.estadoSocket.set(estado));
    this.ubicacionesTiempoReal.conectar(this.destruir$).subscribe((ubicacion) => {
      this.ahora.set(Date.now());
      this.ultimasUbicaciones.update((actuales) => [
        ubicacion,
        ...actuales.filter((u) => u.acopiadorId !== ubicacion.acopiadorId),
      ]);
      if (ubicacion.acopiadorId === this.acopiadorAsignado()) {
        this.eventosRecorrido.update((eventos) => [...eventos, ubicacion]);
      }
    });
  }

  // ---- Recorridos pintados por los acopiadores (V27) ----

  cargarTrazados(): void {
    // El <input type="date"> se puede dejar en blanco, y el backend exige la fecha para no devolver
    // el historial completo. Se cae a hoy en vez de mandar una peticion que responderia 400.
    if (!this.fechaTrazados) this.fechaTrazados = fechaLocal();
    this.cargandoTrazados.set(true);
    this.errorTrazado.set('');
    this.trazadosService.pintados(this.fechaTrazados).subscribe({
      next: (t) => {
        this.trazadosPintados.set(t);
        this.cargandoTrazados.set(false);
        // Si el que se estaba mirando ya no está en la lista (se cambió de fecha), se deja de
        // superponer en vez de quedar dibujando un recorrido que la tabla ya no muestra.
        if (!t.some((x) => x.id === this.trazadoVistoId())) this.trazadoVistoId.set(null);
      },
      error: (e) => {
        this.errorTrazado.set(e.error?.message ?? 'No se pudieron cargar los recorridos pintados.');
        this.cargandoTrazados.set(false);
      },
    });
    this.trazadosService.predeterminados().subscribe({
      next: (t) => this.trazadosPredeterminados.set(t),
      error: () => {
        // Es un dato complementario (marca qué acopiador ya tiene recorrido propio): si falla, la
        // lista de pintados sigue siendo utilizable.
      },
    });
  }

  /** Superpone (o quita) el recorrido pintado sobre el trazado de la ruta, para compararlos. */
  alternarTrazadoEnMapa(trazado: TrazadoAcopiador): void {
    const yaVisible = this.trazadoVistoId() === trazado.id;
    this.trazadoVistoId.set(yaVisible ? null : trazado.id);
    // Se compara contra el recorrido de esa ruta: queda abierto para verlo en el mapa.
    if (!yaVisible) {
      this.limiteAbierto.set(false);
      this.recorridoAbiertoId.set(trazado.rutaId);
    }
    // Se salta a la ruta del trazado: comparar contra el trazado de OTRA ruta no dice nada.
    if (!yaVisible && this.rutaPuntosId() !== trazado.rutaId) {
      this.seleccionarRutaPuntos(trazado.rutaId);
    }
  }

  async predeterminarTrazado(trazado: TrazadoAcopiador): Promise<void> {
    const ok = await this.confirmarCambio(
      `¿Usar este recorrido como el predeterminado de ${trazado.acopiadorNombre}?`,
      'Reemplaza al recorrido predeterminado que tenga ahora.',
      'Sí, predeterminar',
    );
    if (!ok) return;
    this.guardandoTrazado.set(true);
    this.mensajeTrazado.set('');
    this.errorTrazado.set('');
    this.trazadosService.predeterminar(trazado.id).subscribe({
      next: (predeterminado) => {
        this.guardandoTrazado.set(false);
        this.mensajeTrazado.set(
          `${predeterminado.acopiadorNombre} usará este recorrido como predeterminado (${predeterminado.puntos.length} puntos).`,
        );
        // Se recarga en vez de insertar a mano: el backend reemplaza el predeterminado anterior del
        // acopiador, y esa lista tiene que reflejarlo.
        this.trazadosService
          .predeterminados()
          .subscribe((t) => this.trazadosPredeterminados.set(t));
      },
      error: (e) => {
        this.guardandoTrazado.set(false);
        this.errorTrazado.set(
          e.error?.message ?? 'No se pudo asignar el recorrido como predeterminado.',
        );
      },
    });
  }

  async quitarPredeterminado(trazado: TrazadoAcopiador): Promise<void> {
    const ok = await this.dialogo.confirmar({
      titulo: `¿Quitar el recorrido predeterminado de ${trazado.acopiadorNombre}?`,
      mensaje: 'Su recorrido de referencia vuelve a ser el trazado de la ruta.',
      confirmar: 'Quitar',
      tipo: 'peligro',
    });
    if (!ok) return;
    this.guardandoTrazado.set(true);
    this.mensajeTrazado.set('');
    this.errorTrazado.set('');
    this.trazadosService.quitarPredeterminado(trazado.acopiadorId).subscribe({
      next: () => {
        this.guardandoTrazado.set(false);
        this.mensajeTrazado.set(
          `${trazado.acopiadorNombre} vuelve a usar el trazado de su ruta como referencia.`,
        );
        this.trazadosPredeterminados.update((t) =>
          t.filter((x) => x.acopiadorId !== trazado.acopiadorId),
        );
      },
      error: (e) => {
        this.guardandoTrazado.set(false);
        this.errorTrazado.set(e.error?.message ?? 'No se pudo quitar el recorrido predeterminado.');
      },
    });
  }

  ngOnDestroy(): void {
    this.destruir$.next();
    this.destruir$.complete();
  }

  cargarTodo(): void {
    if (!this.fecha) this.fecha = this.hoy;
    this.rutasService.asignaciones(this.fecha).subscribe((a) => this.asignacionesHoy.set(a));
    this.ubicacionesService.ultimas().subscribe((u) => {
      this.ahora.set(Date.now());
      this.ultimasUbicaciones.set(u);
    });
    const fecha = this.fecha;
    this.cargandoPlan.set(true);
    this.errorPlan.set('');
    this.rutasService.planOperativo(fecha).subscribe({
      next: (p) => {
        if (this.fecha !== fecha) return;
        this.plan.set(p);
        this.cargandoPlan.set(false);
        if (!p.some((r) => r.rutaId === this.rutaSeleccionadaId())) {
          this.rutaSeleccionadaId.set(undefined);
          this.verTodos.set(true);
        }
        this.seleccionarRuta();
      },
      error: () => {
        if (this.fecha !== fecha) return;
        this.cargandoPlan.set(false);
        this.plan.set([]);
        this.cambiarRutaSeleccionada(undefined);
        this.errorPlan.set('No se pudo cargar el plan de rutas. Vuelve a intentarlo.');
      },
    });
  }

  cambiarRutaSeleccionada(id: number | undefined): void {
    this.rutaSeleccionadaId.set(id);
    this.verTodos.set(id == null);
    this.seleccionarRuta();
  }

  seleccionarRuta(): void {
    const consulta = ++this.consultaSeguimiento;
    const fecha = this.fecha;
    this.trazadoSeguimiento.set([]);
    this.eventosRecorrido.set([]);
    this.zonasRecorridasSignal.set([]);
    this.errorSeguimiento.set('');
    this.cargandoSeguimiento.set(false);
    this.observacionEdicion = this.asignacionSeleccionada()?.observacion ?? '';
    const acopiadorId = this.acopiadorAsignado();
    const rutaId = this.rutaSeleccionadaId();
    this.cargarProveedoresSeguimiento(rutaId, consulta);
    if (rutaId) {
      this.rutasService.puntos(rutaId).subscribe({
        next: (puntos) => {
          if (consulta === this.consultaSeguimiento && fecha === this.fecha)
            this.trazadoSeguimiento.set(puntos);
        },
        error: () => {
          if (consulta === this.consultaSeguimiento && fecha === this.fecha)
            this.errorSeguimiento.set(
              'No se pudo cargar el trazado planificado. Selecciona la ruta otra vez para reintentar.',
            );
        },
      });
    }
    if (!acopiadorId) {
      this.eventosRecorrido.set([]);
      this.zonasRecorridasSignal.set([]);
      return;
    }
    const desde = new Date(this.fecha + 'T00:00:00').toISOString();
    const hasta = new Date(this.fecha + 'T23:59:59').toISOString();
    this.cargandoSeguimiento.set(true);
    this.ubicacionesService.historial(acopiadorId, desde, hasta).subscribe({
      next: (eventos) => {
        if (consulta !== this.consultaSeguimiento || fecha !== this.fecha) return;
        this.eventosRecorrido.set(eventos);
        this.cargandoSeguimiento.set(false);
      },
      error: () => {
        if (consulta !== this.consultaSeguimiento || fecha !== this.fecha) return;
        this.cargandoSeguimiento.set(false);
        this.errorSeguimiento.set(
          'No se pudo consultar el historial GPS. Selecciona la ruta otra vez para reintentar.',
        );
      },
    });
    this.recojoService.listar({ acopiadorId, desde, hasta, size: 100 }).subscribe((pg) => {
      if (consulta !== this.consultaSeguimiento || fecha !== this.fecha) return;
      const zonas = new Set<string>();
      pg.content.forEach((r: Recojo) => {
        if (r.rutaNombre) zonas.add(r.rutaNombre);
      });
      this.zonasRecorridasSignal.set([...zonas]);
    });
  }

  private cargarProveedoresSeguimiento(rutaId: number | undefined, consulta: number): void {
    const fecha = this.fecha;
    this.proveedoresSeguimiento.set([]);
    this.cargandoProveedoresSeguimiento.set(false);
    const rutas = rutaId ? this.plan().filter((ruta) => ruta.rutaId === rutaId) : this.plan();
    if (rutas.length === 0) {
      this.proveedoresSeguimiento.set([]);
      return;
    }
    this.cargandoProveedoresSeguimiento.set(true);
    forkJoin(
      rutas.map((ruta) =>
        this.rutasService.proveedores(ruta.rutaId).pipe(
          catchError(() => of([] as ProveedorDeRuta[])),
          map((proveedores) =>
            proveedores.map((proveedor) => ({
              ...proveedor,
              rutaId: ruta.rutaId,
              rutaNombre: ruta.rutaNombre,
            })),
          ),
        ),
      ),
    ).subscribe((grupos) => {
      if (consulta !== this.consultaSeguimiento || fecha !== this.fecha) return;
      this.proveedoresSeguimiento.set(
        grupos
          .flat()
          .sort(
            (a, b) =>
              a.rutaNombre.localeCompare(b.rutaNombre) ||
              (a.ordenEnRuta ?? 999) - (b.ordenEnRuta ?? 999),
          ),
      );
      this.cargandoProveedoresSeguimiento.set(false);
    });
  }

  guardarObservacion(asig: AsignacionRuta): void {
    this.rutasService
      .actualizarAsignacion(asig.id, { observacion: this.observacionEdicion })
      .subscribe(() => this.cargarTodo());
  }

  /** Cuando se exporta desde la pestaña "Puntos establecidos" se descarga esa hoja, no "Rutas". */
  exportar(
    hoja = this.tab() === 'puntos' ||
    (this.tab() === 'territorio' && this.seccionPaso3() === 'vivienda')
      ? 'PuntosEstablecidos'
      : 'Rutas',
  ): void {
    this.exportacionesService.descargar(hoja).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `Huata_${hoja}.xlsx`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  private cargarRutas(): void {
    this.rutasService.listar().subscribe((pg) => this.rutas.set(pg.content));
  }

  // ---- Geometría de la ruta y proveedores en orden (2.1.3-2.1.6) ----

  seleccionarRutaPuntos(id: number | undefined): void {
    if (this.guardandoPuntoEstablecido()) return;
    this.cancelarFormPuntoEstablecido();
    this.rutaPuntosEstablecidosId.set(id);
    this.cargarPuntosEstablecidos();
    this.rutaPuntosId.set(id);
    this.rutaPuntosEstablecidosId.set(id);
    this.puntosCargados.set(false);
    this.mensajePuntos.set('');
    this.puntosRuta.set([]);
    this.proveedoresRuta.set([]);
    this.pintandoRuta.set(false);
    this.proveedorAUbicar.set(null);
    if (!id) {
      this.puntosRuta.set([]);
      this.proveedoresRuta.set([]);
      return;
    }
    this.cargandoPuntos.set(true);
    this.cargarPuntosEstablecidos();
    this.rutasService.puntos(id).subscribe({
      next: (p) => {
        if (this.rutaPuntosId() === id) {
          this.puntosRuta.set(p);
          this.cargandoPuntos.set(false);
          this.puntosCargados.set(true);
        }
      },
      error: () => {
        if (this.rutaPuntosId() === id) {
          this.mensajePuntos.set('No se pudieron cargar los puntos. Vuelve a seleccionar la ruta.');
          this.cargandoPuntos.set(false);
        }
      },
    });
    this.rutasService.proveedores(id).subscribe({
      next: (p) => {
        if (this.rutaPuntosId() === id) {
          this.proveedoresRuta.set(p);
          this.proveedoresDeRutaPE.set(p);
        }
      },
      error: () => this.mensajePuntos.set('No se pudieron consultar los proveedores de la ruta.'),
    });
  }

  /**
   * Manejador de clic unificado para el mapa. Según el modo activo (Vivienda, Trazado, Acopio,
   * Zona) o si se está ubicando un proveedor, realiza la acción de inmediato sin requerir
   * cambios de modo adicionales.
   */
  async clicEnElMapa(coordenadas: { latitud: number; longitud: number }): Promise<void> {
    if (this.proveedorAUbicar()) {
      await this.guardarUbicacionDeProveedor(coordenadas);
      return;
    }
    if (this.modoMapa() === 'vivienda' || this.agregandoModoPE()) {
      if (!this.agregandoModoPE()) {
        this.activarModoAgregarPuntoEstablecido();
      }
      this.clicMapaPuntoEstablecido(coordenadas);
      return;
    }
    if (this.modoMapa() === 'acopio') {
      this.nuevoPuntoLat = Number(coordenadas.latitud.toFixed(6));
      this.nuevoPuntoLng = Number(coordenadas.longitud.toFixed(6));
      this.mensajePuntos.set(
        `Coordenadas de acopio capturadas: ${this.nuevoPuntoLat}, ${this.nuevoPuntoLng}. Haz clic en "Agregar punto y reordenar".`,
      );
      return;
    }
    if (this.modoMapa() === 'zona' || this.pintandoPoligono()) {
      this.agregarPuntoPoligono(coordenadas);
      return;
    }
    this.agregarPunto(coordenadas);
  }

  ubicarProveedor(proveedor: ProveedorDeRuta): void {
    this.cancelarFormPuntoEstablecido();
    this.pintandoRuta.set(false);
    this.proveedorAUbicar.set(proveedor);
    this.modoMapa.set('vivienda');
    this.mensajePuntos.set(
      `Haz clic en el mapa para marcar dónde está ${proveedor.nombres} ${proveedor.apellidos}.`,
    );
  }

  cancelarUbicacion(): void {
    this.proveedorAUbicar.set(null);
    this.mensajePuntos.set('');
  }

  private async guardarUbicacionDeProveedor(coordenadas: {
    latitud: number;
    longitud: number;
  }): Promise<void> {
    const proveedor = this.proveedorAUbicar();
    if (proveedor) await this.moverProveedorEnMapa(proveedor, coordenadas);
  }

  agregarPunto(coordenadas?: { latitud: number; longitud: number }): void {
    if (
      !this.puedeEditar() ||
      !this.puntosCargados() ||
      this.cargandoPuntos() ||
      this.guardandoPuntos()
    )
      return;
    const siguiente = this.puntosRuta().length + 1;
    this.puntosRuta.update((p) => [
      ...p,
      { secuencia: siguiente, ...(coordenadas ?? { latitud: -15.65, longitud: -69.98 }) },
    ]);
    if (coordenadas) {
      // Antes cada clic se guardaba solo; ahora el recorrido se guarda una vez y con confirmación.
      this.mensajePuntos.set(
        `${this.puntosRuta().length} punto(s) sin guardar. Pulsa «Guardar recorrido» al terminar.`,
      );
    } else {
      this.mensajePuntos.set('Coordenada añadida. Completa sus valores y guarda los cambios.');
    }
  }

  editarCoordenada(index: number, campo: 'latitud' | 'longitud', valor: number) {
    if (!Number.isFinite(valor) || Math.abs(valor) > (campo === 'latitud' ? 90 : 180)) {
      this.mensajePuntos.set('Coordenada fuera de rango.');
      return;
    }
    this.puntosRuta.update((p) =>
      p.map((punto, i) => (i === index ? { ...punto, [campo]: valor } : punto)),
    );
    this.mensajePuntos.set('Cambios sin guardar.');
  }

  moverPunto(index: number, direccion: number) {
    const puntos = [...this.puntosRuta()];
    const destino = index + direccion;
    if (destino < 0 || destino >= puntos.length) return;
    [puntos[index], puntos[destino]] = [puntos[destino], puntos[index]];
    this.puntosRuta.set(puntos.map((p, i) => ({ ...p, secuencia: i + 1 })));
    this.mensajePuntos.set('Orden actualizado. Guarda el trazado para conservarlo.');
  }

  quitarPunto(index: number): void {
    this.puntosRuta.update((p) =>
      p.filter((_, i) => i !== index).map((pt, i) => ({ ...pt, secuencia: i + 1 })),
    );
    this.mensajePuntos.set('Cambios sin guardar.');
  }

  async guardarPuntosRuta(mensajeExito = 'Recorrido guardado.'): Promise<void> {
    const rutaId = this.rutaPuntosId();
    if (!rutaId || !this.puedeEditar() || !this.puntosCargados() || this.guardandoPuntos()) return;
    const nombre = this.nombreRutaPunto();
    const ok = this.puntosRuta().length
      ? await this.confirmarCambio(
          `¿Guardar el recorrido de ${nombre}?`,
          `${this.puntosRuta().length} punto(s). Reemplaza al recorrido guardado de esta ruta.`,
        )
      : await this.confirmarEliminacion(
          nombre,
          `¿Borrar el recorrido de ${nombre}?`,
          'La ruta se queda sin línea de recorrido en el mapa.',
          'Borrar recorrido',
        );
    if (!ok) return;
    this.pintandoRuta.set(false);
    this.guardandoPuntos.set(true);
    const peticion = this.puntosRuta().length
      ? this.rutasService.guardarPuntos(rutaId, this.puntosRuta())
      : this.rutasService.eliminarPuntos(rutaId).pipe(map(() => [] as RutaPunto[]));
    peticion.subscribe({
      next: (p) => {
        if (this.rutaPuntosId() === rutaId) {
          this.puntosRuta.set(p ?? []);
          this.mensajePuntos.set(mensajeExito);
        }
        this.guardandoPuntos.set(false);
      },
      error: () => {
        this.guardandoPuntos.set(false);
        this.mensajePuntos.set('No se pudo guardar el trazado. Tus cambios siguen en pantalla.');
      },
    });
  }

  /**
   * Punto de acopio dinámico: se agrega por latitud/longitud escritas a mano (sin necesidad de
   * hacer clic en el mapa) y el backend reordena de una vez a los proveedores por cercanía real a
   * ese punto — es el "cambio de destino" que decide el administrador y que la app móvil del
   * acopiador ve reflejado en el orden de visita al refrescar su ruta.
   */
  async agregarPuntoDeAcopioDinamico(): Promise<void> {
    const rutaId = this.rutaPuntosId();
    if (!rutaId || !this.puedeEditar() || this.agregandoPuntoAcopio()) return;
    if (
      this.nuevoPuntoLat == null ||
      this.nuevoPuntoLng == null ||
      Math.abs(this.nuevoPuntoLat) > 90 ||
      Math.abs(this.nuevoPuntoLng) > 180
    ) {
      this.mensajePuntos.set('Indica una latitud (-90 a 90) y una longitud (-180 a 180) válidas.');
      return;
    }
    const ok = await this.confirmarCambio(
      '¿Agregar el destino y reordenar a los proveedores?',
      'El orden de visita de esta ruta cambiará según la cercanía a ese punto.',
      'Sí, reordenar',
    );
    if (!ok) return;
    this.agregandoPuntoAcopio.set(true);
    this.rutasService
      .agregarPuntoDeAcopio(rutaId, this.nuevoPuntoLat, this.nuevoPuntoLng)
      .subscribe({
        next: (resultado) => {
          this.agregandoPuntoAcopio.set(false);
          if (this.rutaPuntosId() !== rutaId) return;
          this.puntosRuta.set(resultado.puntos);
          this.proveedoresRuta.set(resultado.proveedores);
          this.nuevoPuntoLat = null;
          this.nuevoPuntoLng = null;
          this.mensajePuntos.set(
            'Punto de acopio agregado. Los proveedores quedaron reordenados por cercanía a ese punto.',
          );
        },
        error: (e) => {
          this.agregandoPuntoAcopio.set(false);
          this.mensajePuntos.set(e.error?.message ?? 'No se pudo agregar el punto de acopio.');
        },
      });
  }

  /** Reordena a los proveedores por cercanía sin agregar un punto nuevo al trazo. */
  async reordenarPorCercania(): Promise<void> {
    const rutaId = this.rutaPuntosId();
    if (!rutaId || !this.puedeEditar() || this.reordenando()) return;
    const ok = await this.confirmarCambio(
      '¿Reordenar a los proveedores por cercanía?',
      'El orden de visita de esta ruta cambiará.',
      'Sí, reordenar',
    );
    if (!ok) return;
    this.reordenando.set(true);
    this.rutasService.reordenarPorCercania(rutaId).subscribe({
      next: (proveedores) => {
        this.reordenando.set(false);
        if (this.rutaPuntosId() !== rutaId) return;
        this.proveedoresRuta.set(proveedores);
        this.mensajePuntos.set(
          'Proveedores reordenados por cercanía real desde el inicio del trazado.',
        );
      },
      error: (e) => {
        this.reordenando.set(false);
        this.mensajePuntos.set(e.error?.message ?? 'No se pudo reordenar la ruta.');
      },
    });
  }

  // ---- Polígono de zona: dibuja el territorio en el mapa de Rutas y movilidad ----

  seleccionarPoligono(zona: string | undefined): void {
    this.zonaPoligonoSeleccionada.set(zona);
    this.pintandoPoligono.set(false);
    this.mensajePoligono.set('');
    const existente = zona ? this.zonaPoligonos().find((z) => z.zona === zona) : undefined;
    this.poligonoPuntos.set(existente ? existente.puntos.map((p) => ({ ...p })) : []);
    this.poligonoColor = existente?.color ?? '#437862';
  }

  agregarPuntoPoligono(coordenadas?: { latitud: number; longitud: number }): void {
    if (!this.puedeEditar() || !this.zonaPoligonoSeleccionada() || this.guardandoPoligono()) return;
    const siguiente = this.poligonoPuntos().length + 1;
    this.poligonoPuntos.update((p) => [
      ...p,
      { secuencia: siguiente, ...(coordenadas ?? { latitud: -15.65, longitud: -69.98 }) },
    ]);
    this.mensajePoligono.set('Vértice añadido. Guarda el polígono para conservarlo.');
  }

  editarCoordenadaPoligono(index: number, campo: 'latitud' | 'longitud', valor: number): void {
    if (!Number.isFinite(valor) || Math.abs(valor) > (campo === 'latitud' ? 90 : 180)) {
      this.mensajePoligono.set('Coordenada fuera de rango.');
      return;
    }
    this.poligonoPuntos.update((p) =>
      p.map((punto, i) => (i === index ? { ...punto, [campo]: valor } : punto)),
    );
    this.mensajePoligono.set('Cambios sin guardar.');
  }

  moverPuntoPoligono(index: number, direccion: number): void {
    const puntos = [...this.poligonoPuntos()];
    const destino = index + direccion;
    if (destino < 0 || destino >= puntos.length) return;
    [puntos[index], puntos[destino]] = [puntos[destino], puntos[index]];
    this.poligonoPuntos.set(puntos.map((p, i) => ({ ...p, secuencia: i + 1 })));
    this.mensajePoligono.set('Orden actualizado. Guarda para conservarlo.');
  }

  quitarPuntoPoligono(index: number): void {
    this.poligonoPuntos.update((p) =>
      p.filter((_, i) => i !== index).map((pt, i) => ({ ...pt, secuencia: i + 1 })),
    );
    this.mensajePoligono.set('Cambios sin guardar.');
  }

  async guardarPoligono(): Promise<void> {
    const zona = this.zonaPoligonoSeleccionada();
    if (!zona || !this.puedeEditar() || this.guardandoPoligono()) return;
    if (this.poligonoPuntos().length < 3) {
      this.mensajePoligono.set('Se necesitan al menos 3 vértices para formar un polígono.');
      return;
    }
    const anterior = this.poligonoExistente();
    const ok = await this.confirmarCambio(
      `¿Guardar el límite de «${zona}»?`,
      anterior
        ? `Reemplaza el límite actual (${anterior.puntos.length} vértices) por el nuevo (${this.poligonoPuntos().length}). ` +
            'El actual queda en el historial por si necesitas volver a él.'
        : `Se guarda con ${this.poligonoPuntos().length} vértices.`,
    );
    if (!ok) return;
    this.guardandoPoligono.set(true);
    this.rutasService
      .guardarPoligonoDeZona(zona, this.poligonoPuntos(), this.poligonoColor)
      .subscribe({
        next: (guardado) => {
          this.guardandoPoligono.set(false);
          this.pintandoPoligono.set(false);
          this.mensajePoligono.set('Límite guardado.');
          this.zonaPoligonos.update((zs) => [
            ...zs.filter((z) => z.zona !== guardado.zona),
            guardado,
          ]);
          this.cargarHistorialLimite();
        },
        error: () => {
          this.guardandoPoligono.set(false);
          this.mensajePoligono.set(
            'No se pudo guardar el polígono. Tus cambios siguen en pantalla.',
          );
        },
      });
  }

  async eliminarPoligonoActual(): Promise<void> {
    const existente = this.poligonoExistente();
    if (!existente) {
      this.poligonoPuntos.set([]);
      this.mensajePoligono.set('');
      return;
    }
    const ok = await this.confirmarEliminacion(
      existente.zona,
      `¿Borrar el límite de «${existente.zona}»?`,
      'La zona y sus rutas siguen existiendo; solo se borra su dibujo. Podrás restaurarlo desde el historial.',
      'Borrar límite',
    );
    if (!ok) return;
    this.rutasService.eliminarPoligonoDeZona(existente.id).subscribe(() => {
      this.zonaPoligonos.update((zs) => zs.filter((z) => z.id !== existente.id));
      this.poligonoPuntos.set([]);
      this.mensajePoligono.set('Polígono eliminado.');
    });
  }

  // ---- Flujo territorial: 1 zona -> 2 acopiador -> 3 trazado de su ruta ----

  /**
   * Un solo punto de entrada a la zona: deja listos el polígono (paso 1), sus rutas y acopiadores
   * (paso 2) y el trazado de su primera ruta (paso 3), para no tener que volver a elegir la zona en
   * cada paso.
   */
  /**
   * Permite llegar a una zona concreta desde otra pantalla (/rutas?tab=territorio&zona=NOMBRE).
   * Se ejecuta con los polígonos ya cargados porque la zona puede existir solo como polígono.
   */
  private abrirDesdeLaUrl(): void {
    const params = this.ruta.snapshot.queryParamMap;
    const tabParam = params.get('tab');
    const esPuntos = tabParam === 'puntos';
    if (esPuntos || tabParam === 'territorio') {
      this.tab.set('territorio');
    } else {
      return;
    }
    const zona = params.get('zona');
    if (!zona || !this.zonasDisponibles().includes(zona)) return;
    this.seleccionarZona(zona, esPuntos ? 3 : 1);
    if (esPuntos) {
      this.seccionPaso3.set('vivienda');
      this.setModoMapa('vivienda');
    }

    // "Pintar ruta" desde otra pantalla (p. ej. Acopiadores) aterriza directamente en el trazado de
    // esa ruta, sin obligar a volver a elegir zona y ruta aquí.
    const rutaId = Number(params.get('ruta'));
    if (Number.isFinite(rutaId) && this.rutasDeLaZona(zona).some((r) => r.id === rutaId)) {
      this.seleccionarRutaPuntos(rutaId);
      this.pasoTerritorio.set(3);
    }
  }

  seleccionarZona(zona: string | undefined, paso: 1 | 2 | 3 = 1): void {
    if (this.guardandoPuntoEstablecido()) return;
    this.seleccionarPoligono(zona);
    // Ya no hay pestaña "Límite": pedir el paso 1 abre "Rutas y personas" con el límite desplegado.
    const abrirLimite = paso === 1 && !!zona && zona !== SIN_ZONA;
    this.pasoTerritorio.set(paso === 1 ? 2 : paso);
    this.limiteAbierto.set(abrirLimite);
    this.mensajeZona.set('');
    this.errorZona.set('');
    this.renombrandoZona.set(false);
    this.editandoRutaZonaId.set(null);
    this.formAcopiadorAbierto.set(false);
    this.formRutaZona = { nombre: '', descripcion: '' };
    this.recorridoAbiertoId.set(null);
    this.seleccionarRutaPuntos(zona ? this.rutasDeLaZona(zona)[0]?.id : undefined);
  }

  crearZona(): void {
    const zona = this.nuevaZonaPoligono.trim();
    if (!zona || !this.puedeEditar()) return;
    const repetida = this.zonasDisponibles().some(
      (z) => z.toLocaleLowerCase('es') === zona.toLocaleLowerCase('es'),
    );
    if (repetida) {
      this.errorZona.set('Ya existe una zona llamada ' + zona + '.');
      return;
    }
    this.seleccionarZona(undefined);
    this.zonaPoligonoSeleccionada.set(zona);
    this.poligonoPuntos.set([]);
    this.poligonoColor = '#437862';
    this.nuevaZonaPoligono = '';
    this.tab.set('territorio');
    this.pasoTerritorio.set(2);
    this.limiteAbierto.set(true);
    this.pintandoPoligono.set(true);
    this.mensajePoligono.set(
      'Zona nueva: marca sus vértices en el mapa y guarda el polígono. La zona queda registrada al guardar el polígono o al crearle su primera ruta.',
    );
  }

  abrirRenombrarZona(): void {
    this.nombreZonaEditado = this.zonaPoligonoSeleccionada() ?? '';
    this.errorZona.set('');
    this.renombrandoZona.set(true);
  }

  async renombrarZona(): Promise<void> {
    const zona = this.zonaPoligonoSeleccionada();
    const nuevo = this.nombreZonaEditado.trim();
    if (!zona || !nuevo || this.guardandoZona()) return;
    if (nuevo === zona) {
      this.renombrandoZona.set(false);
      return;
    }
    const ok = await this.confirmarCambio(
      `¿Renombrar «${zona}» a «${nuevo}»?`,
      'Cambia el nombre en todas sus rutas, sus proveedores y su límite.',
      'Sí, renombrar',
    );
    if (!ok) return;
    this.guardandoZona.set(true);
    this.rutasService.renombrarZona(zona, nuevo).subscribe({
      next: () => {
        this.guardandoZona.set(false);
        this.renombrandoZona.set(false);
        this.recargarTerritorio(nuevo, this.pasoTerritorio());
        this.mensajeZona.set('Zona renombrada a ' + nuevo + '.');
      },
      error: (e) => {
        this.guardandoZona.set(false);
        this.errorZona.set(e.error?.message ?? 'No se pudo renombrar la zona.');
      },
    });
  }

  async eliminarZona(): Promise<void> {
    const zona = this.zonaPoligonoSeleccionada();
    if (!zona || this.guardandoZona()) return;
    const ok = await this.confirmarEliminacion(
      zona,
      `¿Eliminar la zona «${zona}»?`,
      'Si todavía tiene rutas activas no se eliminará. Su límite queda en el historial.',
      'Eliminar zona',
    );
    if (!ok) return;
    this.guardandoZona.set(true);
    this.errorZona.set('');
    this.rutasService.eliminarZona(zona).subscribe({
      next: () => {
        this.guardandoZona.set(false);
        this.recargarTerritorio(undefined);
        this.mensajeZona.set('Zona ' + zona + ' eliminada.');
      },
      error: (e) => {
        this.guardandoZona.set(false);
        this.errorZona.set(e.error?.message ?? 'No se pudo eliminar la zona.');
      },
    });
  }

  // ---- Paso 2: rutas de la zona ----

  crearRutaEnZona(): void {
    const zona = this.zonaPoligonoSeleccionada();
    const nombre = this.formRutaZona.nombre.trim();
    if (!zona || !nombre || this.guardandoZona()) return;
    this.guardandoZona.set(true);
    this.errorZona.set('');
    this.rutasService
      .crear({ nombre, zona, descripcion: this.formRutaZona.descripcion.trim() || undefined })
      .subscribe({
        next: (creada) => {
          this.guardandoZona.set(false);
          this.formRutaZona = { nombre: '', descripcion: '' };
          this.formRutaAbierto.set(false);
          this.recargarTerritorio(zona, 2, creada.id);
          this.mensajeZona.set(
            'Ruta ' + creada.nombre + ' creada en ' + zona + '. Ya puedes asignarle un acopiador.',
          );
        },
        error: (e) => {
          this.guardandoZona.set(false);
          this.errorZona.set(e.error?.message ?? 'No se pudo crear la ruta.');
        },
      });
  }

  abrirEdicionRuta(ruta: Ruta): void {
    this.editandoRutaZonaId.set(ruta.id);
    this.formRutaEdicion = { nombre: ruta.nombre, descripcion: ruta.descripcion ?? '' };
    this.errorZona.set('');
  }

  async guardarEdicionRuta(ruta: Ruta): Promise<void> {
    const nombre = this.formRutaEdicion.nombre.trim();
    if (!nombre || this.guardandoZona()) return;
    const ok = await this.confirmarCambio(`¿Guardar los cambios de la ruta «${ruta.nombre}»?`);
    if (!ok) return;
    this.guardandoZona.set(true);
    this.rutasService
      .actualizar(ruta.id, {
        nombre,
        zona: ruta.zona,
        descripcion: this.formRutaEdicion.descripcion.trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.guardandoZona.set(false);
          this.editandoRutaZonaId.set(null);
          this.recargarTerritorio(this.zonaPoligonoSeleccionada(), 2, this.rutaPuntosId());
          this.mensajeZona.set('Ruta actualizada.');
        },
        error: (e) => {
          this.guardandoZona.set(false);
          this.errorZona.set(e.error?.message ?? 'No se pudo actualizar la ruta.');
        },
      });
  }

  /**
   * Baja lógica de la ruta. Se bloquea mientras algún acopiador la tenga como ruta habitual: sin
   * ruta activa con zona no se puede volver a guardar ese acopiador (lo exige el backend).
   */
  async darDeBajaRuta(ruta: Ruta): Promise<void> {
    if (this.guardandoZona()) return;
    const conAcopiador = this.acopiadores().filter((a) => a.rutaId === ruta.id);
    if (conAcopiador.length > 0) {
      const nombres = conAcopiador.map((a) => a.nombres + ' ' + a.apellidos).join(', ');
      this.errorZona.set('Primero mueve a otra ruta a ' + nombres + '.');
      return;
    }
    const ok = await this.confirmarEliminacion(
      ruta.nombre,
      `¿Dar de baja la ruta «${ruta.nombre}»?`,
      'Deja de aparecer en el mapa y en la app del acopiador.',
      'Dar de baja',
    );
    if (!ok) return;
    this.guardandoZona.set(true);
    this.errorZona.set('');
    this.rutasService.inactivar(ruta.id).subscribe({
      next: () => {
        this.guardandoZona.set(false);
        this.recargarTerritorio(this.zonaPoligonoSeleccionada(), 2);
        this.mensajeZona.set('Ruta ' + ruta.nombre + ' dada de baja.');
      },
      error: (e) => {
        this.guardandoZona.set(false);
        this.errorZona.set(e.error?.message ?? 'No se pudo dar de baja la ruta.');
      },
    });
  }

  // ---- Paso 2: acopiador de la zona ----

  abrirFormAcopiador(): void {
    this.errorZona.set('');
    this.acopiadorExistenteId = undefined;
    this.rutaAcopiadorZonaId = this.rutasDeZonaSeleccionada()[0]?.id;
    this.formAcopiadorAbierto.set(true);
  }

  /** Mueve un acopiador existente a una ruta de esta zona (Acopiador.ruta -> zona). */
  async guardarAcopiadorZona(): Promise<void> {
    if (this.guardandoZona()) return;
    const rutaId = this.rutaAcopiadorZonaId;
    if (!rutaId) {
      this.errorZona.set('Selecciona la ruta de esta zona donde trabajará el acopiador.');
      return;
    }
    const existente = this.acopiadores().find((a) => a.id === this.acopiadorExistenteId);
    if (!existente) {
      this.errorZona.set('Selecciona el acopiador que quieres traer a esta zona.');
      return;
    }
    const destino = this.rutas().find((r) => r.id === rutaId)?.nombre ?? 'la ruta elegida';
    const ok = await this.confirmarCambio(
      `¿Pasar a ${existente.nombres} ${existente.apellidos} a ${destino}?`,
      existente.rutaNombre ? `Deja de acopiar en ${existente.rutaNombre}.` : undefined,
      'Sí, asignar',
    );
    if (!ok) return;
    this.guardandoZona.set(true);
    this.acopiadoresService.actualizar(existente.id, this.datosDe(existente, rutaId)).subscribe({
      next: () => {
        this.guardandoZona.set(false);
        this.formAcopiadorAbierto.set(false);
        this.recargarTerritorio(this.zonaPoligonoSeleccionada(), 2, this.rutaPuntosId());
        this.mensajeZona.set('Acopiador asignado a la zona.');
      },
      error: (e) => {
        this.guardandoZona.set(false);
        this.errorZona.set(e.error?.message ?? 'No se pudo guardar el acopiador.');
      },
    });
  }

  /** Cambia de ruta a un acopiador que ya trabaja en la zona. */
  async moverAcopiadorDeRuta(acopiador: Acopiador, rutaId: number): Promise<void> {
    if (this.guardandoZona() || !rutaId || acopiador.rutaId === rutaId) return;
    const destino = this.rutas().find((r) => r.id === rutaId)?.nombre ?? 'otra ruta';
    const ok = await this.confirmarCambio(
      `¿Pasar a ${acopiador.nombres} ${acopiador.apellidos} a ${destino}?`,
      acopiador.rutaNombre ? `Deja de acopiar en ${acopiador.rutaNombre}.` : undefined,
      'Sí, cambiar de ruta',
    );
    if (!ok) {
      // El selector ya muestra la ruta nueva: se vuelve a leer para que muestre la de verdad.
      this.recargarTerritorio(this.zonaPoligonoSeleccionada(), 2, this.rutaPuntosId());
      return;
    }
    this.guardandoZona.set(true);
    this.errorZona.set('');
    this.acopiadoresService.actualizar(acopiador.id, this.datosDe(acopiador, rutaId)).subscribe({
      next: () => {
        this.guardandoZona.set(false);
        this.recargarTerritorio(this.zonaPoligonoSeleccionada(), 2, this.rutaPuntosId());
        this.mensajeZona.set(acopiador.nombres + ' ' + acopiador.apellidos + ' cambió de ruta.');
      },
      error: (e) => {
        this.guardandoZona.set(false);
        this.errorZona.set(e.error?.message ?? 'No se pudo cambiar la ruta del acopiador.');
      },
    });
  }

  /**
   * El backend actualiza el acopiador con el registro completo, así que se reenvían sus datos
   * actuales cambiando solo la ruta. La contraseña va vacía a propósito: significa "conserva la
   * actual" (ver AcopiadorService.sincronizarCuenta).
   */
  private datosDe(acopiador: Acopiador, rutaId: number): AcopiadorRequest {
    return {
      nombres: acopiador.nombres,
      apellidos: acopiador.apellidos,
      documentoIdentidad: acopiador.documentoIdentidad,
      telefono: acopiador.telefono,
      username: acopiador.username,
      password: '',
      rutaId,
    };
  }

  // ---- Paso 2: proveedores de la zona (igual que los acopiadores) ----

  /** El padrón activo completo. 500 es el techo de página del backend; hoy hay 19. */
  private cargarProveedores() {
    return this.productoresService.listar({ size: 500 }).pipe(
      map((pg) => pg.content),
      catchError(() => of([] as Productor[])),
    );
  }

  abrirFormProveedor(): void {
    this.errorZona.set('');
    this.proveedorExistenteId = undefined;
    this.ordenProveedorZona = undefined;
    this.rutaProveedorZonaId = this.rutasDeZonaSeleccionada()[0]?.id;
    this.formProveedorAbierto.set(true);
  }

  /** Trae a un proveedor de otra zona (o sin ruta) a una ruta de esta zona. */
  async traerProveedorAZona(): Promise<void> {
    if (this.guardandoZona()) return;
    const proveedor = this.proveedores().find((p) => p.id === this.proveedorExistenteId);
    if (!proveedor) {
      this.errorZona.set('Selecciona el proveedor que quieres traer a esta zona.');
      return;
    }
    if (!this.rutaProveedorZonaId) {
      this.errorZona.set('Selecciona la ruta de esta zona donde lo visitarán.');
      return;
    }
    const destino =
      this.rutas().find((r) => r.id === this.rutaProveedorZonaId)?.nombre ?? 'la ruta elegida';
    const ok = await this.confirmarCambio(
      `¿Pasar a ${proveedor.nombres} ${proveedor.apellidos} a ${destino}?`,
      proveedor.rutaNombre ? `Deja de estar en ${proveedor.rutaNombre}.` : undefined,
      'Sí, cambiar',
    );
    if (!ok) return;
    this.asignarRutaProveedor(
      proveedor,
      this.rutaProveedorZonaId,
      this.ordenProveedorZona ?? null,
      () => {
        this.formProveedorAbierto.set(false);
        return `${proveedor.nombres} ${proveedor.apellidos} ahora está en ${this.zonaPoligonoSeleccionada()}.`;
      },
    );
  }

  /** Cambia de ruta a un proveedor que ya está en la zona. Pierde su orden: era el de la otra ruta. */
  async moverProveedorDeRuta(proveedor: Productor, rutaId: number): Promise<void> {
    if (!rutaId || proveedor.rutaId === rutaId) return;
    const destino = this.rutas().find((r) => r.id === rutaId)?.nombre ?? 'otra ruta';
    const ok = await this.confirmarCambio(
      `¿Pasar a ${proveedor.nombres} ${proveedor.apellidos} a ${destino}?`,
      'Pierde su orden de visita actual.',
      'Sí, cambiar de ruta',
    );
    if (!ok) {
      this.recargarTerritorio(this.zonaPoligonoSeleccionada(), 2, this.rutaPuntosId());
      return;
    }
    this.asignarRutaProveedor(
      proveedor,
      rutaId,
      null,
      () => `${proveedor.nombres} ${proveedor.apellidos} cambió de ruta.`,
    );
  }

  /** El orden en que el acopiador lo visita dentro de su ruta. Vacío = sin orden fijo. */
  async cambiarOrdenProveedor(proveedor: Productor, valor: string | number | null): Promise<void> {
    const texto = valor == null ? '' : String(valor).trim();
    const orden = texto === '' ? null : Number(texto);
    if (orden !== null && (!Number.isInteger(orden) || orden < 1)) {
      this.errorZona.set('El orden en la ruta es un número entero desde 1.');
      return;
    }
    if (!proveedor.rutaId || (proveedor.ordenEnRuta ?? null) === orden) return;
    const ok = await this.confirmarCambio(
      `¿Cambiar el orden de visita de ${proveedor.nombres} ${proveedor.apellidos}?`,
      `De ${proveedor.ordenEnRuta ?? 'sin orden'} a ${orden ?? 'sin orden'}.`,
      'Sí, cambiar',
    );
    if (!ok) {
      this.recargarTerritorio(this.zonaPoligonoSeleccionada(), 2, this.rutaPuntosId());
      return;
    }
    this.asignarRutaProveedor(
      proveedor,
      proveedor.rutaId,
      orden,
      () => `Orden de ${proveedor.nombres} ${proveedor.apellidos} actualizado.`,
    );
  }

  private asignarRutaProveedor(
    proveedor: Productor,
    rutaId: number,
    orden: number | null,
    mensaje: () => string,
  ): void {
    if (this.guardandoZona()) return;
    this.guardandoZona.set(true);
    this.errorZona.set('');
    this.productoresService.asignarRuta(proveedor.id, rutaId, orden).subscribe({
      next: () => {
        this.guardandoZona.set(false);
        const texto = mensaje();
        this.recargarTerritorio(this.zonaPoligonoSeleccionada(), 2, this.rutaPuntosId());
        this.mensajeZona.set(texto);
      },
      error: (e) => {
        this.guardandoZona.set(false);
        this.errorZona.set(e.error?.message ?? 'No se pudo cambiar la ruta del proveedor.');
      },
    });
  }

  /** Vuelve a leer rutas y acopiadores tras un cambio territorial y deja la zona/ruta en pantalla. */
  private recargarTerritorio(zona: string | undefined, paso: 1 | 2 | 3 = 1, rutaId?: number): void {
    // Los polígonos entran aquí porque el nombre de la zona también vive en ellos: tras renombrar
    // o eliminar una zona, quedarse con la copia anterior mostraría la zona dos veces en la lista.
    forkJoin({
      rutas: this.rutasService.listar('', 0, 200),
      acopiadores: this.acopiadoresService.listar('', 0, 200),
      proveedores: this.cargarProveedores(),
      poligonos: this.rutasService.poligonosDeZona(),
    }).subscribe(({ rutas, acopiadores, proveedores, poligonos }) => {
      this.rutas.set(rutas.content);
      this.acopiadores.set(acopiadores.content);
      this.proveedores.set(proveedores);
      this.zonaPoligonos.set(poligonos);
      this.cargarTodo();
      this.zonaPoligonoSeleccionada.set(zona);
      this.pasoTerritorio.set(paso);
      if (zona) {
        const existente = this.zonaPoligonos().find((z) => z.zona === zona);
        this.poligonoPuntos.set(existente ? existente.puntos.map((pt) => ({ ...pt })) : []);
        this.poligonoColor = existente?.color ?? '#437862';
        const rutasZona = this.rutasDeLaZona(zona);
        const objetivo =
          rutaId && rutasZona.some((r) => r.id === rutaId) ? rutaId : rutasZona[0]?.id;
        this.seleccionarRutaPuntos(objetivo);
      } else {
        this.poligonoPuntos.set([]);
        this.seleccionarRutaPuntos(undefined);
      }
    });
  }

  // ---- Puntos establecidos: referencia fija que administración crea a mano en el mapa ----

  cargarPuntosEstablecidos(): void {
    const rutaId = this.rutaPuntosEstablecidosId();
    this.cargandoPuntosEstablecidos.set(true);
    this.puntosEstablecidosService
      .listar(this.rutaPuntosEstablecidosId(), this.puedeEditar())
      .subscribe({
        next: (p) => {
          if (this.rutaPuntosEstablecidosId() !== rutaId) return;
          this.puntosEstablecidos.set(p);
          this.errorPuntoEstablecido.set('');
          this.cargandoPuntosEstablecidos.set(false);
        },
        error: () => {
          if (this.rutaPuntosEstablecidosId() !== rutaId) return;
          this.errorPuntoEstablecido.set('No se pudieron cargar los puntos establecidos.');
          this.cargandoPuntosEstablecidos.set(false);
        },
      });
  }

  cambiarRutaPuntosEstablecidos(rutaId: number | undefined): void {
    this.rutaPuntosEstablecidosId.set(rutaId);
    this.cargarPuntosEstablecidos();
  }

  /** Al elegir/cambiar la ruta del formulario se recarga la lista de proveedores para asociar. */
  alCambiarRutaDelFormulario(rutaId: number | undefined): void {
    this.formPuntoEstablecido.rutaId = rutaId;
    this.formPuntoEstablecido.proveedorId = undefined;
    if (!rutaId) {
      this.proveedoresDeRutaPE.set([]);
      return;
    }
    this.rutasService.proveedores(rutaId).subscribe((p) => this.proveedoresDeRutaPE.set(p));
  }

  activarModoAgregarPuntoEstablecido(): void {
    if (!this.puedeEditar()) return;
    this.editandoPuntoEstablecidoId.set(null);
    this.modoAltaPunto.set('referencia');
    this.pintandoRuta.set(false);
    this.proveedorAUbicar.set(null);
    const rutaId = this.rutaPuntosId() ?? this.rutaPuntosEstablecidosId();
    this.formPuntoEstablecido = {
      nombre: '',
      latitud: null,
      longitud: null,
      rutaId: rutaId,
    };
    if (rutaId) {
      this.alCambiarRutaDelFormulario(rutaId);
    } else {
      this.proveedoresDeRutaPE.set([]);
    }
    this.agregandoModoPE.set(true);
    this.modoMapa.set('vivienda');
    this.seccionPaso3.set('vivienda');
    this.actualizarDraftMapa();
    this.mensajePuntoEstablecido.set(
      'Haz clic en el mapa para marcar el punto, o escribe la latitud y longitud a mano.',
    );
    this.errorPuntoEstablecido.set('');
  }

  editarPuntoEstablecido(p: PuntoEstablecido): void {
    if (!this.puedeEditar()) return;
    if (p.proveedorId && !this.puedeMoverProveedores()) return;
    this.pintandoRuta.set(false);
    this.proveedorAUbicar.set(null);
    this.modoAltaPunto.set(p.proveedorId ? 'existente' : 'referencia');
    this.puntoSeleccionado.set(p.id);
    this.editandoPuntoEstablecidoId.set(p.id);
    this.formPuntoEstablecido = {
      nombre: p.nombre,
      latitud: p.latitud,
      longitud: p.longitud,
      rutaId: p.rutaId,
      acopiadorId: p.acopiadorId,
      proveedorId: p.proveedorId,
      observacion: p.observacion,
    };
    this.rutasService.proveedores(p.rutaId).subscribe((prov) => this.proveedoresDeRutaPE.set(prov));
    this.agregandoModoPE.set(true);
    this.actualizarDraftMapa();
    this.mensajePuntoEstablecido.set(
      'Editando "' + p.nombre + '". Haz clic en el mapa para mover el punto.',
    );
    this.errorPuntoEstablecido.set('');
  }

  /** Autocompleta latitud/longitud con el clic en el mapa (modo agregar o editar). */
  clicMapaPuntoEstablecido(coordenadas: { latitud: number; longitud: number }): void {
    if (!this.agregandoModoPE()) return;
    this.formPuntoEstablecido.latitud = coordenadas.latitud;
    this.formPuntoEstablecido.longitud = coordenadas.longitud;
    this.actualizarDraftMapa();
  }

  cancelarFormPuntoEstablecido(): void {
    if (this.guardandoPuntoEstablecido()) return;
    this.agregandoModoPE.set(false);
    this.editandoPuntoEstablecidoId.set(null);
    this.mensajePuntoEstablecido.set('');
    this.errorPuntoEstablecido.set('');
    this.actualizarDraftMapa();
  }

  /**
   * `alCancelar` deshace lo que ya se movió en pantalla (un marcador arrastrado) si la persona no
   * acepta el cambio.
   */
  async guardarPuntoEstablecido(alCancelar?: () => void): Promise<void> {
    if (!this.puedeEditar() || this.guardandoPuntoEstablecido()) return;
    const f = this.formPuntoEstablecido;
    if (this.modoAltaPunto() !== 'referencia' && !this.puedeMoverProveedores()) return;
    if (this.modoAltaPunto() === 'existente' && !f.proveedorId) {
      this.errorPuntoEstablecido.set('Selecciona el proveedor que quieres ubicar.');
      return;
    }
    if (!f.nombre.trim()) {
      this.errorPuntoEstablecido.set('Indica un nombre para el punto.');
      return;
    }
    if (!f.rutaId) {
      this.errorPuntoEstablecido.set('Selecciona la ruta a la que pertenece el punto.');
      return;
    }
    if (f.latitud == null || f.longitud == null) {
      this.errorPuntoEstablecido.set(
        'Falta ubicar en el mapa. Haz clic para colocar el punto antes de guardar.',
      );
      return;
    }
    if (
      !Number.isFinite(f.latitud) ||
      !Number.isFinite(f.longitud) ||
      Math.abs(f.latitud) > 90 ||
      Math.abs(f.longitud) > 180
    ) {
      this.errorPuntoEstablecido.set(
        'Indica una latitud (-90 a 90) y una longitud (-180 a 180) válidas.',
      );
      return;
    }
    this.errorPuntoEstablecido.set('');
    const editando = this.editandoPuntoEstablecidoId();
    const ok = await this.confirmarCambio(
      editando
        ? `¿Guardar los cambios del punto «${f.nombre.trim()}»?`
        : `¿Crear el punto «${f.nombre.trim()}»?`,
      'Se verá así en el mapa y en la app del acopiador.',
    );
    if (!ok) {
      alCancelar?.();
      return;
    }
    this.guardandoPuntoEstablecido.set(true);
    const datos = {
      nombre: f.nombre.trim(),
      latitud: f.latitud,
      longitud: f.longitud,
      rutaId: f.rutaId,
      acopiadorId: f.acopiadorId,
      proveedorId: f.proveedorId,
      observacion: f.observacion?.trim() || undefined,
    };
    const id = this.editandoPuntoEstablecidoId();
    const peticion = id
      ? this.puntosEstablecidosService.actualizar(id, datos)
      : this.puntosEstablecidosService.crear(datos);
    peticion.subscribe({
      next: (punto) => {
        this.puntoSeleccionado.set(punto.id);
        this.guardandoPuntoEstablecido.set(false);
        this.agregandoModoPE.set(false);
        this.editandoPuntoEstablecidoId.set(null);
        this.mensajePuntoEstablecido.set(id ? 'Punto actualizado.' : 'Punto creado.');
        this.cargarPuntosEstablecidos();
        this.actualizarDraftMapa();
        this.proveedorAUbicar.set(null);
        if (f.rutaId)
          this.rutasService.proveedores(f.rutaId).subscribe((p) => {
            if (this.rutaPuntosId() === f.rutaId) {
              this.proveedoresRuta.set(p);
              this.proveedoresDeRutaPE.set(p);
            }
          });
        this.cargarProveedores().subscribe((p) => this.proveedores.set(p));
      },
      error: (e) => {
        this.guardandoPuntoEstablecido.set(false);
        this.errorPuntoEstablecido.set(
          e.error?.message ?? 'No se pudo guardar el punto establecido.',
        );
      },
    });
  }

  async desactivarPuntoEstablecido(p: PuntoEstablecido): Promise<void> {
    if (!this.puedeEditar()) return;
    const ok = await this.dialogo.confirmar({
      titulo: `¿Desactivar el punto «${p.nombre}»?`,
      mensaje: 'Deja de mostrarse en la app del acopiador. Puedes reactivarlo después.',
      confirmar: 'Desactivar',
      tipo: 'peligro',
    });
    if (!ok) return;
    this.puntosEstablecidosService.desactivar(p.id).subscribe({
      next: () => {
        this.mensajePuntoEstablecido.set('Punto "' + p.nombre + '" desactivado.');
        this.errorPuntoEstablecido.set('');
        this.cargarPuntosEstablecidos();
      },
      error: (e) => {
        this.errorPuntoEstablecido.set(e.error?.message ?? 'No se pudo desactivar el punto.');
      },
    });
  }

  async activarPuntoEstablecido(p: PuntoEstablecido): Promise<void> {
    if (!this.puedeEditar()) return;
    const ok = await this.confirmarCambio(
      `¿Activar el punto «${p.nombre}»?`,
      'Vuelve a mostrarse en la app del acopiador.',
      'Sí, activar',
    );
    if (!ok) return;
    this.puntosEstablecidosService.activar(p.id).subscribe({
      next: () => {
        this.errorPuntoEstablecido.set('');
        this.cargarPuntosEstablecidos();
      },
      error: (e) =>
        this.errorPuntoEstablecido.set(e.error?.message ?? 'No se pudo activar el punto.'),
    });
  }

  async eliminarPuntoEstablecido(p: PuntoEstablecido): Promise<void> {
    if (!this.puedeEditar()) return;
    const ok = await this.confirmarEliminacion(
      p.nombre,
      `¿Eliminar definitivamente el punto «${p.nombre}»?`,
      'No se podrá reactivar.',
      'Eliminar punto',
    );
    if (!ok) return;
    this.puntosEstablecidosService.eliminar(p.id).subscribe({
      next: () => {
        this.errorPuntoEstablecido.set('');
        this.cargarPuntosEstablecidos();
      },
      error: (e) =>
        this.errorPuntoEstablecido.set(e.error?.message ?? 'No se pudo eliminar el punto.'),
    });
  }
}
