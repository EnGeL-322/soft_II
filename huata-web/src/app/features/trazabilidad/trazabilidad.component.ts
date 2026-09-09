import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Component, OnInit, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TrazabilidadItem, UbicacionGps } from '../../core/models/models';
import { TrazabilidadService } from '../../core/services/trazabilidad.service';
import { UbicacionesService } from '../../core/services/ubicaciones.service';
import { BadgeEstadoComponent } from '../../shared/ui/badge-estado.component';
import { MapaComponent, PuntoMapa } from '../../shared/ui/mapa.component';

/**
 * Seguimiento de producción: buscador libre por código de proveedor, zona, fecha o id de
 * recepción, más un mapa GPS real (Leaflet + OpenStreetMap) con la última posición de cada
 * acopiador que haya compartido su ubicación desde "Recepción / Acopio".
 */
@Component({
  selector: 'app-trazabilidad',
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe, BadgeEstadoComponent, MapaComponent, RouterLink],
  templateUrl: './trazabilidad.component.html',
})
export class TrazabilidadComponent implements OnInit {
  readonly resultados = signal<TrazabilidadItem[]>([]);
  readonly ubicaciones = signal<UbicacionGps[]>([]);
  readonly puntosMapa = computed<PuntoMapa[]>(() =>
    this.ubicaciones().map((u) => ({
      id: u.acopiadorId,
      lat: u.latitud,
      lng: u.longitud,
      etiqueta: u.acopiadorNombre,
      detalle: 'Actualizado: ' + new Date(u.fecha).toLocaleString(),
    })),
  );

  q = '';
  tab = 'resumen'; desde = ''; hasta = ''; acopiador = ''; pagina = 1; size = 5; mapa = false;
  readonly error = signal('');
  seleccionado: TrazabilidadItem | null = null;
  private readonly formatoDia = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' });
  get filtrados() {
    const q = this.q.toLowerCase().trim();
    return this.resultados().filter(r => {
      const dia = this.formatoDia.format(new Date(r.fechaHora));
      return (!this.desde || dia >= this.desde) && (!this.hasta || dia <= this.hasta)
        && (!this.acopiador || r.acopiadorNombre === this.acopiador)
        && [r.recojoId, r.clientUuid, r.productorCodigo, r.productorNombre, r.zona, dia].join(' ').toLowerCase().includes(q);
    });
  }
  get visibles() { return this.filtrados.slice((this.pagina - 1) * this.size, this.pagina * this.size); }
  get paginas() { return Math.max(1, Math.ceil(this.filtrados.length / this.size)); }
  get acopiadores() { return [...new Set(this.resultados().map(r => r.acopiadorNombre).filter(Boolean))]; }
  filtrar() { this.pagina = 1; }
  limpiar() { this.q = this.desde = this.hasta = this.acopiador = ''; this.filtrar(); }
  readonly rankings = computed(() => {
    const agrupar = (porZona: boolean) => {
      const grupos = new Map<string, { nombre: string; litros: number; total: number; aptas: number; porcentaje: number }>();
      this.resultados().filter(r => r.estado !== 'CANCELLED' && r.resultadoCalidad).forEach(r => {
        const nombre = porZona ? r.zona ?? 'Sin zona' : r.productorCodigo + ' · ' + r.productorNombre;
        const g = grupos.get(nombre) ?? { nombre, litros: 0, total: 0, aptas: 0, porcentaje: 0 };
        g.litros += r.litros; g.total++; if (r.resultadoCalidad === 'ACEPTADA') g.aptas++;
        g.porcentaje = g.aptas / g.total * 100; grupos.set(nombre, g);
      });
      return [...grupos.values()].filter(g => porZona || g.total >= 2).sort((a, b) => b.porcentaje - a.porcentaje || b.litros - a.litros);
    };
    const zonas = agrupar(true), proveedores = agrupar(false);
    return [
      { titulo: 'Zonas con mejor calidad', filas: zonas.slice(0, 5) },
      { titulo: 'Zonas a reforzar', filas: [...zonas].reverse().slice(0, 5) },
      { titulo: 'Proveedores modelo', filas: proveedores.slice(0, 5) },
      { titulo: 'Proveedores a reforzar', filas: [...proveedores].reverse().slice(0, 5) },
    ];
  });

  constructor(
    private readonly trazabilidadService: TrazabilidadService,
    private readonly ubicacionesService: UbicacionesService,
  ) {}

  ngOnInit(): void {
    this.buscar();
    this.ubicacionesService.ultimas().subscribe((u) => this.ubicaciones.set(u));
  }

  buscar(): void {
    this.trazabilidadService.buscar('').subscribe({ next: r => this.resultados.set(r), error: () => this.error.set('No se pudo cargar el seguimiento.') });
  }
}
