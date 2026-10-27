import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/actualizacion/actualizacion_service.dart';
import '../../core/api/cuenta_api.dart';
import '../../core/auth/auth_controller.dart';
import '../../core/sync/sync_service.dart';
import '../../core/theme/huata_theme.dart';
import '../../shared/huata_scaffold.dart';
import 'hojas_cuenta.dart';

/// Mi cuenta: perfil, datos personales, seguridad (contraseña y usuario), estado de la app y salir.
///
/// Cada edición va en su hoja inferior y la confirma el servidor en el momento; sin señal se avisa
/// en vez de encolarla. Cambiar el usuario se bloquea mientras haya registros por enviar: la cola del
/// teléfono está a nombre del usuario actual y quedaría sin dueño.
class CuentaScreen extends StatefulWidget {
  const CuentaScreen({super.key});

  @override
  State<CuentaScreen> createState() => _CuentaScreenState();
}

class _CuentaScreenState extends State<CuentaScreen> {
  MiCuenta? _cuenta;
  bool _cargando = true;
  bool _sinSenal = false;

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  Future<void> _cargar() async {
    setState(() => _cargando = true);
    try {
      final cuenta = await context.read<CuentaApi>().obtener();
      if (mounted) {
        setState(() {
          _cuenta = cuenta;
          _sinSenal = false;
        });
      }
    } catch (_) {
      // Sin señal: se muestra lo que ya se sabe de la sesión, sin opciones de edición.
      if (mounted) setState(() => _sinSenal = true);
    } finally {
      if (mounted) setState(() => _cargando = false);
    }
  }

