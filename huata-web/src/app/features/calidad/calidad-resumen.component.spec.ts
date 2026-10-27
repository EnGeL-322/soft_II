import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { CalidadService, EvidenciaCalidad } from '../../core/services/calidad.service';
import { ProductoresService } from '../../core/services/productores.service';
import { CalidadResumenComponent } from './calidad-resumen.component';

/** Visor de la foto del voucher en Calidad (RNF-20). */
describe('CalidadResumenComponent · voucher', () => {
  const evidencia = (parcial: Partial<EvidenciaCalidad>): EvidenciaCalidad => ({
    id: 1,
    controlCalidadId: 7,
    tipo: 'VOUCHER',
    urlArchivo: '/api/v1/calidad/evidencias/1/archivo',
    tieneArchivo: true,
    ...parcial,
  });

  function crear(calidad: Partial<CalidadService>, permisos: string[] = []) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [CalidadResumenComponent],
      providers: [
        provideRouter([]),
        { provide: CalidadService, useValue: { incidencias: () => of([]), ...calidad } },
        { provide: ProductoresService, useValue: {} },
        { provide: AuthService, useValue: { puede: (p: string) => permisos.includes(p) } },
      ],
    });
    return TestBed.createComponent(CalidadResumenComponent).componentInstance;
  }

  beforeEach(() => {
    URL.createObjectURL = vi.fn(() => 'blob:foto');
    URL.revokeObjectURL = vi.fn();
  });

  it('descarga la foto con la sesión y muestra lo que leyó el OCR', () => {
    const c = crear({
      evidenciasDeControl: () => of([evidencia({ textoOcr: 'Densidad.....23.9', verificadoPor: 'calidad.demo@huata.pe' })]),
      archivoEvidencia: () => of(new Blob(['x'], { type: 'image/jpeg' })),
    });

    c.verVoucher(7);

    expect(c.voucher()?.url).toBe('blob:foto');
    expect(c.voucher()?.evidencia?.textoOcr).toBe('Densidad.....23.9');
    expect(c.abriendoVoucher()).toBeNull();
  });

  it('un control sin foto lo dice en vez de mostrar una imagen rota', () => {
    const c = crear({ evidenciasDeControl: () => of([evidencia({ tieneArchivo: false })]) });

    c.verVoucher(7);

    expect(c.voucher()?.url).toBeUndefined();
    expect(c.voucher()?.mensaje).toContain('no tiene foto');
  });

  it('si la descarga falla avisa y no se queda "abriendo"', () => {
    const c = crear({
      evidenciasDeControl: () => of([evidencia({})]),
      archivoEvidencia: () => throwError(() => new Error('403')),
    });

    c.verVoucher(7);

    expect(c.voucher()?.mensaje).toContain('No se pudo descargar');
    expect(c.abriendoVoucher()).toBeNull();
  });

  it('al cerrar libera la foto de la memoria del navegador', () => {
    const c = crear({
      evidenciasDeControl: () => of([evidencia({})]),
      archivoEvidencia: () => of(new Blob(['x'])),
    });
    c.verVoucher(7);

    c.cerrarVoucher();

    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:foto');
    expect(c.voucher()).toBeNull();
  });

  it('con calidad.aprobar se aprueba desde la web y el análisis sale de la lista', () => {
    const aprobar = vi.fn(() => of(undefined));
    const c = crear({ aprobar }, ['calidad.aprobar']);
    const control = { id: 9 } as never;
    c.porAprobar.set([control]);

    c.aprobar(control);

    expect(aprobar).toHaveBeenCalledWith(9);
    expect(c.porAprobar()).toEqual([]);
  });

  it('sin calidad.aprobar no se ofrece ni se llama', () => {
    const aprobar = vi.fn(() => of(undefined));
    const c = crear({ aprobar });
    expect(c.puedeAprobar()).toBe(false);
    c.aprobar({ id: 9 } as never);
    expect(aprobar).not.toHaveBeenCalled();
  });
});
