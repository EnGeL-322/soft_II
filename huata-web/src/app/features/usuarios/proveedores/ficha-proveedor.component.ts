import { Component, computed, inject, output, signal, viewChild } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { finalize } from 'rxjs';
import { REGLAS } from '../../../core/config/reglas-datos';
import { Productor } from '../../../core/models/models';
import { ProductorRequest, ProductoresService } from '../../../core/services/productores.service';
import { BuscadorComponent } from '../../../shared/ui/buscador.component';
import { CatalogoTerritorio } from '../compartido/catalogos';
import { generarClave } from '../compartido/clave';
import { ErroresForm } from '../compartido/errores-form';
import { VentanaFichaComponent } from '../compartido/ventana-ficha.component';

const VACIO: ProductorRequest = {
  nombres: '',
  apellidos: '',
  documentoIdentidad: '',
  telefono: '',
  notas: '',
  rutaId: undefined,
  username: '',
  password: '',
};

/**
 * Alta y edición de un proveedor. El alta nace ACTIVO (decisión del cliente, 2026-10-26) con su cuenta (web y app);
 * el DNI es opcional: si lo tiene es su código, si no el backend genera uno (PRV-00001...). De la ruta salen su zona y su acopiador; el orden de visita se define en
 * Rutas y mapa, así que la ficha no los pide (el backend conserva lo que ya tenga).
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
  selector: 'app-ficha-proveedor',
  standalone: true,
  imports: [FormsModule, BuscadorComponent, VentanaFichaComponent],
  templateUrl: './ficha-proveedor.component.html',
  styleUrls: ['../accesos-dialog.css', '../compartido/personas.css'],
})
export class FichaProveedorComponent {
  private readonly productores = inject(ProductoresService);
  protected readonly territorio = inject(CatalogoTerritorio);
  protected readonly reglas = REGLAS;
  private readonly ventana = viewChild.required(VentanaFichaComponent);
  private readonly formulario = viewChild.required<NgForm>('f');

  readonly guardado = output<{ proveedor: Productor; nuevo: boolean }>();

  readonly editando = signal<Productor | null>(null);
  readonly guardando = signal(false);
  readonly verClave = signal(false);
  /** Al abrir la ficha para completar lo que falta antes de aprobar. */
  readonly aviso = signal('');
  readonly errores = new ErroresForm();
  /** Espejo de form.rutaId para mostrar quién recoge en esa ruta. */
  readonly rutaElegida = signal<number | undefined>(undefined);
  form: ProductorRequest = { ...VACIO };

  readonly acopiadoresDeLaRuta = computed(() =>
    this.territorio
      .acopiadoresDeRuta(this.rutaElegida())
      .map((a) => `${a.nombres} ${a.apellidos}`)
      .join(', '),
  );

  nuevo(): void {
    this.abrir(null, { ...VACIO });
  }

  editar(p: Productor, aviso = ''): void {
    this.abrir(
      p,
      {
        nombres: p.nombres,
        apellidos: p.apellidos,
        // Un proveedor heredado puede no tener DNI. El campo abre vacío y no deja guardar hasta
        // completarlo: el padrón se va limpiando al editar, sin reescribir nada por detrás.
        documentoIdentidad: p.documentoIdentidad ?? '',
        telefono: p.telefono ?? '',
        rutaId: p.rutaId,
        notas: p.notas ?? '',
        username: p.username ?? '',
        password: '',
      },
      aviso,
    );
  }

  private abrir(p: Productor | null, form: ProductorRequest, aviso = ''): void {
    this.territorio.asegurar();
    this.editando.set(p);
    this.form = form;
    this.rutaElegida.set(form.rutaId);
    this.aviso.set(aviso);
    this.verClave.set(false);
    this.errores.limpiar();
    this.formulario().resetForm(form);
    this.ventana().abrir();
  }

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
    const datos: ProductorRequest = {
      ...this.form,
      documentoIdentidad: this.form.documentoIdentidad?.trim() || '',
      telefono: this.form.telefono?.trim() || undefined,
    };
    (actual ? this.productores.actualizar(actual.id, datos) : this.productores.crear(datos))
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: (proveedor) => {
          this.guardando.set(false);
          this.ventana().cerrar();
          this.guardado.emit({ proveedor, nuevo: !actual });
        },
        error: (e) => this.errores.desdeServidor(e, 'No se pudo guardar el proveedor.'),
      });
  }
}
