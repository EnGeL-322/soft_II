import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { DialogoHostComponent } from './shared/notificaciones/dialogo-host.component';
import { NotificacionesHostComponent } from './shared/notificaciones/notificaciones-host.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NotificacionesHostComponent, DialogoHostComponent],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {}
