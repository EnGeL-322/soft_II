import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_BASE_URL } from '../../../core/config/api-config';
import { ImportarProveedoresComponent, ResultadoImportacion } from './importar-proveedores.component';

const URL_IMPORTAR = `${API_BASE_URL}/importaciones/proveedores`;

function vistaPrevia(errores = 0): ResultadoImportacion {
  return {
    aplicado: false, crear: 1, actualizar: 1, errores,
    filas: [
      { fila: 2, codigo: '12345678', nombre: 'Pedro Huanca', accion: 'CREAR', mensaje: null },
      { fila: 3, codigo: '87654321', nombre: 'Juana Ccama', accion: errores ? 'ERROR' : 'ACTUALIZAR', mensaje: errores ? 'DNI inválido' : null },
    ],
  };
}

describe('ImportarProveedoresComponent', () => {
  function crear() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ImportarProveedoresComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(ImportarProveedoresComponent);
    fixture.detectChanges();
    const c = fixture.componentInstance;
    const http = TestBed.inject(HttpTestingController);
    const archivo = new File(['x'], 'proveedores.xlsx');
    c.elegir({ target: { files: [archivo] } } as unknown as Event);
    return { fixture, c, http };
  }

  it('al elegir el archivo pide solo la vista previa (no guarda) y muestra cada fila', () => {
    const { fixture, c, http } = crear();
    const req = http.expectOne((r) => r.url === URL_IMPORTAR);
    expect(req.request.params.get('confirmar')).toBe('false');
    req.flush(vistaPrevia());
    fixture.detectChanges();

    expect(c.resultado()?.aplicado).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Confirmar importación');
    http.verify();
  });

  it('con errores no ofrece confirmar', () => {
    const { fixture, http } = crear();
    http.expectOne((r) => r.url === URL_IMPORTAR).flush(vistaPrevia(1));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Confirmar importación');
    expect(fixture.nativeElement.textContent).toContain('DNI inválido');
  });

  it('confirmar aplica y avisa para recargar la lista', () => {
    const { c, http } = crear();
    const importados: ResultadoImportacion[] = [];
    c.importado.subscribe((r) => importados.push(r));
    http.expectOne((r) => r.url === URL_IMPORTAR).flush(vistaPrevia());

    c.confirmar();
    const req = http.expectOne((r) => r.url === URL_IMPORTAR);
    expect(req.request.params.get('confirmar')).toBe('true');
    req.flush({ ...vistaPrevia(), aplicado: true });

    expect(importados).toHaveLength(1);
    http.verify();
  });
});
