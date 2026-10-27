import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import {
  ControlCalidad,
  EstadoIncidencia,
  IncidenciaCalidad,
  Productor,
  ResultadoCalidad,
} from '../../core/models/models';
import { PERMISO } from '../../core/config/navegacion';
import { AuthService } from '../../core/services/auth.service';
import { CalidadService, EvidenciaCalidad } from '../../core/services/calidad.service';
import { ProductoresService } from '../../core/services/productores.service';
import { PaginacionLocal } from '../../shared/ui/paginacion-local';
import { PaginadorComponent } from '../../shared/ui/paginador.component';
import { recargarEnVivo } from '../../core/services/cambios-tiempo-real.service';

const fechaLima = (d: Date) =>
  new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Lima',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(d);

/**
 * Calidad de la leche: los avisos (incidencias) de un día y el historial de controles de un
 * proveedor, con su voucher. Tarjetas y palabras simples en lugar de tablas y códigos.
 */
@Component({
  selector: 'app-calidad-resumen',
  standalone: true,
  imports: [PaginadorComponent, DatePipe, DecimalPipe, FormsModule, RouterLink],
  templateUrl: './calidad-resumen.component.html',
  styleUrl: './calidad-resumen.component.css',
})
export class CalidadResumenComponent implements OnInit, OnDestroy {
  /** Tiempo real: si alguien guarda algo de calidad, acopio, esta pantalla se actualiza sola. */
  private readonly _enVivo = recargarEnVivo(['calidad', 'acopio'], () => this.recargar());
  readonly vista = signal<'incidencias' | 'aprobar' | 'historial'>('incidencias');
  readonly hoy = fechaLima(new Date());
  readonly ayer = fechaLima(new Date(Date.now() - 86_400_000));
  fecha = this.hoy;
  busqueda = '';
  readonly pagAvisos = new PaginacionLocal();
  readonly pagControles = new PaginacionLocal();
  readonly incidencias = signal<IncidenciaCalidad[]>([]);
  readonly proveedores = signal<Productor[]>([]);
  readonly controles = signal<ControlCalidad[]>([]);
  /** Análisis registrados con la app que esperan aprobación (V49). */
  readonly porAprobar = signal<ControlCalidad[]>([]);
  private readonly auth = inject(AuthService);
  /**
   * calidad.aprobar se concede por rol o por persona desde "Usuarios y roles". Antes solo servia en la
   * app movil: concederselo a alguien que trabaja en la web no le daba ningun boton.
   */
  readonly puedeAprobar = computed(() => this.auth.puede(PERMISO.calidadAprobar));
  readonly aprobando = signal<number | null>(null);
  /** Análisis del testeador en campo que todavía esperan el recojo del acopiador (V47). */
  readonly sinRecojo = signal<ControlCalidad[]>([]);
  readonly nombre = signal('');
  readonly error = signal('');
  readonly cargando = signal(false);
  readonly buscando = signal(false);
  readonly cargandoHistorial = signal(false);
  readonly totalResultados = signal(0);
  readonly busquedaHecha = signal(false);
  readonly abriendoVoucher = signal<number | null>(null);
  readonly voucher = signal<{
    url?: string;
    evidencia?: EvidenciaCalidad;
    mensaje?: string;
  } | null>(null);
  private consulta = 0;
  constructor(
    private readonly calidad: CalidadService,
    private readonly productoresService: ProductoresService,
  ) {}
  ngOnInit() {
    this.recargar();
  }

