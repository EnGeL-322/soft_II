import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/cuenta_api.dart';
import '../../core/api/rutas_api.dart' show mensajeDeError;
import '../../core/auth/auth_controller.dart';
import '../../core/theme/huata_theme.dart';
import '../../shared/huata_scaffold.dart';

/// Abre una hoja de edición de la cuenta. Devuelve el mensaje de éxito, o null si se cerró sin guardar.
Future<String?> abrirHojaCuenta(BuildContext context, Widget hoja) => showModalBottomSheet<String>(
  context: context,
  isScrollControlled: true,
  useSafeArea: true,
  builder: (ctx) => Padding(
    padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(ctx).bottom),
    child: SingleChildScrollView(padding: const EdgeInsets.fromLTRB(20, 0, 20, 24), child: hoja),
  ),
);

/// Esqueleto común: título, explicación, campos, error y botón de guardar con su estado de carga.
class _HojaBase extends StatelessWidget {
  const _HojaBase({
    required this.titulo,
    required this.detalle,
    required this.campos,
    required this.error,
    required this.guardando,
    required this.alGuardar,
    this.textoBoton = 'Guardar cambios',
  });

  final String titulo;
  final String detalle;
  final List<Widget> campos;
  final String? error;
  final bool guardando;
  final VoidCallback alGuardar;
  final String textoBoton;

  @override
  Widget build(BuildContext context) => Column(
    mainAxisSize: MainAxisSize.min,
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      Text(titulo, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700)),
      const SizedBox(height: 4),
      Text(detalle, style: const TextStyle(color: HuataColors.muted, fontSize: 13.5, height: 1.4)),
      const SizedBox(height: 20),
      ...campos,
      if (error != null) ...[const SizedBox(height: 4), EstadoAviso(mensaje: error!, esError: true)],
      const SizedBox(height: 8),
      ElevatedButton(
        onPressed: guardando ? null : alGuardar,
        style: ElevatedButton.styleFrom(minimumSize: const Size.fromHeight(50)),
        child: guardando
            ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
            : Text(textoBoton),
      ),
    ],
  );
}

/// Sin señal no se puede: estas operaciones necesitan que el servidor confirme en el momento.
String _mensajeCuenta(Object e) {
  final m = mensajeDeError(e);
  return m == 'Sin conexión con el servidor.' ? 'Necesitas señal para cambiar los datos de tu cuenta.' : m;
}

class _CampoClave extends StatefulWidget {
  const _CampoClave({required this.controller, required this.etiqueta, this.validator, this.onChanged, this.accion});
  final TextEditingController controller;
  final String etiqueta;
  final FormFieldValidator<String>? validator;
  final ValueChanged<String>? onChanged;
  final TextInputAction? accion;

  @override
  State<_CampoClave> createState() => _CampoClaveState();
}

class _CampoClaveState extends State<_CampoClave> {
  bool _ver = false;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 14),
    child: TextFormField(
      controller: widget.controller,
      obscureText: !_ver,
      textInputAction: widget.accion ?? TextInputAction.next,
      onChanged: widget.onChanged,
      validator: widget.validator,
      decoration: InputDecoration(
        labelText: widget.etiqueta,
        prefixIcon: const Icon(Icons.lock_outline),
        suffixIcon: IconButton(
          tooltip: _ver ? 'Ocultar' : 'Mostrar',
          icon: Icon(_ver ? Icons.visibility_off_outlined : Icons.visibility_outlined),
          onPressed: () => setState(() => _ver = !_ver),
        ),
      ),
    ),
  );
}

// ---------------------------------------------------------------- Datos personales

class HojaDatosPersonales extends StatefulWidget {
  const HojaDatosPersonales({super.key, required this.cuenta});
  final MiCuenta cuenta;

  @override
  State<HojaDatosPersonales> createState() => _HojaDatosPersonalesState();
}

class _HojaDatosPersonalesState extends State<HojaDatosPersonales> {
  final _form = GlobalKey<FormState>();
  late final _nombre = TextEditingController(text: widget.cuenta.nombreCompleto);
  late final _correo = TextEditingController(text: widget.cuenta.email ?? '');
  late final _telefono = TextEditingController(text: widget.cuenta.telefono ?? '');
  bool _guardando = false;
  String? _error;

  @override
  void dispose() {
    _nombre.dispose();
    _correo.dispose();
    _telefono.dispose();
    super.dispose();
  }

