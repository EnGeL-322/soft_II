import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { EventoAcopio } from '../models/models';
import { AuthService } from './auth.service';
import { abrirCanal, EstadoSocket } from './canal-tiempo-real';

/**
 * Canal de acopio: cada recojo que el acopiador registra en el camino desde la app móvil, y cada
 * total que anota planta, llega aquí apenas el backend lo confirma (RF-53, RF-54).
 *
 * Cada mensaje trae la fila de la ruta **ya recalculada por el servidor**, no el recojo suelto: si
 * esta pantalla sumara los litros por su cuenta, un mensaje perdido la dejaría con un total distinto
 * del de la base sin que nadie lo note.
 */
@Injectable({ providedIn: 'root' })
export class AcopioTiempoRealService {
  private readonly estadoSignal = new BehaviorSubject<EstadoSocket>('desconectado');
  readonly estado$ = this.estadoSignal.asObservable();

  constructor(private readonly auth: AuthService) {}

  conectar(hasta$: Subject<void>): Observable<EventoAcopio> {
    return abrirCanal<EventoAcopio>('/api/ws/acopio', this.auth.token, hasta$, (e) => this.estadoSignal.next(e));
  }
}
