import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { mensajeDeError } from '../../core/notificaciones/mensaje-error';
import { DialogoService } from '../../core/notificaciones/dialogo.service';
import { NotificacionesService } from '../../core/notificaciones/notificaciones.service';
import { CLAVE_MINIMA, CuentaService, MiCuenta } from '../../core/services/cuenta.service';

const TELEFONO = /^\+?(?:\d[ .()\-]?){6,15}$/;
const CORREO = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const USUARIO = /^[\p{L}0-9._@+\-]{3,100}$/u;

/** Qué tan fuerte es la contraseña nueva: orienta, no bloquea (la regla es el mínimo). */
export function fuerzaClave(clave: string): 0 | 1 | 2 | 3 {
  if (!clave) return 0;
  if (clave.length < CLAVE_MINIMA) return 1;
  let puntos = 0;
  if (/[a-z]/.test(clave) && /[A-Z]/.test(clave)) puntos++;
  if (/\d/.test(clave)) puntos++;
  if (/[^A-Za-z0-9]/.test(clave)) puntos++;
  if (clave.length >= 12) puntos++;
  return puntos >= 3 ? 3 : puntos >= 1 ? 2 : 1;
}

/**
 * Mi cuenta (web): mismos datos y reglas que el módulo "Mi cuenta" de la app. Tres bloques: datos
 * personales, contraseña y usuario; cada uno se guarda por separado y se confirma antes de enviar.
 */
@Component({
  selector: 'app-mi-cuenta',
  standalone: true,
  imports: [FormsModule, DatePipe],
  templateUrl: './mi-cuenta.component.html',
  styleUrl: './mi-cuenta.component.css',
})
export class MiCuentaComponent implements OnInit {
  private readonly cuentas = inject(CuentaService);
  private readonly dialogo = inject(DialogoService);
  private readonly avisos = inject(NotificacionesService);

  readonly claveMinima = CLAVE_MINIMA;
  readonly cuenta = signal<MiCuenta | null>(null);
  readonly error = signal('');
  readonly guardando = signal<'datos' | 'clave' | 'usuario' | null>(null);

  // Datos personales
  nombre = '';
  correo = '';
  telefono = '';
  readonly errorDatos = signal('');

  // Contraseña
  claveActual = '';
  claveNueva = '';
  claveRepetida = '';
  verClaves = false;
  readonly errorClave = signal('');

  // Usuario
  usuarioNuevo = '';
  claveUsuario = '';
  readonly errorUsuario = signal('');

  readonly iniciales = computed(() => {
    const partes = (this.cuenta()?.nombreCompleto ?? '').trim().split(/\s+/).filter(Boolean);
    return ((partes[0]?.[0] ?? '') + (partes[1]?.[0] ?? '')).toUpperCase() || '?';
  });

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cuentas.obtener().subscribe({
      next: (c) => this.fijar(c),
      error: (e) => this.error.set(mensajeDeError(e, 'No se pudo cargar tu cuenta.')),
    });
  }

  private fijar(c: MiCuenta): void {
    this.cuenta.set(c);
    this.nombre = c.nombreCompleto;
    this.correo = c.email ?? '';
    this.telefono = c.telefono ?? '';
  }

  fuerza(): 0 | 1 | 2 | 3 {
    return fuerzaClave(this.claveNueva);
  }

  readonly etiquetasFuerza = ['', 'Débil', 'Aceptable', 'Fuerte'];

  datosCambiados(): boolean {
    const c = this.cuenta();
    return (
      !!c &&
      (this.nombre.trim() !== c.nombreCompleto ||
        this.correo.trim() !== (c.email ?? '') ||
        this.telefono.trim() !== (c.telefono ?? ''))
    );
  }

  async guardarDatos(): Promise<void> {
    const nombre = this.nombre.trim();
    const correo = this.correo.trim();
    const telefono = this.telefono.trim();
    if (nombre.length < 2) return this.errorDatos.set('Escribe tu nombre.');
    if (correo && !CORREO.test(correo)) return this.errorDatos.set('Correo inválido.');
    if (telefono && !TELEFONO.test(telefono)) return this.errorDatos.set('Teléfono inválido: usa entre 6 y 15 dígitos.');
    this.errorDatos.set('');
    const ok = await this.dialogo.confirmar({
      titulo: '¿Guardar tus datos?',
      mensaje: 'Se actualizarán tu nombre, correo y teléfono.',
      confirmar: 'Guardar',
    });
    if (!ok) return;
    this.guardando.set('datos');
    this.cuentas
      .actualizarDatos({ nombreCompleto: nombre, email: correo, telefono })
      .pipe(finalize(() => this.guardando.set(null)))
      .subscribe({
        next: (c) => {
          this.fijar(c);
          this.avisos.exito('Tus datos se guardaron.');
        },
        error: (e) => this.errorDatos.set(mensajeDeError(e)),
      });
  }

  async cambiarClave(): Promise<void> {
    if (!this.claveActual) return this.errorClave.set('Escribe tu contraseña actual.');
    if (this.claveNueva.length < CLAVE_MINIMA) return this.errorClave.set(`Mínimo ${CLAVE_MINIMA} caracteres.`);
    if (this.claveNueva === this.claveActual) return this.errorClave.set('Debe ser distinta de la actual.');
    if (this.claveNueva !== this.claveRepetida) return this.errorClave.set('La confirmación no coincide.');
    this.errorClave.set('');
    const ok = await this.dialogo.confirmar({
      titulo: '¿Cambiar tu contraseña?',
      mensaje: 'Desde ahora entrarás con la nueva, en la web y en la app.',
      confirmar: 'Cambiar contraseña',
    });
    if (!ok) return;
    this.guardando.set('clave');
    this.cuentas
      .cambiarClave(this.claveActual, this.claveNueva)
      .pipe(finalize(() => this.guardando.set(null)))
      .subscribe({
        next: () => {
          this.claveActual = this.claveNueva = this.claveRepetida = '';
          this.avisos.exito('Tu contraseña se cambió.');
        },
        error: (e) => this.errorClave.set(mensajeDeError(e)),
      });
  }

  async cambiarUsuario(): Promise<void> {
    const nuevo = this.usuarioNuevo.trim();
    if (!USUARIO.test(nuevo)) return this.errorUsuario.set('Entre 3 y 100 caracteres, sin espacios.');
    if (nuevo.toLowerCase() === this.cuenta()?.username.toLowerCase()) return this.errorUsuario.set('Ese ya es tu usuario.');
    if (!this.claveUsuario) return this.errorUsuario.set('Confirma con tu contraseña.');
    this.errorUsuario.set('');
    const ok = await this.dialogo.confirmar({
      titulo: `¿Cambiar tu usuario a «${nuevo}»?`,
      mensaje: 'Desde ahora entrarás con este usuario, en la web y en la app. Tu sesión sigue abierta.',
      confirmar: 'Cambiar usuario',
      tipo: 'aviso',
    });
    if (!ok) return;
    this.guardando.set('usuario');
    this.cuentas
      .cambiarUsuario(this.claveUsuario, nuevo)
      .pipe(finalize(() => this.guardando.set(null)))
      .subscribe({
        next: (s) => {
          this.usuarioNuevo = this.claveUsuario = '';
          this.avisos.exito(`Tu usuario ahora es ${s.username}.`);
          this.cargar();
        },
        error: (e) => this.errorUsuario.set(mensajeDeError(e)),
      });
  }
}