  Future<void> _guardar() async {
    if (!_form.currentState!.validate()) return;
    setState(() {
      _guardando = true;
      _error = null;
    });
    try {
      await context.read<CuentaApi>().actualizarDatos(
        nombreCompleto: _nombre.text.trim(),
        email: _correo.text.trim(),
        telefono: _telefono.text.trim(),
      );
      if (!mounted) return;
      // El nombre se ve en la barra superior: se relee la sesión.
      await context.read<AuthController>().refrescarPermisos();
      if (mounted) Navigator.of(context).pop('Tus datos se guardaron.');
    } catch (e) {
      if (mounted) setState(() => _error = _mensajeCuenta(e));
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final editaNombre = widget.cuenta.puedeEditarNombre;
    return Form(
      key: _form,
      child: _HojaBase(
        titulo: 'Datos personales',
        detalle: editaNombre
            ? 'Así te verán en la planta y en los registros.'
            : 'Tu nombre lo corrige la planta en tu ficha, porque identifica tus pagos.',
        error: _error,
        guardando: _guardando,
        alGuardar: _guardar,
        campos: [
          TextFormField(
            controller: _nombre,
            enabled: editaNombre,
            textCapitalization: TextCapitalization.words,
            decoration: InputDecoration(
              labelText: 'Nombre completo',
              prefixIcon: const Icon(Icons.person_outline),
              suffixIcon: editaNombre ? null : const Icon(Icons.lock_outline, size: 18),
            ),
            validator: (v) => (v == null || v.trim().length < 2) ? 'Escribe tu nombre' : null,
          ),
          const SizedBox(height: 14),
          TextFormField(
            controller: _correo,
            keyboardType: TextInputType.emailAddress,
            decoration: const InputDecoration(labelText: 'Correo', prefixIcon: Icon(Icons.mail_outline)),
            validator: (v) {
              final t = v?.trim() ?? '';
              if (t.isEmpty) return null;
              return RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$').hasMatch(t) ? null : 'Correo inválido';
            },
          ),
          const SizedBox(height: 14),
          TextFormField(
            controller: _telefono,
            keyboardType: TextInputType.phone,
            decoration: const InputDecoration(labelText: 'Teléfono', prefixIcon: Icon(Icons.phone_outlined)),
            validator: (v) {
              final t = v?.trim() ?? '';
              if (t.isEmpty) return null;
              return RegExp(r'^\+?(?:\d[ .()\-]?){6,15}$').hasMatch(t) ? null : 'Usa entre 6 y 15 dígitos';
            },
          ),
          const SizedBox(height: 14),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------- Contraseña

class HojaCambiarClave extends StatefulWidget {
  const HojaCambiarClave({super.key});

  @override
  State<HojaCambiarClave> createState() => _HojaCambiarClaveState();
}

class _HojaCambiarClaveState extends State<HojaCambiarClave> {
  final _form = GlobalKey<FormState>();
  final _actual = TextEditingController();
  final _nueva = TextEditingController();
  final _confirmar = TextEditingController();
  FuerzaClave _fuerza = FuerzaClave.vacia;
  bool _guardando = false;
  String? _error;

  @override
  void dispose() {
    _actual.dispose();
    _nueva.dispose();
    _confirmar.dispose();
    super.dispose();
  }

  Future<void> _guardar() async {
    if (!_form.currentState!.validate()) return;
    setState(() {
      _guardando = true;
      _error = null;
    });
    try {
      await context.read<CuentaApi>().cambiarClave(actual: _actual.text, nueva: _nueva.text);
      if (mounted) Navigator.of(context).pop('Tu contraseña se cambió.');
    } catch (e) {
      if (mounted) setState(() => _error = _mensajeCuenta(e));
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }

  @override
  Widget build(BuildContext context) => Form(
    key: _form,
    child: _HojaBase(
      titulo: 'Cambiar contraseña',
      detalle: 'Usa al menos ${CuentaApi.claveMinima} caracteres. Mejor si mezclas mayúsculas, números y símbolos.',
      error: _error,
      guardando: _guardando,
      alGuardar: _guardar,
      textoBoton: 'Cambiar contraseña',
      campos: [
        _CampoClave(
          controller: _actual,
          etiqueta: 'Contraseña actual',
          validator: (v) => (v == null || v.isEmpty) ? 'Escribe tu contraseña actual' : null,
        ),
        _CampoClave(
          controller: _nueva,
          etiqueta: 'Nueva contraseña',
          onChanged: (v) => setState(() => _fuerza = medirClave(v)),
          validator: (v) {
            if (v == null || v.length < CuentaApi.claveMinima) return 'Mínimo ${CuentaApi.claveMinima} caracteres';
            if (v == _actual.text) return 'Debe ser distinta de la actual';
            return null;
          },
        ),
        IndicadorFuerza(fuerza: _fuerza),
        const SizedBox(height: 14),
        _CampoClave(
          controller: _confirmar,
          etiqueta: 'Repite la nueva contraseña',
          accion: TextInputAction.done,
          validator: (v) => v != _nueva.text ? 'No coincide con la nueva contraseña' : null,
        ),
      ],
    ),
  );
}

/// Barra de 3 tramos bajo la nueva contraseña: orienta, no bloquea (la regla es el mínimo).
class IndicadorFuerza extends StatelessWidget {
  const IndicadorFuerza({super.key, required this.fuerza});
  final FuerzaClave fuerza;

  @override
  Widget build(BuildContext context) {
    final (nivel, texto, color) = switch (fuerza) {
      FuerzaClave.vacia => (0, '', HuataColors.line),
      FuerzaClave.debil => (1, 'Débil', HuataColors.red),
      FuerzaClave.media => (2, 'Aceptable', HuataColors.amber),
      FuerzaClave.fuerte => (3, 'Fuerte', HuataColors.green),
    };
    return Row(
      children: [
        for (var i = 1; i <= 3; i++) ...[
          Expanded(
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              height: 5,
              decoration: BoxDecoration(
                color: i <= nivel ? color : HuataColors.line,
                borderRadius: BorderRadius.circular(3),
              ),
            ),
          ),
          if (i < 3) const SizedBox(width: 6),
        ],
        const SizedBox(width: 10),
        SizedBox(
          width: 70,
          child: Text(texto, style: TextStyle(color: color, fontSize: 12.5, fontWeight: FontWeight.w600)),
        ),
      ],
    );
  }
}

// ---------------------------------------------------------------- Usuario

class HojaCambiarUsuario extends StatefulWidget {
  const HojaCambiarUsuario({super.key, required this.actual});
  final String actual;

  @override
  State<HojaCambiarUsuario> createState() => _HojaCambiarUsuarioState();
}

class _HojaCambiarUsuarioState extends State<HojaCambiarUsuario> {
  final _form = GlobalKey<FormState>();
  final _usuario = TextEditingController();
  final _clave = TextEditingController();
  bool _guardando = false;
  String? _error;

  @override
  void dispose() {
    _usuario.dispose();
    _clave.dispose();
    super.dispose();
  }

  Future<void> _guardar() async {
    if (!_form.currentState!.validate()) return;
    setState(() {
      _guardando = true;
      _error = null;
    });
    try {
      final sesion = await context.read<CuentaApi>().cambiarUsuario(clave: _clave.text, nuevoUsuario: _usuario.text.trim());
      if (!mounted) return;
      await context.read<AuthController>().aplicarSesionNueva(sesion);
      if (mounted) Navigator.of(context).pop('Tu usuario ahora es ${sesion['username']}. Úsalo para entrar.');
    } catch (e) {
      if (mounted) setState(() => _error = _mensajeCuenta(e));
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }

  @override
  Widget build(BuildContext context) => Form(
    key: _form,
    child: _HojaBase(
      titulo: 'Cambiar usuario',
      detalle: 'Es con lo que inicias sesión en la app y en la web. Tu usuario actual es ${widget.actual}.',
      error: _error,
      guardando: _guardando,
      alGuardar: _guardar,
      textoBoton: 'Cambiar usuario',
      campos: [
        TextFormField(
          controller: _usuario,
          autocorrect: false,
          textInputAction: TextInputAction.next,
          decoration: const InputDecoration(labelText: 'Nuevo usuario', prefixIcon: Icon(Icons.alternate_email)),
          validator: (v) {
            final t = v?.trim() ?? '';
            if (!RegExp(r'^[\p{L}0-9._@+\-]{3,100}$', unicode: true).hasMatch(t)) {
              return 'Entre 3 y 100 caracteres, sin espacios';
            }
            if (t.toLowerCase() == widget.actual.toLowerCase()) return 'Ese ya es tu usuario';
            return null;
          },
        ),
        const SizedBox(height: 14),
        _CampoClave(
          controller: _clave,
          etiqueta: 'Tu contraseña (para confirmar)',
          accion: TextInputAction.done,
          validator: (v) => (v == null || v.isEmpty) ? 'Confirma con tu contraseña' : null,
        ),
      ],
    ),
  );
}
