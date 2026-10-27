import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_BASE_URL } from '../../../core/config/api-config';
import { AuthService } from '../../../core/services/auth.service';
import { CatalogoTerritorio } from '../compartido/catalogos';
import { FichaAcopiadorComponent } from './ficha-acopiador.component';

describe('Ficha de acopiador', () => {
  beforeEach(() => {
    HTMLDialogElement.prototype.showModal = function () {
      this.setAttribute('open', '');
    };
    HTMLDialogElement.prototype.close = function () {
      this.removeAttribute('open');
      this.dispatchEvent(new Event('close'));
    };
  });

  it('DNI y teléfono son opcionales: vacíos no se envían y el alta se acepta', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        CatalogoTerritorio,
        { provide: AuthService, useValue: { puede: () => false } },
      ],
    });
    const fixture = TestBed.createComponent(FichaAcopiadorComponent);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.match((r) => r.url === `${API_BASE_URL}/rutas`).forEach((r) =>
      r.flush({ content: [{ id: 1, nombre: 'Ruta Norte', zona: 'COLLANA', activo: true }], page: 0, size: 200, totalElements: 1, totalPages: 1 }),
    );
    const c = fixture.componentInstance;
    c.nuevo();
    fixture.detectChanges();
    await fixture.whenStable();
    c.form.nombres = 'Rosa';
    c.form.apellidos = 'Quispe';
    c.form.rutaId = 1;
    c.form.username = 'rosa@huata.pe';
    c.form.password = 'Huata2026*';
    fixture.detectChanges();
    await fixture.whenStable();

    c.guardar();

    const req = http.expectOne((r) => r.method === 'POST' && r.url === `${API_BASE_URL}/acopiadores`);
    expect(req.request.body.documentoIdentidad).toBeUndefined();
    expect(req.request.body.telefono).toBeUndefined();
    expect(c.errores.general()).toBe('');
  });
});
