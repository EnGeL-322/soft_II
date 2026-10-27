import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { UbicacionGps } from '../models/models';
import { AuthService } from './auth.service';
import { abrirCanal, EstadoSocket } from './canal-tiempo-real';

export type { EstadoSocket } from './canal-tiempo-real';

/** Canal WebSocket para mostrar en administración cada GPS recibido por el backend. */
@Injectable({ providedIn: 'root' })
export class UbicacionesTiempoRealService {
  private readonly estadoSignal = new BehaviorSubject<EstadoSocket>('desconectado');
  readonly estado$ = this.estadoSignal.asObservable();

  constructor(private readonly auth: AuthService) {}

  conectar(hasta$: Subject<void>): Observable<UbicacionGps> {
    return abrirCanal<UbicacionGps>('/api/ws/ubicaciones', this.auth.token, hasta$, (e) => this.estadoSignal.next(e));
  }
}