  Future<void> _editar(Widget hoja) async {
    final mensaje = await abrirHojaCuenta(context, hoja);
    if (mensaje == null || !mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Row(children: [
        const Icon(Icons.check_circle, color: Colors.white, size: 20),
        const SizedBox(width: 10),
        Expanded(child: Text(mensaje)),
      ]),
    ));
    _cargar();
  }

  @override
  Widget build(BuildContext context) {
    final sesion = context.watch<AuthController>().sesion;
    final act = context.watch<ActualizacionService>();
    final sync = context.watch<SyncService>();
    if (sesion == null) return const SizedBox.shrink();
    final porEnviar = sync.pendientesRecojo + sync.pendientesUbicacion + sync.pendientesTrazado + sync.pendientesMensaje;
    final cuenta = _cuenta;
    final nombre = cuenta?.nombreCompleto ?? sesion.nombreCompleto;
    final usuario = cuenta?.username ?? sesion.username;
    final editable = cuenta != null && !_sinSenal;

    return HuataScaffold(
      titulo: 'Mi cuenta',
      body: RefreshIndicator(
        onRefresh: _cargar,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(16),
          children: [
            _Perfil(nombre: nombre, usuario: usuario, rol: etiquetaRol(sesion), ultimoAcceso: cuenta?.ultimoAcceso),
            if (_sinSenal) ...[
              const SizedBox(height: 12),
              const EstadoAviso(mensaje: 'Sin señal: puedes ver tu cuenta, pero para editarla necesitas conexión.'),
            ],
            if (_cargando && cuenta == null) const Padding(padding: EdgeInsets.only(top: 12), child: LinearProgressIndicator()),
            const SizedBox(height: 16),
            const EncabezadoSeccion('Datos personales'),
            _Grupo(children: [
              _Opcion(
                icono: Icons.badge_outlined,
                titulo: 'Nombre',
                valor: nombre,
                bloqueado: cuenta != null && !cuenta.puedeEditarNombre,
              ),
              _Opcion(icono: Icons.mail_outline, titulo: 'Correo', valor: _o(cuenta?.email)),
              _Opcion(icono: Icons.phone_outlined, titulo: 'Teléfono', valor: _o(cuenta?.telefono)),
              _Accion(
                icono: Icons.edit_outlined,
                titulo: 'Editar datos personales',
                habilitado: editable,
                onTap: () => _editar(HojaDatosPersonales(cuenta: cuenta!)),
              ),
            ]),
            const SizedBox(height: 16),
            const EncabezadoSeccion('Seguridad'),
            _Grupo(children: [
              _Accion(
                icono: Icons.password_outlined,
                titulo: 'Cambiar contraseña',
                detalle: 'Mínimo ${CuentaApi.claveMinima} caracteres',
                habilitado: editable,
                onTap: () => _editar(const HojaCambiarClave()),
              ),
              _Accion(
                icono: Icons.alternate_email,
                titulo: 'Cambiar usuario',
                detalle: porEnviar > 0
                    ? 'Primero deben enviarse tus $porEnviar ${porEnviar == 1 ? 'registro pendiente' : 'registros pendientes'}'
                    : 'Actual: $usuario',
                habilitado: editable && porEnviar == 0,
                onTap: () => _editar(HojaCambiarUsuario(actual: usuario)),
              ),
            ]),
            const SizedBox(height: 16),
            const EncabezadoSeccion('Aplicación'),
            _Grupo(children: [
              _Opcion(
                icono: porEnviar == 0 ? Icons.cloud_done_outlined : Icons.cloud_upload_outlined,
                titulo: 'Envío de datos',
                valor: porEnviar == 0
                    ? 'Todo enviado'
                    : '$porEnviar por enviar${sync.sincronizando ? ' · enviando…' : ''}',
                colorValor: porEnviar == 0 ? HuataColors.green : HuataColors.amber,
              ),
              _Opcion(icono: Icons.info_outline, titulo: 'Versión', valor: act.versionInstalada ?? '—'),
              if (act.estado == EstadoActualizacion.disponible || act.estado == EstadoActualizacion.obligatoria)
                _Accion(
                  icono: Icons.system_update_outlined,
                  titulo: 'Descargar versión ${act.publicada?.versionActual ?? ''}',
                  detalle: act.publicada?.notas ?? 'Hay una versión nueva',
                  onTap: act.descargar,
                )
              else
                _Opcion(
                  icono: Icons.verified_outlined,
                  titulo: 'Actualizaciones',
                  valor: act.estado == EstadoActualizacion.alDia ? 'Al día' : 'Se revisa con conexión',
                  colorValor: act.estado == EstadoActualizacion.alDia ? HuataColors.green : HuataColors.muted,
                ),
            ]),
            const SizedBox(height: 20),
            OutlinedButton.icon(
              style: OutlinedButton.styleFrom(
                foregroundColor: HuataColors.red,
                side: const BorderSide(color: HuataColors.redSoft),
                minimumSize: const Size.fromHeight(50),
              ),
              onPressed: () => _confirmarSalida(context, porEnviar),
              icon: const Icon(Icons.logout),
              label: const Text('Cerrar sesión'),
            ),
          ],
        ),
      ),
    );
  }

  static String _o(String? v) => (v == null || v.isEmpty) ? 'Sin registrar' : v;

  /// Con registros por enviar se advierte: cerrar sesión los deja en el teléfono, a la espera de que
  /// vuelva a entrar la misma persona.
  Future<void> _confirmarSalida(BuildContext context, int porEnviar) async {
    final auth = context.read<AuthController>();
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('¿Cerrar sesión?'),
        content: Text(porEnviar > 0
            ? 'Tienes $porEnviar ${porEnviar == 1 ? 'registro' : 'registros'} sin enviar. Se enviarán cuando vuelvas '
                'a entrar con tu usuario.'
            : 'Tendrás que ingresar tu usuario y contraseña para volver a entrar.'),
        actions: [
          TextButton(onPressed: () => Navigator.of(ctx).pop(false), child: const Text('Cancelar')),
          TextButton(
            style: TextButton.styleFrom(foregroundColor: HuataColors.red),
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('Cerrar sesión'),
          ),
        ],
      ),
    );
    if (ok == true) auth.cerrarSesion();
  }
}

class _Perfil extends StatelessWidget {
  const _Perfil({required this.nombre, required this.usuario, required this.rol, this.ultimoAcceso});
  final String nombre;
  final String usuario;
  final String rol;
  final DateTime? ultimoAcceso;

