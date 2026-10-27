import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/auth/auth_controller.dart';
import '../../core/theme/huata_theme.dart';
import '../../shared/huata_scaffold.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _usuarioCtrl = TextEditingController();
  final _claveCtrl = TextEditingController();
  bool _enviando = false;
  bool _verClave = false;

  @override
  void dispose() {
    _usuarioCtrl.dispose();
    _claveCtrl.dispose();
    super.dispose();
  }

  Future<void> _enviar() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _enviando = true);
    final auth = context.read<AuthController>();
    await auth.iniciarSesion(_usuarioCtrl.text.trim(), _claveCtrl.text);
    if (mounted) setState(() => _enviando = false);
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthController>();
    return Scaffold(
      backgroundColor: HuataMarca.verdeProfundo,
      body: DecoratedBox(
        decoration: const BoxDecoration(gradient: HuataMarca.degradado),
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 28),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 420),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const Center(child: LogoHuata(tamano: 64)),
                    const SizedBox(height: 16),
                    const Text(
                      'Huata Lácteos',
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 26, fontWeight: FontWeight.w800, color: Colors.white, letterSpacing: -0.5),
                    ),
                    const SizedBox(height: 4),
                    const Text(
                      'Acopio, calidad y producción en campo',
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 14, color: Color(0xCCFFFFFF)),
                    ),
                    const SizedBox(height: 28),
                    Container(
                      padding: const EdgeInsets.fromLTRB(20, 24, 20, 20),
                      decoration: BoxDecoration(
                        color: HuataColors.panel,
                        borderRadius: BorderRadius.circular(22),
                        boxShadow: const [BoxShadow(color: Color(0x33000000), blurRadius: 30, offset: Offset(0, 12))],
                      ),
                      child: Form(
                        key: _formKey,
                        child: AutofillGroup(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              const Text('Iniciar sesión', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700)),
                              const SizedBox(height: 4),
                              const Text(
                                'Ingresa con la cuenta que te dio la planta.',
                                style: TextStyle(color: HuataColors.muted, fontSize: 13.5),
                              ),
                              const SizedBox(height: 22),
                              TextFormField(
                                controller: _usuarioCtrl,
                                keyboardType: TextInputType.emailAddress,
                                textInputAction: TextInputAction.next,
                                autofillHints: const [AutofillHints.username],
                                decoration: const InputDecoration(
                                  labelText: 'Usuario',
                                  hintText: 'nombre@huata.pe',
                                  prefixIcon: Icon(Icons.person_outline),
                                ),
                                validator: (v) => (v == null || v.trim().isEmpty) ? 'Ingresa tu usuario' : null,
                              ),
                              const SizedBox(height: 14),
                              TextFormField(
                                controller: _claveCtrl,
                                obscureText: !_verClave,
                                autofillHints: const [AutofillHints.password],
                                decoration: InputDecoration(
                                  labelText: 'Contraseña',
                                  prefixIcon: const Icon(Icons.lock_outline),
                                  suffixIcon: IconButton(
                                    tooltip: _verClave ? 'Ocultar contraseña' : 'Ver contraseña',
                                    icon: Icon(_verClave ? Icons.visibility_off_outlined : Icons.visibility_outlined),
                                    onPressed: () => setState(() => _verClave = !_verClave),
                                  ),
                                ),
                                validator: (v) => (v == null || v.isEmpty) ? 'Ingresa tu contraseña' : null,
                                onFieldSubmitted: (_) => _enviar(),
                              ),
                              if (auth.errorLogin != null) ...[
                                const SizedBox(height: 14),
                                EstadoAviso(mensaje: auth.errorLogin!, esError: true),
                              ],
                              const SizedBox(height: 20),
                              ElevatedButton(
                                onPressed: _enviando ? null : _enviar,
                                style: ElevatedButton.styleFrom(minimumSize: const Size.fromHeight(52)),
                                child: _enviando
                                    ? const SizedBox(
                                        height: 20,
                                        width: 20,
                                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                                      )
                                    : const Text('Ingresar'),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 20),
                    const Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.cloud_done_outlined, size: 16, color: Color(0xB3FFFFFF)),
                        SizedBox(width: 6),
                        Flexible(
                          child: Text(
                            'Funciona sin señal: lo registrado se envía solo',
                            textAlign: TextAlign.center,
                            style: TextStyle(fontSize: 12.5, color: Color(0xB3FFFFFF)),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
