import { TestBed } from '@angular/core/testing';
import { PreferenciasMapaService } from './preferencias-mapa';

describe('PreferenciasMapaService', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('por defecto muestra el recorrido como trazado', () => {
    expect(TestBed.inject(PreferenciasMapaService).modoRecorrido()).toBe('TRAZADO');
  });

  it('recuerda la elección de la persona en este navegador', () => {
    TestBed.inject(PreferenciasMapaService).cambiarModo('PUNTOS');
    TestBed.resetTestingModule();
    expect(TestBed.inject(PreferenciasMapaService).modoRecorrido()).toBe('PUNTOS');
  });

  it('ignora un valor guardado que no reconoce', () => {
    localStorage.setItem('huata.modoRecorrido', 'OTRO');
    expect(TestBed.inject(PreferenciasMapaService).modoRecorrido()).toBe('TRAZADO');
  });
});
