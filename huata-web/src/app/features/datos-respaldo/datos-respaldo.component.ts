import { Component, OnInit, signal } from '@angular/core';
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
  imports: [],
  templateUrl: './datos-respaldo.component.html',
})
export class DatosRespaldoComponent implements OnInit {
  readonly hojas = signal<string[]>([]);

  constructor(private readonly exportacionesService: ExportacionesService) {}

  ngOnInit(): void {
    this.exportacionesService.hojas().subscribe((h) => this.hojas.set(h));
  }

  descripcion(hoja: string): string {
    return DESCRIPCIONES[hoja] ?? '';
  }

  descargar(hoja: string): void {
    this.exportacionesService.descargar(hoja).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `Huata_${hoja}.xlsx`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }
}
