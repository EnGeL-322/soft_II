import { DatePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
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

  nuevo: { clave: string; valor: string; descripcion?: string } = { clave: '', valor: '' };

  constructor(
    private readonly calidadService: CalidadService,
    private readonly parametrosService: ParametrosService,
    private readonly auditoriaService: AuditoriaService,
    private readonly route: ActivatedRoute,
  ) {}

  ngOnInit(): void {
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
    this.calidadService.guardarParametro(p.id, p).subscribe();
  }

  guardarParametroNegocio(): void {
    this.parametrosService.guardar(this.nuevo).subscribe(() => {
      this.nuevo = { clave: '', valor: '' };
      this.parametrosService.listar().subscribe((p) => this.parametrosNegocio.set(p));
    });
  }
}