  recargar(): void {
    this.cargando.set(true);
    this.calidad
      .incidencias()
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: (i) => this.incidencias.set(i),
        error: () => this.error.set('No se pudieron consultar las incidencias.'),
      });
    // Si falla, el bloque simplemente no aparece: no impide revisar los avisos.
    this.calidad.controlesSinRecojo().subscribe({ next: (c) => this.sinRecojo.set(c) });
    this.cargarPorAprobar();
  }

  cargarPorAprobar() {
    this.calidad.porAprobar().subscribe({
      next: (c) => this.porAprobar.set(c),
      error: () => this.error.set('No se pudieron consultar los análisis por aprobar.'),
    });
  }

  aprobar(c: ControlCalidad): void {
    if (!this.puedeAprobar() || this.aprobando() !== null) return;
    this.aprobando.set(c.id);
    this.error.set('');
    this.calidad
      .aprobar(c.id)
      .pipe(finalize(() => this.aprobando.set(null)))
      .subscribe({
        next: () => this.porAprobar.update((lista) => lista.filter((x) => x.id !== c.id)),
        error: (e) => this.error.set(e.error?.message ?? 'No se pudo aprobar el análisis.'),
      });
  }

  /** Automática (cumplió el estándar), el usuario que aprobó en la app, o pendiente. */
  textoAprobacion(c: ControlCalidad): string {
    if (!c.aprobadoEn) return 'Pendiente';
    return c.aprobadoPor === 'SISTEMA' ? 'Automática' : (c.aprobadoPor ?? 'Aprobado');
  }

  /** De dónde viene la leche analizada. */
  textoOrigen(c: ControlCalidad): string {
    if (c.recojoId == null) return 'Esperando recojo';
    return c.lugarAnalisis === 'PLANTA' ? 'Entrega directa' : 'Recojo en ruta';
  }

  /** Campo / Planta / Sin dato: los controles anteriores a V47 de recojos de ruta no lo registran. */
  textoLugar(c: ControlCalidad): string {
    return c.lugarAnalisis === 'CAMPO' ? 'Campo' : c.lugarAnalisis === 'PLANTA' ? 'Planta' : 'Sin dato';
  }
  incidenciasDelDia() {
    return this.incidencias().filter((i) => fechaLima(new Date(i.fecha)) === this.fecha);
  }

  /** Cuántos avisos del día hay en cada estado, para el resumen de arriba. */
  contarEstado(estado: EstadoIncidencia): number {
    return this.incidenciasDelDia().filter((i) => i.estado === estado).length;
  }

  /** Cuántos controles del proveedor salieron buenos, observados y rechazados. */
  readonly resumenControles = computed(() => {
    const c = this.controles();
    return {
      buenas: c.filter((x) => x.resultado === 'ACEPTADA').length,
      observadas: c.filter((x) => x.resultado === 'OBSERVADA').length,
      rechazadas: c.filter((x) => x.resultado === 'RECHAZADA').length,
    };
  });

  textoResultado(r: ResultadoCalidad): string {
    return { ACEPTADA: 'Buena', OBSERVADA: 'Observada', RECHAZADA: 'Rechazada' }[r] ?? r;
  }

  textoEstado(e: EstadoIncidencia): string {
    return { ABIERTA: 'Pendiente', EN_SEGUIMIENTO: 'En seguimiento', CERRADA: 'Resuelta' }[e] ?? e;
  }
  buscar() {
    if (!this.busqueda.trim() || this.buscando()) return;
    this.buscando.set(true);
    this.error.set('');
    this.proveedores.set([]);
    this.busquedaHecha.set(false);
    this.productoresService
      .listar({ q: this.busqueda.trim(), size: 20 })
      .pipe(finalize(() => this.buscando.set(false)))
      .subscribe({
        next: (p) => {
          this.proveedores.set(p.content);
          this.totalResultados.set(p.totalElements);
          this.busquedaHecha.set(true);
        },
        error: () => this.error.set('No se pudo buscar al proveedor.'),
      });
  }
  verHistorial(id: number, nombre: string) {
    this.vista.set('historial');
    const consulta = ++this.consulta;
    this.nombre.set(nombre);
    this.controles.set([]);
    this.cargandoHistorial.set(true);
    this.error.set('');
    this.calidad.historial(id).subscribe({
      next: (c) => {
        if (consulta === this.consulta) {
          this.controles.set(c);
          this.cargandoHistorial.set(false);
        }
      },
      error: () => {
        if (consulta === this.consulta) {
          this.error.set('No se pudo cargar el historial.');
          this.cargandoHistorial.set(false);
        }
      },
    });
  }

  /** Muestra la foto del voucher del control y lo que leyó el OCR (RNF-20). */
  verVoucher(controlId: number) {
    this.cerrarVoucher();
    this.abriendoVoucher.set(controlId);
    this.calidad.evidenciasDeControl(controlId).subscribe({
      next: (evidencias) => {
        const conFoto = evidencias.filter((e) => e.tieneArchivo).pop();
        if (!conFoto) {
          this.abriendoVoucher.set(null);
          this.voucher.set({ mensaje: 'Este control no tiene foto de voucher.' });
          return;
        }
        this.calidad
          .archivoEvidencia(conFoto.id)
          .pipe(finalize(() => this.abriendoVoucher.set(null)))
          .subscribe({
            next: (blob) =>
              this.voucher.set({ url: URL.createObjectURL(blob), evidencia: conFoto }),
            error: () => this.voucher.set({ mensaje: 'No se pudo descargar la foto del voucher.' }),
          });
      },
      error: () => {
        this.abriendoVoucher.set(null);
        this.voucher.set({ mensaje: 'No se pudieron consultar las evidencias del control.' });
      },
    });
  }

  /** Libera la foto de la memoria del navegador. */
  cerrarVoucher() {
    const url = this.voucher()?.url;
    if (url) URL.revokeObjectURL(url);
    this.voucher.set(null);
  }

  ngOnDestroy() {
    this.cerrarVoucher();
  }
}
