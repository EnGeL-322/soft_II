import { DatePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize, forkJoin, of } from 'rxjs';
import { LiquidacionesService } from '../../core/services/liquidaciones.service';
import { ActivatedRoute } from '@angular/router';
import { PageResponse, ParametroCalidad, ParametroNegocio, RegistroAuditoria, RolInfo } from '../../core/models/models';
import { AuditoriaService } from '../../core/services/auditoria.service';
import { CalidadService } from '../../core/services/calidad.service';
import { ParametrosService } from '../../core/services/parametros.service';

type Tab = 'calidad' | 'general' | 'roles' | 'auditoria';

/**
 * Configuración: todo lo que el proyecto marca como "no hardcodear" vive aquí, editable solo por
 * Administración (rangos de calidad, parámetros generales, roles y bitácora de auditoría — igual
 * que la vista "config" del prototipo).
 */
@Component({
  selector: 'app-parametros',
  standalone: true,
  imports: [FormsModule, DatePipe],
  templateUrl: './parametros.component.html',
})
export class ParametrosComponent implements OnInit {
  readonly tab = signal<Tab>('calidad');
  readonly parametrosCalidad = signal<ParametroCalidad[]>([]);
  readonly parametrosNegocio = signal<ParametroNegocio[]>([]);
  readonly roles = signal<RolInfo[]>([]);
  readonly auditoria = signal<RegistroAuditoria[]>([]);
  readonly mensaje = signal('');
  readonly guardando = signal(false);
  precioNormal: number | null = null;
  precioObservada: number | null = null;
  private tarifaInicial = '';
  guardarConfiguracion() {
    if (this.guardando()) return;
    const tarifa = JSON.stringify([this.precioNormal, this.precioObservada]);
    if (this.precioNormal == null || this.precioObservada == null || this.precioNormal < 0 || this.precioObservada < 0) {
      this.mensaje.set('Completa las tarifas con valores válidos.'); return;
    }
    this.guardando.set(true); this.mensaje.set('');
    const calidad = this.parametrosCalidad().map(p => this.calidadService.guardarParametro(p.id, p));
    const hoy = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(new Date());
    const precio = tarifa === this.tarifaInicial ? of(null) : this.liquidaciones.crearTarifa({ precioNormal: this.precioNormal, precioObservada: this.precioObservada, vigenteDesde: hoy });
    forkJoin([...calidad, precio]).pipe(finalize(() => this.guardando.set(false))).subscribe({
      next: () => { this.tarifaInicial = tarifa; this.mensaje.set('Configuración guardada.'); },
      error: e => this.mensaje.set((e.error?.message ?? 'No se pudieron guardar todos los cambios.') + ' Revisa los valores antes de reintentar.'),
    });
  }

  nuevo: { clave: string; valor: string; descripcion?: string } = { clave: '', valor: '' };

  constructor(
    private readonly calidadService: CalidadService,
    private readonly parametrosService: ParametrosService,
    private readonly auditoriaService: AuditoriaService,
    private readonly route: ActivatedRoute,
    private readonly liquidaciones: LiquidacionesService,
  ) {}

  ngOnInit(): void {
    this.liquidaciones.tarifas().subscribe(tarifas => {
      const hoy = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(new Date());
      const tarifa = tarifas.filter(t => t.vigenteDesde <= hoy && (!t.vigenteHasta || t.vigenteHasta >= hoy)).sort((a,b) => b.vigenteDesde.localeCompare(a.vigenteDesde) || b.id-a.id)[0];
      this.precioNormal = tarifa?.precioNormal ?? null; this.precioObservada = tarifa?.precioObservada ?? null;
      this.tarifaInicial = JSON.stringify([this.precioNormal, this.precioObservada]);
    });
    this.calidadService.parametros().subscribe((p) => this.parametrosCalidad.set(p));
    this.parametrosService.listar().subscribe((p) => this.parametrosNegocio.set(p));
    this.auditoriaService.roles().subscribe((r) => this.roles.set(r));
    this.auditoriaService.listar().subscribe((pg: PageResponse<RegistroAuditoria>) => this.auditoria.set(pg.content));

    const tabParam = this.route.snapshot.queryParamMap.get('tab') as Tab | null;
    if (tabParam) {
      this.tab.set(tabParam);
    }
  }

  guardarParametroCalidad(p: ParametroCalidad): void {
    this.calidadService.guardarParametro(p.id, p).subscribe({ next: () => this.mensaje.set('Parámetro guardado.'), error: e => this.mensaje.set(e.error?.message ?? 'No se pudo guardar el parámetro.') });
  }

  guardarParametroNegocio(): void {
    this.parametrosService.guardar(this.nuevo).subscribe(() => {
      this.nuevo = { clave: '', valor: '' };
      this.parametrosService.listar().subscribe((p) => this.parametrosNegocio.set(p));
    });
  }
}
