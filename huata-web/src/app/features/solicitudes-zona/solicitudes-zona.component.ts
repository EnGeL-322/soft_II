import { Component, OnInit, signal, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PERMISO } from '../../core/config/navegacion';
import { EstadoSolicitudZona, SolicitudCambioZona } from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { ProductoresService } from '../../core/services/productores.service';
import { PaginacionLocal } from '../../shared/ui/paginacion-local';
import { PaginadorComponent } from '../../shared/ui/paginador.component';
import { DialogoService } from '../../core/notificaciones/dialogo.service';
import { NotificacionesService } from '../../core/notificaciones/notificaciones.service';
import { recargarEnVivo } from '../../core/services/cambios-tiempo-real.service';

/** Bandeja de solicitudes de cambio de zona del proveedor (2.5): recibir, revisar, aprobar/rechazar. */
@Component({
  selector: 'app-solicitudes-zona',
  standalone: true,
  imports: [PaginadorComponent, FormsModule],
  templateUrl: './solicitudes-zona.component.html',
})
export class SolicitudesZonaComponent implements OnInit {
  /** Tiempo real: si alguien guarda algo de proveedores, esta pantalla se actualiza sola. */
  private readonly _enVivo = recargarEnVivo(['proveedores'], () => this.cargar());
  protected readonly notificaciones = inject(NotificacionesService);
  private readonly dialogo = inject(DialogoService);
  readonly solicitudes = signal<SolicitudCambioZona[]>([]);
  readonly pag = new PaginacionLocal();
  readonly error = signal('');
  filtro: EstadoSolicitudZona | '' = 'PENDIENTE';

  constructor(
    private readonly productores: ProductoresService,
    protected readonly auth: AuthService,
  ) {}

  ngOnInit(): void {
    this.cargar();
  }

  puedeDecidir(): boolean {
    return this.auth.puede(PERMISO.proveedoresWrite);
  }

  cargar(): void {
    this.productores.solicitudesZona(this.filtro || undefined).subscribe({
      next: (s) => this.solicitudes.set(s),
      error: (e) => this.error.set(e.error?.message ?? 'No se pudieron cargar las solicitudes.'),
    });
  }

  async decidir(id: number, aprobar: boolean): Promise<void> {
    const texto = await this.dialogo.pedirTexto({
      titulo: aprobar ? '¿Aprobar el cambio de zona?' : '¿Rechazar el cambio de zona?',
      mensaje: aprobar ? 'Se actualizan la zona, la ruta y el acopiador del proveedor.' : undefined,
      etiqueta: aprobar ? 'Observación' : 'Motivo del rechazo',
      obligatorio: !aprobar,
      confirmar: aprobar ? 'Aprobar' : 'Rechazar',
      tipo: aprobar ? 'confirmacion' : 'peligro',
    });
    if (texto === null) return;
    const observacion = texto || undefined;
    this.productores.decidirSolicitudZona(id, aprobar, observacion).subscribe({
      next: () => {
        this.notificaciones.exito(aprobar ? 'Solicitud aprobada: zona, ruta y acopiador quedaron sincronizados.' : 'Solicitud rechazada.');
        this.cargar();
      },
      error: (e) => this.error.set(e.error?.message ?? 'No se pudo decidir la solicitud.'),
    });
  }
}
