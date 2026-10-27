import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { RutasService } from '../../core/services/rutas.service';
import { ZonasComponent } from './zonas.component';

describe('ZonasComponent', () => {
  let fixture: ComponentFixture<ZonasComponent>;
  let component: ZonasComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ZonasComponent],
      providers: [
        provideRouter([]),
        {
          provide: RutasService,
          useValue: {
            zonasAsignadas: () =>
              of([
                {
                  zona: 'Huata Norte',
                  rutaId: 1,
                  rutaNombre: 'Ruta Norte',
                  proveedores: [
                    { id: 10, codigo: 'P-10', nombres: 'Ana', apellidos: 'Quispe', ordenEnRuta: 2 },
                    { id: 11, codigo: 'P-11', nombres: 'Luis', apellidos: 'Mamani', ordenEnRuta: 1 },
                  ],
                  acopiadores: [{ id: 20, nombres: 'Juan', apellidos: 'Condori' }],
                },
                {
                  zona: 'Huata Sur',
                  rutaId: 2,
                  rutaNombre: 'Ruta Sur',
                  proveedores: [],
                  acopiadores: [],
                },
              ]),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ZonasComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('agrupa proveedores y acopiadores por la zona de su ruta', () => {
    const norte = component.zonas.find((zona) => zona.nombre === 'Huata Norte');
    expect(norte?.proveedores.map((p) => p.codigo)).toEqual(['P-11', 'P-10']);
    expect(norte?.acopiadores.map((a) => a.id)).toEqual([20]);
  });

  it('muestra el recorrido de la zona con sus proveedores', () => {
    expect(fixture.nativeElement.textContent).toContain('Huata Norte');
    expect(fixture.nativeElement.textContent).toContain('Juan Condori');
    expect(fixture.nativeElement.textContent).toContain('P-10');
    expect(fixture.nativeElement.textContent).toContain('P-11');
  });

  it('al llegar desde la ficha del acopiador abre su zona y resalta su ruta y su nombre', async () => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ZonasComponent],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({ zona: 'Huata Sur', ruta: '1', acopiador: '20' }) } },
        },
        {
          provide: RutasService,
          useValue: {
            zonasAsignadas: () =>
              of([
                { zona: 'Huata Sur', rutaId: 2, rutaNombre: 'Ruta Sur', proveedores: [], acopiadores: [] },
                {
                  zona: 'Huata Norte',
                  rutaId: 1,
                  rutaNombre: 'Ruta Norte',
                  proveedores: [],
                  acopiadores: [{ id: 20, nombres: 'Juan', apellidos: 'Condori' }],
                },
              ]),
          },
        },
      ],
    }).compileComponents();
    const f = TestBed.createComponent(ZonasComponent);
    f.detectChanges();
    // La zona sale de la ruta, no del texto del enlace: abre Huata Norte aunque pidan Huata Sur.
    expect(f.componentInstance.zonaAbierta).toBe('Huata Norte');
    expect(f.nativeElement.textContent).toContain('Juan Condori quedó asignado a Huata Norte · Ruta Norte.');
    expect(f.nativeElement.querySelector('#ruta-1.resaltada')).not.toBeNull();
    expect(f.nativeElement.querySelector('.collector-list b.recien')?.textContent).toContain('Juan Condori');
  });

  it('al llegar desde la ficha del proveedor abre su zona y resalta su fila', async () => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ZonasComponent],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ ruta: '1', proveedor: '10' }) } } },
        {
          provide: RutasService,
          useValue: {
            zonasAsignadas: () =>
              of([
                { zona: 'Huata Sur', rutaId: 2, rutaNombre: 'Ruta Sur', proveedores: [], acopiadores: [] },
                {
                  zona: 'Huata Norte',
                  rutaId: 1,
                  rutaNombre: 'Ruta Norte',
                  proveedores: [{ id: 10, codigo: 'PRV-00001', nombres: 'Ana', apellidos: 'Quispe' }],
                  acopiadores: [],
                },
              ]),
          },
        },
      ],
    }).compileComponents();
    const f = TestBed.createComponent(ZonasComponent);
    f.detectChanges();
    expect(f.componentInstance.zonaAbierta).toBe('Huata Norte');
    expect(f.nativeElement.textContent).toContain('Ana Quispe quedó en Huata Norte · Ruta Norte.');
    expect(f.nativeElement.querySelector('tr.recien')?.textContent).toContain('PRV-00001');
  });
});
