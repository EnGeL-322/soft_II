import { Component, inject, output, signal, viewChild } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { finalize } from 'rxjs';
import { REGLAS } from '../../../core/config/reglas-datos';
import { Acopiador } from '../../../core/models/models';
import { AcopiadorRequest, AcopiadoresService } from '../../../core/services/acopiadores.service';
import { BuscadorComponent } from '../../../shared/ui/buscador.component';
import { CatalogoTerritorio } from '../compartido/catalogos';
import { generarClave } from '../compartido/clave';
import { ErroresForm } from '../compartido/errores-form';
import { VentanaFichaComponent } from '../compartido/ventana-ficha.component';

const VACIO: AcopiadorRequest = {
  nombres: '',
  apellidos: '',
  documentoIdentidad: '',
  telefono: '',
  rutaId: undefined,
  username: '',
  password: '',
};

/**
 * Alta y edición de un acopiador. Crea a la vez su cuenta con el rol Acopiador: una sola, con la
 * que entra a la web y a la app móvil. La ruta es obligatoria porque de ella sale su zona y los
 * proveedores que visita.
 */
/** Nombre de cada campo en los mensajes de "Falta: …". */
const ETIQUETAS: Record<string, string> = {
  documentoIdentidad: 'DNI',
  nombres: 'nombres',
  apellidos: 'apellidos',
  telefono: 'teléfono',
  rutaId: 'zona y ruta',
  username: 'usuario (email)',
  password: 'contraseña',
};

@Component({
  selector: 'app-ficha-acopiador',
  standalone: true,
  imports: [FormsModule, BuscadorComponent, VentanaFichaComponent],
  templateUrl: './ficha-acopiador.component.html',
  styleUrls: ['../accesos-dialog.css', '../compartido/personas.css'],
})
export class FichaAcopiadorComponent {
  private readonly acopiadores = inject(AcopiadoresService);
  protected readonly territorio = inject(CatalogoTerritorio);
  protected readonly reglas = REGLAS;
  private readonly ventana = viewChild.required(VentanaFichaComponent);
  private readonly formulario = viewChild.required<NgForm>('f');

  readonly guardado = output<Acopiador>();

  readonly editando = signal<Acopiador | null>(null);
  readonly guardando = signal(false);
  readonly verClave = signal(false);
  readonly errores = new ErroresForm();
  form: AcopiadorRequest = { ...VACIO };

  nuevo(): void {
    this.abrir(null, { ...VACIO });
  }

  editar(a: Acopiador): void {
    this.abrir(a, {
      nombres: a.nombres,
      apellidos: a.apellidos,
      documentoIdentidad: a.documentoIdentidad ?? '',
      telefono: a.telefono ?? '',
      rutaId: a.rutaId,
      username: a.username ?? '',
      password: '',
    });
  }

  private abrir(a: Acopiador | null, form: AcopiadorRequest): void {
    this.territorio.asegurar();
    this.editando.set(a);
    this.form = form;
    this.verClave.set(false);
    this.errores.limpiar();
    this.formulario().resetForm(form);
    this.ventana().abrir();
  }

  /** Sin cuenta todavía (acopiador heredado): la contraseña es obligatoria para crearla. */
  get exigeClave(): boolean {
    return !this.editando()?.usuarioId;
  }

  cerrar(): void {
    this.ventana().cerrar();
  }

  generarClave(): void {
    this.form.password = generarClave();
    this.verClave.set(true);
  }

  guardar(): void {
    if (this.guardando()) return;
    if (!this.errores.revisar(this.formulario(), ETIQUETAS)) return;
    const actual = this.editando();
    this.guardando.set(true);
    this.errores.limpiar();
    // Lo opcional que se deja vacío viaja como "no hay", no como texto vacío.
    const datos: AcopiadorRequest = {
      ...this.form,
      documentoIdentidad: this.form.documentoIdentidad?.trim() || undefined,
      telefono: this.form.telefono?.trim() || undefined,
    };
    (actual ? this.acopiadores.actualizar(actual.id, datos) : this.acopiadores.crear(datos))
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: (a) => {
          this.guardando.set(false);
          this.ventana().cerrar();
          this.territorio.recargarAcopiadores();
          this.guardado.emit(a);
        },
        error: (e) => this.errores.desdeServidor(e, 'No se pudo guardar el acopiador.'),
      });
  }
}
