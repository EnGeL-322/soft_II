import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { ExportacionesService } from '../../core/services/exportaciones.service';

const DESCRIPCIONES: Record<string, string> = {
  Proveedores: 'Listado completo de productores.',
  Recepciones: 'Historial de recojos de leche.',
  HistorialCalidad: 'Controles de calidad registrados.',
  Pagos: 'Liquidaciones calculadas.',
  ResumenZonas: 'Comparativo de producción por zona.',
  Quesos: 'Producción y venta de quesos.',
  Configuracion: 'Parámetros de negocio y tarifas vigentes.',
  Auditoria: 'Bitácora de acciones del sistema.',
};

/** Datos y respaldo: exporta cada hoja a un .xlsx real (Apache POI en el backend), igual que el botón "Exportar Excel" del prototipo. */
@Component({
  selector: 'app-datos-respaldo',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './datos-respaldo.component.html',
})
export class DatosRespaldoComponent implements OnInit {
  readonly hojas = signal<string[]>([]);
  readonly error = signal('');
  readonly descargando = signal(false);
  hojaSeleccionada = 'Proveedores';
  ultimaCopia = '';
  exportarJson() {
    if (this.descargando()) return;
    this.descargando.set(true); this.error.set('');
    this.exportacionesService.datosJson().pipe(finalize(() => this.descargando.set(false))).subscribe({ next: datos => {
      this.guardarArchivo(new Blob([JSON.stringify({ generado: new Date().toISOString(), datos }, null, 2)], { type: 'application/json' }), 'Huata_Datos.json');
      this.ultimaCopia = new Date().toLocaleString('es-PE');
    }, error: () => this.error.set('No se pudo exportar la copia de datos.') });
  }
  private guardarArchivo(blob: Blob, nombre: string) {
    const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = nombre; a.click(); URL.revokeObjectURL(url);
  }

  constructor(private readonly exportacionesService: ExportacionesService) {}

  ngOnInit(): void {
    this.exportacionesService.hojas().subscribe((h) => this.hojas.set(h));
  }

  descripcion(hoja: string): string {
    return DESCRIPCIONES[hoja] ?? '';
  }

  descargar(hoja: string): void {
    if (this.descargando()) return;
    this.descargando.set(true); this.error.set('');
    this.exportacionesService.descargar(hoja).pipe(finalize(() => this.descargando.set(false))).subscribe({
      next: blob => this.guardarArchivo(blob, `Huata_${hoja === 'libro' ? 'Completo' : hoja}.xlsx`),
      error: () => this.error.set('No se pudo generar el archivo Excel.'),
    });
  }
}
