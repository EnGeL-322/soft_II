import { Component, OnInit, computed, inject, input, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { PERMISO } from '../../../core/config/navegacion';
import { EstadoProductor, Productor } from '../../../core/models/models';
import { DialogoService } from '../../../core/notificaciones/dialogo.service';
import { NotificacionesService } from '../../../core/notificaciones/notificaciones.service';
import { AuthService } from '../../../core/services/auth.service';
import { ExportacionesService } from '../../../core/services/exportaciones.service';
import { ProductoresService } from '../../../core/services/productores.service';
import { BadgeEstadoComponent } from '../../../shared/ui/badge-estado.component';
import { IconoComponent } from '../../../shared/ui/icono.component';
import { ListaPaginada } from '../../../shared/ui/lista-paginada';
import { PaginadorComponent } from '../../../shared/ui/paginador.component';
import { CatalogoTerritorio } from '../compartido/catalogos';
import { faltaParaAprobar } from './aprobacion';
import { ESTADOS_PROVEEDOR, destinosDe, iconoDe, verboDe } from './estado-proveedor';
import { FichaProveedorComponent } from './ficha-proveedor.component';
import { ImportarProveedoresComponent } from './importar-proveedores.component';
import { ReporteProveedoresComponent } from './reporte-proveedores.component';
import { recargarEnVivo } from '../../../core/services/cambios-tiempo-real.service';

/**
 * Padrón de proveedores con filtros por estado, zona, ruta y acopiador. Aquí se aprueban, se
 * inactivan y se marca la Excelencia; toda transición pide motivo y queda auditada (7.3, 7.4).
 */
@Component({
  selector: 'app-lista-proveedores',
  standalone: true,
  imports: [
    FormsModule,
    BadgeEstadoComponent,
    IconoComponent,
    PaginadorComponent,
    FichaProveedorComponent,
    ImportarProveedoresComponent,
    ReporteProveedoresComponent,
  ],
  templateUrl: './lista-proveedores.component.html',
  styleUrl: '../compartido/personas.css',
})
export class ListaProveedoresComponent implements OnInit {
  /** Tiempo real: si alguien guarda algo de proveedores, usuarios, esta pantalla se actualiza sola. */
  private readonly _enVivo = recargarEnVivo(['proveedores', 'usuarios'], () => this.buscar());
  private readonly productores = inject(ProductoresService);
  private readonly exportaciones = inject(ExportacionesService);
  private readonly auth = inject(AuthService);
  private readonly dialogo = inject(DialogoService);
  private readonly notificaciones = inject(NotificacionesService);
  private readonly router = inject(Router);
  protected readonly territorio = inject(CatalogoTerritorio);
  private readonly ficha = viewChild.required(FichaProveedorComponent);

  readonly abrirNuevo = input(false);
  /** Texto que llega del buscador general de Usuarios (o de ?q= en el enlace). */
  readonly busquedaInicial = input('');

  q = '';
  estado?: EstadoProductor;
  zona = '';
  rutaId?: number;
  acopiadorId?: number;

  readonly lista = new ListaPaginada<Productor>(
    (page, size) =>
      this.productores.listar({
        q: this.q,
        estado: this.estado,
        zona: this.zona,
        rutaId: this.rutaId,
        acopiadorId: this.acopiadorId,
        page,
        size,
      }),
    'No se pudo cargar el padrón de proveedores.',
  );

  readonly estados = ESTADOS_PROVEEDOR;
  readonly destinosDe = destinosDe;
  readonly verboDe = verboDe;
  readonly iconoDe = iconoDe;

  /** Lo primero que hay que atender: altas esperando aprobación. */
  readonly pendientes = signal(0);
  readonly vista = signal<'padron' | 'reportes'>('padron');
  readonly importando = signal(false);

  readonly puedeEditar = computed(() => this.auth.puede(PERMISO.proveedoresWrite));
  /** Importar exige los dos permisos, igual que el backend: importar datos y editar proveedores. */
  readonly puedeImportar = computed(() => this.auth.puede(PERMISO.respaldoWrite) && this.puedeEditar());

  get rutasDeLaZona() {
    return this.territorio.rutasDeZona(this.zona);
  }

  get acopiadoresFiltro() {
    const rutas = new Set(this.rutasDeLaZona.map((r) => r.id));
    return this.territorio.acopiadores().filter((a) => (this.rutaId ? a.rutaId === this.rutaId : !this.zona || rutas.has(a.rutaId!)));
  }

  get hayFiltros(): boolean {
    return !!(this.q || this.estado || this.zona || this.rutaId || this.acopiadorId);
  }

  ngOnInit(): void {
    this.q = this.busquedaInicial();
    this.territorio.asegurar();
    this.buscar();
    if (this.abrirNuevo()) queueMicrotask(() => this.nuevo());
  }

  buscar(): void {
    this.lista.buscar();
    this.contarPendientes();
  }

  private contarPendientes(): void {
    this.productores
      .listar({ estado: 'PENDIENTE_APROBACION', page: 0, size: 1 })
      .subscribe({ next: (pg) => this.pendientes.set(pg.totalElements), error: () => this.pendientes.set(0) });
  }

  verPendientes(): void {
    this.limpiarFiltros(false);
    this.estado = 'PENDIENTE_APROBACION';
    this.lista.buscar();
  }

  cambiarZona(): void {
    if (this.rutaId && !this.rutasDeLaZona.some((r) => r.id === this.rutaId)) this.rutaId = undefined;
    this.cambiarRuta();
  }

  cambiarRuta(): void {
    if (this.acopiadorId && !this.acopiadoresFiltro.some((a) => a.id === this.acopiadorId)) this.acopiadorId = undefined;
    this.lista.buscar();
  }

  limpiarFiltros(buscar = true): void {
    this.q = '';
    this.estado = undefined;
    this.zona = '';
    this.rutaId = undefined;
    this.acopiadorId = undefined;
    if (buscar) this.lista.buscar();
  }

  nuevo(): void {
    if (!this.puedeEditar()) return;
    this.vista.set('padron');
    this.ficha().nuevo();
  }

  editar(p: Productor): void {
    if (this.puedeEditar()) this.ficha().editar(p);
  }

  alGuardar({ proveedor: p, nuevo }: { proveedor: Productor; nuevo: boolean }): void {
    this.notificaciones.exito(
      nuevo
        ? `Se registró a ${p.nombres} ${p.apellidos}. Ya está activo y puede entregar leche.`
        : `Se guardaron los datos de ${p.nombres} ${p.apellidos}.`,
    );
    // Igual que con el acopiador: se va a Zonas, con su zona abierta y su fila resaltada.
    if (p.rutaId && this.auth.puede(PERMISO.zonasRead)) {
      this.router.navigate(['/zonas'], { queryParams: { ruta: p.rutaId, proveedor: p.id } });
      return;
    }
    this.lista.recargar();
    this.contarPendientes();
  }

  /**
   * Pide el motivo antes de enviar: sin motivo el backend devuelve 422, y el motivo es lo único que
   * hace útil la bitácora meses después ("por qué se inactivó a este proveedor").
   */
  async cambiarEstado(p: Productor, destino: EstadoProductor): Promise<void> {
    // Aprobar un alta incompleta fallaría en el servidor: mejor abrir su ficha y decir qué completar.
    if (p.estado === 'PENDIENTE_APROBACION' && destino === 'ACTIVO') {
      const falta = faltaParaAprobar(p);
      if (falta.length) {
        this.ficha().editar(
          p,
          `Para aprobar a este proveedor falta: ${falta.join(', ')}. Complétalo, guarda y vuelve a pulsar "Aprobar".`,
        );
        return;
      }
    }
    const motivo = (
      (await this.dialogo.pedirTexto({
        titulo: `${verboDe(destino)} a ${p.codigo}`,
        etiqueta: 'Motivo',
        obligatorio: true,
        confirmar: verboDe(destino),
        tipo: destino === 'ACTIVO' ? 'confirmacion' : 'aviso',
      })) ?? ''
    ).trim();
    if (!motivo) {
      this.notificaciones.aviso('No se cambió el estado: hace falta escribir un motivo.');
      return;
    }
    this.productores.cambiarEstado(p.id, destino, motivo).subscribe({
      next: (r) => {
        this.notificaciones.exito(
          destino === 'ACTIVO'
            ? `${r.nombres} ${r.apellidos} ya está activo: desde ahora puede entregar leche.`
            : `Estado de ${r.nombres} ${r.apellidos} cambiado a ${verboDe(destino).toLowerCase()}.`,
        );
        this.lista.recargar();
        this.contarPendientes();
      },
      error: (e) => this.notificaciones.error(e.error?.message ?? 'No se pudo cambiar el estado.'),
    });
  }

  /** Conceder o retirar la Excelencia (7.4). Cambia lo que se le paga, así que también pide motivo. */
  async alternarExcelencia(p: Productor): Promise<void> {
    const motivo = (
      (await this.dialogo.pedirTexto({
        titulo: p.excelencia ? `Retirar Excelencia a ${p.codigo}` : `Conceder Excelencia a ${p.codigo}`,
        mensaje: `Vas a ${p.excelencia ? 'retirar' : 'conceder'} la marca de Excelencia a ${p.codigo}. Cambia lo que se le paga por litro.`,
        etiqueta: 'Motivo',
        obligatorio: true,
        confirmar: p.excelencia ? 'Retirar' : 'Conceder',
      })) ?? ''
    ).trim();
    if (!motivo) return;
    this.productores.cambiarExcelencia(p.id, !p.excelencia, motivo).subscribe({
      next: () => this.lista.recargar(),
      error: (e) => this.notificaciones.error(e.error?.message ?? 'No se pudo cambiar la marca de Excelencia.'),
    });
  }

  alImportar(r: { crear: number; actualizar: number }): void {
    this.notificaciones.exito(`Importación aplicada: ${r.crear} altas y ${r.actualizar} actualizaciones.`);
    this.buscar();
  }

  exportar(): void {
    this.exportaciones.descargar('Proveedores').subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'Huata_Proveedores.xlsx';
      a.click();
      URL.revokeObjectURL(url);
    });
  }
}
