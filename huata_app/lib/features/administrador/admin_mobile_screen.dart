import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/api_client.dart';
import '../../core/theme/huata_theme.dart';
import '../../shared/huata_scaffold.dart';
import '../../core/tiempo_real/cambios_en_vivo.dart';

class AdminMobileScreen extends StatefulWidget {
  const AdminMobileScreen({super.key});

  @override
  State<AdminMobileScreen> createState() => _AdminMobileScreenState();
}

class _AdminMobileScreenState extends State<AdminMobileScreen> with EnVivo<AdminMobileScreen> {
  bool _cargando = true;
  String? _error;
  List<String> _roles = const [];
  List<Map<String, dynamic>> _usuarios = const [];

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'usuarios', 'proveedores', 'acopiadores'}, () => _cargar(silencioso: true));
    _cargar();
  }

  Future<void> _cargar({bool silencioso = false}) async {
    if (!silencioso) {
      setState(() {
        _cargando = true;
        _error = null;
      });
    }
    try {
      final dio = context.read<ApiClient>().dio;
      final respuestas = await Future.wait([
        dio.get('/usuarios', queryParameters: {'page': 0, 'size': 100}),
        dio.get('/usuarios/roles'),
      ]);
      final pagina = respuestas[0].data as Map<String, dynamic>;
      final usuarios = (pagina['content'] as List? ?? const [])
          .map((item) => Map<String, dynamic>.from(item as Map))
          .toList()
        ..sort((a, b) {
          final aAdmin = (a['roles'] as List? ?? const []).contains('ADMINISTRADOR');
          final bAdmin = (b['roles'] as List? ?? const []).contains('ADMINISTRADOR');
          if (aAdmin != bAdmin) return aAdmin ? -1 : 1;
          return '${a['nombreCompleto'] ?? ''}'.compareTo('${b['nombreCompleto'] ?? ''}');
        });
      if (!mounted) return;
      setState(() {
        _usuarios = usuarios;
        _roles = (respuestas[1].data as List? ?? const []).map((rol) => '$rol').toList();
        _cargando = false;
      });
    } on DioException catch (e) {
      // En vivo: si la recarga falla (sin señal), se queda lo que ya se veía.
      if (silencioso) return;
      if (!mounted) return;
      setState(() {
        _error = e.response?.data is Map
            ? '${(e.response!.data as Map)['message'] ?? 'No se pudieron cargar los datos.'}'
            : 'No se pudieron cargar los datos. Revisa la conexión.';
        _cargando = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final activos = _usuarios.where((usuario) => usuario['activo'] != false).length;
    return HuataScaffold(
      titulo: 'Usuarios y roles',
      actions: [
        IconButton(onPressed: _cargar, tooltip: 'Actualizar', icon: const Icon(Icons.refresh)),
      ],
      body: _cargando
            ? const Center(child: CircularProgressIndicator())
            : _error != null
                ? _ErrorCarga(mensaje: _error!, reintentar: _cargar)
                : RefreshIndicator(
                    onRefresh: _cargar,
                    child: ListView(
                      physics: const AlwaysScrollableScrollPhysics(),
                      padding: const EdgeInsets.all(16),
                      children: [
                        Text(
                          'Panel del administrador',
                          style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800),
                        ),
                        const SizedBox(height: 4),
                        const Text(
                          'Cuentas sincronizadas con proveedores y acopiadores.',
                          style: TextStyle(color: HuataColors.muted),
                        ),
                        const SizedBox(height: 16),
                        Row(
                          children: [
                            Expanded(child: _Resumen(valor: '${_usuarios.length}', etiqueta: 'Usuarios')),
                            const SizedBox(width: 10),
                            Expanded(child: _Resumen(valor: '$activos', etiqueta: 'Activos')),
                            const SizedBox(width: 10),
                            Expanded(child: _Resumen(valor: '${_roles.length}', etiqueta: 'Roles')),
                          ],
                        ),
                        const SizedBox(height: 20),
                        Text('Roles disponibles', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
                        const SizedBox(height: 10),
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: _roles.map((rol) => Chip(label: Text(_nombreRol(rol)))).toList(),
                        ),
                        const SizedBox(height: 22),
                        Text('Usuarios', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
                        const SizedBox(height: 8),
                        ..._usuarios.map(_tarjetaUsuario),
                      ],
                    ),
                  ),
    );
  }

  Widget _tarjetaUsuario(Map<String, dynamic> usuario) {
    final roles = (usuario['roles'] as List? ?? const []).map((rol) => _nombreRol('$rol')).join(' · ');
    final activo = usuario['activo'] != false;
    final esAdmin = (usuario['roles'] as List? ?? const []).contains('ADMINISTRADOR');
    return Card(
      color: esAdmin ? HuataColors.greenSoft : null,
      margin: const EdgeInsets.only(bottom: 10),
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: esAdmin ? HuataColors.green : HuataColors.greenSoft,
          foregroundColor: esAdmin ? Colors.white : HuataColors.green,
          child: Icon(esAdmin ? Icons.admin_panel_settings : Icons.person_outline),
        ),
        title: Text('${usuario['nombreCompleto'] ?? 'Sin nombre'}', style: const TextStyle(fontWeight: FontWeight.w700)),
        subtitle: Text('${usuario['username'] ?? ''}\n$roles'),
        isThreeLine: true,
        trailing: Container(
          width: 10,
          height: 10,
          decoration: BoxDecoration(color: activo ? Colors.green : Colors.redAccent, shape: BoxShape.circle),
        ),
      ),
    );
  }

  String _nombreRol(String rol) => switch (rol) {
        'ADMINISTRADOR' => 'Administrador',
        'ACOPIADOR' => 'Acopiador',
        'PROVEEDOR' => 'Proveedor',
        'CALIDAD' => 'Analista de calidad',
        'TESTEADOR' => 'Testeador',
        'SECRETARIA' => 'Secretaría y finanzas',
        'PRODUCTOR_QUESO' => 'Producción',
        'SUPERVISOR_PLANTA' => 'Supervisor de planta',
        'OPERADOR_PLANTA' => 'Operador de planta',
        'CAJA_CONTABILIDAD' => 'Caja / Contabilidad',
        'COMPRAS_ALMACEN' => 'Compras / Almacén',
        'VENTAS' => 'Ventas',
        'GERENCIA' => 'Gerencia / Consulta',
        _ => rol,
      };
}

class _Resumen extends StatelessWidget {
  const _Resumen({required this.valor, required this.etiqueta});
  final String valor;
  final String etiqueta;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 10),
      decoration: BoxDecoration(color: HuataColors.greenSoft, borderRadius: BorderRadius.circular(16)),
      child: Column(
        children: [
          Text(valor, style: const TextStyle(fontSize: 23, fontWeight: FontWeight.w800, color: HuataColors.green)),
          Text(etiqueta, style: const TextStyle(fontSize: 12, color: HuataColors.muted)),
        ],
      ),
    );
  }
}

class _ErrorCarga extends StatelessWidget {
  const _ErrorCarga({required this.mensaje, required this.reintentar});
  final String mensaje;
  final VoidCallback reintentar;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.cloud_off_outlined, size: 48, color: HuataColors.muted),
            const SizedBox(height: 12),
            Text(mensaje, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            FilledButton.icon(onPressed: reintentar, icon: const Icon(Icons.refresh), label: const Text('Reintentar')),
          ],
        ),
      ),
    );
  }
}
