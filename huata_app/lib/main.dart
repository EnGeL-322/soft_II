import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'core/actualizacion/actualizacion_service.dart';
import 'core/api/acopiadores_api.dart';
import 'core/api/api_client.dart';
import 'core/api/calidad_api.dart';
import 'core/api/cuenta_api.dart';
import 'core/api/liquidaciones_api.dart';
import 'core/api/mensajeria_api.dart';
import 'core/api/portal_api.dart';
import 'core/api/produccion_api.dart';
import 'core/api/productores_api.dart';
import 'core/api/quesos_api.dart';
import 'core/api/recojos_api.dart';
import 'core/api/ruteo_api.dart';
import 'core/api/trazados_api.dart';
import 'core/api/rutas_api.dart';
import 'core/api/ubicaciones_api.dart';
import 'core/auth/auth_controller.dart';
import 'core/api/acopio_oficina_api.dart';
import 'core/api/configuracion_api.dart';
import 'core/api/personas_api.dart';
import 'core/api/produccion_oficina_api.dart';
import 'core/api/ventas_pagos_api.dart';
import 'core/auth/token_store.dart';
import 'core/tiempo_real/cambios_en_vivo.dart';
import 'core/local/local_store.dart';
import 'core/local/local_store_factory.dart';
import 'core/mapa/recorrido_mapa.dart';
import 'core/mensajeria/mensajeria_en_vivo.dart';
import 'core/sync/sync_service.dart';
import 'core/theme/huata_theme.dart';
import 'features/actualizacion/actualizacion_obligatoria_screen.dart';
import 'features/auth/login_screen.dart';
import 'features/shell/role_router.dart';

void main() {
  final tokenStore = TokenStore();
  final apiClient = ApiClient(tokenStore);
  final localStore = LocalStoreImpl();
  final auth = AuthController(apiClient, tokenStore);

  runApp(
    MultiProvider(
      providers: [
        Provider<TokenStore>.value(value: tokenStore),
        Provider<ApiClient>.value(value: apiClient),
        Provider<LocalStore>.value(value: localStore),
        Provider<RutasApi>(create: (_) => RutasApi(apiClient)),
        // Vistas de oficina (las de la web) en el celular.
        Provider<AcopioOficinaApi>(create: (_) => AcopioOficinaApi(apiClient)),
        Provider<VentasPagosApi>(create: (_) => VentasPagosApi(apiClient)),
        Provider<ProduccionOficinaApi>(create: (_) => ProduccionOficinaApi(apiClient)),
        Provider<PersonasApi>(create: (_) => PersonasApi(apiClient)),
        Provider<ConfiguracionApi>(create: (_) => ConfiguracionApi(apiClient)),
        Provider<ProductoresApi>(create: (_) => ProductoresApi(apiClient)),
        Provider<AcopiadoresApi>(create: (_) => AcopiadoresApi(apiClient)),
        Provider<RecojosApi>(create: (_) => RecojosApi(apiClient)),
        Provider<CalidadApi>(create: (_) => CalidadApi(apiClient)),
        Provider<CuentaApi>(create: (_) => CuentaApi(apiClient)),
        Provider<QuesosApi>(create: (_) => QuesosApi(apiClient)),
        Provider<UbicacionesApi>(create: (_) => UbicacionesApi(apiClient)),
        Provider<PortalApi>(create: (_) => PortalApi(apiClient)),
        Provider<LiquidacionesApi>(create: (_) => LiquidacionesApi(apiClient)),
        Provider<MensajeriaApi>(create: (_) => MensajeriaApi(apiClient)),
        Provider<ProduccionApi>(create: (_) => ProduccionApi(apiClient)),
        Provider<TrazadosApi>(create: (_) => TrazadosApi(apiClient)),
        Provider<RuteoApi>(create: (_) => RuteoApi()),
        ChangeNotifierProvider<AuthController>(create: (_) => auth..restaurar()),
        // Versión nueva del APK (lo nativo); los parches de código llegan solos por Shorebird.
        ChangeNotifierProvider<ActualizacionService>(create: (_) => ActualizacionService(apiClient)..iniciar()),
        ChangeNotifierProvider<PreferenciasMapa>(create: (_) => PreferenciasMapa()..cargar()),
        ChangeNotifierProvider<SyncService>(
          // La cola de mensajes solo sale con la sesión de quien los escribió.
          create: (_) => SyncService(localStore, apiClient, usuarioActual: () => auth.sesion?.username)..iniciar(),
        ),
        ChangeNotifierProvider<MensajeriaEnVivo>(
          // Al reconectar el canal, la bandeja de salida manda lo que quedó pendiente.
          create: (ctx) => MensajeriaEnVivo(token: tokenStore.read)..alConectar = () => ctx.read<SyncService>().sincronizar(),
        ),
        ChangeNotifierProvider<CambiosEnVivo>(
          // Tiempo real del resto: lo que se guarda en la web u otro celular recarga las pantallas abiertas.
          // Si cambian los permisos de esta cuenta, se releen al momento (aparecen o se van módulos).
          create: (_) => CambiosEnVivo(token: tokenStore.read)..alCambiarAcceso = auth.refrescarPermisos,
        ),
      ],
      child: const HuataApp(),
    ),
  );
}

class HuataApp extends StatelessWidget {
  const HuataApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Huata Lácteos',
      debugShowCheckedModeBanner: false,
      theme: buildHuataTheme(),
      home: Consumer2<AuthController, ActualizacionService>(
        builder: (context, auth, actualizacion, _) {
          // Antes que el login: una versión que el servidor ya no acepta no debe registrar nada.
          if (actualizacion.estado == EstadoActualizacion.obligatoria) {
            return const ActualizacionObligatoriaScreen();
          }
          switch (auth.estado) {
            case EstadoSesion.cargando:
              return const _SplashScreen();
            case EstadoSesion.noAutenticado:
              return const LoginScreen();
            case EstadoSesion.autenticado:
              return const RoleRouter();
          }
        },
      ),
    );
  }
}

class _SplashScreen extends StatelessWidget {
  const _SplashScreen();

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: DecoratedBox(
        decoration: BoxDecoration(gradient: HuataMarca.degradado),
        child: Center(child: CircularProgressIndicator(color: Colors.white)),
      ),
    );
  }
}