  @override
  Widget build(BuildContext context) {
    final acceso = ultimoAcceso?.toLocal();
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: HuataMarca.degradado,
        borderRadius: BorderRadius.circular(20),
        boxShadow: const [BoxShadow(color: Color(0x331D4F91), blurRadius: 18, offset: Offset(0, 8))],
      ),
      child: Row(
        children: [
          CircleAvatar(
            radius: 32,
            backgroundColor: const Color(0x33FFFFFF),
            child: Text(iniciales(nombre),
                style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 22)),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(nombre,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: Colors.white, fontSize: 19, fontWeight: FontWeight.w700)),
                const SizedBox(height: 2),
                Text(usuario,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: Color(0xCCFFFFFF), fontSize: 13.5)),
                const SizedBox(height: 10),
                Wrap(spacing: 8, runSpacing: 6, children: [
                  _Pastilla(texto: rol),
                  if (acceso != null)
                    _Pastilla(
                      texto: 'Último acceso ${acceso.day.toString().padLeft(2, '0')}/'
                          '${acceso.month.toString().padLeft(2, '0')} '
                          '${acceso.hour.toString().padLeft(2, '0')}:${acceso.minute.toString().padLeft(2, '0')}',
                    ),
                ]),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _Pastilla extends StatelessWidget {
  const _Pastilla({required this.texto});
  final String texto;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
    decoration: BoxDecoration(color: const Color(0x26FFFFFF), borderRadius: BorderRadius.circular(999)),
    child: Text(texto, style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w600)),
  );
}

/// Tarjeta con filas separadas por líneas finas, como los ajustes de un teléfono.
class _Grupo extends StatelessWidget {
  const _Grupo({required this.children});
  final List<Widget> children;

  @override
  Widget build(BuildContext context) => Container(
    decoration: BoxDecoration(
      color: HuataColors.panel,
      borderRadius: BorderRadius.circular(16),
      border: Border.all(color: const Color(0xFFE8EDF3)),
      boxShadow: HuataMarca.sombra,
    ),
    clipBehavior: Clip.antiAlias,
    child: Column(
      children: [
        for (var i = 0; i < children.length; i++) ...[
          if (i > 0) const Divider(indent: 56),
          children[i],
        ],
      ],
    ),
  );
}

class _IconoFila extends StatelessWidget {
  const _IconoFila(this.icono);
  final IconData icono;

  @override
  Widget build(BuildContext context) => Container(
    width: 34,
    height: 34,
    decoration: BoxDecoration(color: HuataColors.greenSoft, borderRadius: BorderRadius.circular(10)),
    child: Icon(icono, size: 18, color: HuataColors.green),
  );
}

/// Dato de solo lectura.
class _Opcion extends StatelessWidget {
  const _Opcion({required this.icono, required this.titulo, required this.valor, this.bloqueado = false, this.colorValor});
  final IconData icono;
  final String titulo;
  final String valor;
  final bool bloqueado;
  final Color? colorValor;

  @override
  Widget build(BuildContext context) => ListTile(
    leading: _IconoFila(icono),
    title: Text(titulo, style: const TextStyle(fontSize: 12.5, color: HuataColors.muted, fontWeight: FontWeight.w500)),
    subtitle: Text(valor,
        style: TextStyle(fontSize: 15, color: colorValor ?? HuataColors.text, fontWeight: FontWeight.w600)),
    trailing: bloqueado
        ? const Tooltip(
            message: 'Lo corrige la planta en tu ficha',
            child: Icon(Icons.lock_outline, size: 18, color: HuataColors.muted),
          )
        : null,
  );
}

/// Fila que abre una edición.
class _Accion extends StatelessWidget {
  const _Accion({required this.icono, required this.titulo, required this.onTap, this.detalle, this.habilitado = true});
  final IconData icono;
  final String titulo;
  final String? detalle;
  final VoidCallback onTap;
  final bool habilitado;

  @override
  Widget build(BuildContext context) => ListTile(
    enabled: habilitado,
    onTap: habilitado ? onTap : null,
    leading: Opacity(opacity: habilitado ? 1 : 0.5, child: _IconoFila(icono)),
    title: Text(titulo),
    subtitle: detalle == null ? null : Text(detalle!),
    trailing: const Icon(Icons.chevron_right, color: HuataColors.muted),
  );
}
