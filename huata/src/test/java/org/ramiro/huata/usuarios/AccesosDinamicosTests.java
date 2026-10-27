package org.ramiro.huata.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accesos dinamicos (V46): roles editables, roles personalizados, excepciones por usuario y las
 * protecciones que impiden que un cambio deje el sistema sin administracion.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "admin.pruebas@huata.pe")
class AccesosDinamicosTests {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private RolService rolService;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private PermisoRepository permisoRepository;

    private static String unico(String prefijo) {
        return prefijo + System.nanoTime() + "@huata.pe";
    }

    private static UsuarioRequest solicitud(String username, List<String> roles, List<String> conceder, List<String> quitar) {
        return new UsuarioRequest("Persona de prueba", username, username, "951000111", "Clave2026*", roles, conceder, quitar);
    }

    // ---------------------------------------------------------------- roles personalizados

    @Test
    void unRolPersonalizadoGeneraSuCodigoYConcedeSusPermisos() {
        RolResponse rol = rolService.crear(new RolRequest("Jefe de turño", "Supervisa el turno de noche",
                List.of(Permiso.PANEL_READ, Permiso.ACOPIO_READ)));

        assertThat(rol.nombre()).isEqualTo("JEFE_DE_TURNO");
        assertThat(rol.sistema()).isFalse();
        Usuario usuario = usuarioService.crearDesdeAdministracion(
                solicitud(unico("turno"), List.of(rol.nombre()), null, null));
        assertThat(Usuarios.permisosDe(usuario)).containsExactly(Permiso.ACOPIO_READ, Permiso.PANEL_READ);
    }

    @Test
    void dosRolesConElMismoNombreNoSePermiten() {
        rolService.crear(new RolRequest("Auxiliar de planta", null, List.of(Permiso.PANEL_READ)));

        assertThatThrownBy(() -> rolService.crear(new RolRequest("auxiliar de planta", null, List.of())))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Ya existe");
    }

    @Test
    void unPermisoInventadoSeRechaza() {
        assertThatThrownBy(() -> rolService.crear(new RolRequest("Rol raro", null, List.of("todo.poder"))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Permiso desconocido");
    }

    @Test
    void unRolPredefinidoNoSeEliminaYUnoConUsuariosTampoco() {
        Rol ventas = rolRepository.findByNombre(Rol.VENTAS).orElseThrow();
        assertThatThrownBy(() -> rolService.eliminar(ventas.getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("predefinidos");

        RolResponse propio = rolService.crear(new RolRequest("Temporal", null, List.of(Permiso.PANEL_READ)));
        usuarioService.crearDesdeAdministracion(solicitud(unico("temporal"), List.of(propio.nombre()), null, null));
        assertThatThrownBy(() -> rolService.eliminar(propio.id()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("usuario(s) activo(s)");
    }

    @Test
    void unRolEliminadoYaNoSeOfreceNiSeAsigna() {
        RolResponse propio = rolService.crear(new RolRequest("Eventual", null, List.of(Permiso.PANEL_READ)));

        rolService.eliminar(propio.id());

        assertThat(rolService.asignablesEnUsuarios()).doesNotContain(propio.nombre());
        assertThatThrownBy(() -> usuarioService.crear(solicitud(unico("eventual"), List.of(propio.nombre()), null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Rol invalido");
    }

    @Test
    void restaurarDevuelveUnRolPredefinidoASuEstandar() {
        Rol ventas = rolRepository.findByNombre(Rol.VENTAS).orElseThrow();
        rolService.actualizar(ventas.getId(), new RolRequest("Vendedores", "otra", List.of(Permiso.PANEL_READ)));

        RolResponse restaurado = rolService.restaurarEstandar(ventas.getId());

        assertThat(restaurado.etiqueta()).isEqualTo("Ventas");
        assertThat(new TreeSet<>(restaurado.permisos())).isEqualTo(new TreeSet<>(MatrizPermisos.POR_ROL.get(Rol.VENTAS)));
    }

    /** Darle a PROVEEDOR acopio.read le dejaria ver las entregas de todos los demas proveedores. */
    @Test
    void losPermisosDelProveedorNoSeEditan() {
        Rol proveedor = rolRepository.findByNombre(Rol.PROVEEDOR).orElseThrow();

        assertThatThrownBy(() -> rolService.actualizar(proveedor.getId(),
                        new RolRequest("Proveedor", null, List.of(Permiso.MENSAJERIA_READ, Permiso.ACOPIO_READ))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("no se editan");
    }

    /** Cambiar la etiqueta del proveedor si se puede: lo bloqueado son sus permisos. */
    @Test
    void alProveedorSeLePuedeCambiarElNombreVisible() {
        Rol proveedor = rolRepository.findByNombre(Rol.PROVEEDOR).orElseThrow();
        List<String> actuales = proveedor.getPermisos().stream().map(Permiso::getCodigo).toList();

        RolResponse editado = rolService.actualizar(proveedor.getId(), new RolRequest("Socio lechero", null, actuales));

        assertThat(editado.etiqueta()).isEqualTo("Socio lechero");
    }

    // ---------------------------------------------------------------- excepciones por usuario

    @Test
    void unPermisoConcedidoSeSumaAlDeSusRoles() {
        Usuario u = usuarioService.crearDesdeAdministracion(
                solicitud(unico("ventas"), List.of(Rol.VENTAS), List.of(Permiso.REPORTES_READ), List.of()));

        assertThat(Usuarios.permisosDe(u)).contains(Permiso.REPORTES_READ, Permiso.QUESOS_WRITE);
        assertThat(Usuarios.excepciones(u, UsuarioPermiso.Tipo.CONCEDER)).containsExactly(Permiso.REPORTES_READ);
    }

    @Test
    void unPermisoQuitadoGanaAunqueOtroRolLoConceda() {
        Usuario u = usuarioService.crearDesdeAdministracion(solicitud(
                unico("doble"), List.of(Rol.VENTAS, Rol.GERENCIA), List.of(), List.of(Permiso.QUESOS_READ)));

        assertThat(Usuarios.permisosDe(u)).doesNotContain(Permiso.QUESOS_READ);
    }

    /** Conceder lo que ya dan sus roles, o quitar lo que no dan, no cambia nada: no se guarda. */
    @Test
    void lasExcepcionesSinEfectoNoSeGuardan() {
        Usuario u = usuarioService.crearDesdeAdministracion(solicitud(
                unico("limpio"), List.of(Rol.VENTAS), List.of(Permiso.QUESOS_WRITE), List.of(Permiso.PAGOS_WRITE)));

        assertThat(u.getPermisosPersonalizados()).isEmpty();
    }

    @Test
    void noSePuedeConcederYQuitarElMismoPermiso() {
        assertThatThrownBy(() -> usuarioService.crearDesdeAdministracion(solicitud(
                        unico("contradiccion"), List.of(Rol.VENTAS), List.of(Permiso.REPORTES_READ),
                        List.of(Permiso.REPORTES_READ))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("a la vez");
    }

    @Test
    void editarLasExcepcionesReemplazaLasAnterioresSinChocarConLaRestriccionUnica() {
        String username = unico("editable");
        Usuario u = usuarioService.crearDesdeAdministracion(
                solicitud(username, List.of(Rol.VENTAS), List.of(Permiso.REPORTES_READ), List.of(Permiso.QUESOS_WRITE)));

        // Mismo permiso, tipo cambiado; uno nuevo; uno que desaparece.
        Usuario editado = usuarioService.actualizarDesdeAdministracion(u.getId(), solicitud(
                username, List.of(Rol.VENTAS), List.of(Permiso.TRAZABILIDAD_READ), List.of(Permiso.QUESOS_READ)));

        assertThat(Usuarios.excepciones(editado, UsuarioPermiso.Tipo.CONCEDER)).containsExactly(Permiso.TRAZABILIDAD_READ);
        assertThat(Usuarios.excepciones(editado, UsuarioPermiso.Tipo.REVOCAR)).containsExactly(Permiso.QUESOS_READ);
        assertThat(Usuarios.permisosDe(editado)).contains(Permiso.QUESOS_WRITE).doesNotContain(Permiso.REPORTES_READ);
    }

    /** Acopiadores y Proveedores no mandan excepciones (null): editar su cuenta no debe borrarlas. */
    @Test
    void sinExcepcionesEnLaPeticionSeConservanLasQueHabia() {
        String username = unico("conserva");
        Usuario u = usuarioService.crearDesdeAdministracion(
                solicitud(username, List.of(Rol.VENTAS), List.of(Permiso.REPORTES_READ), List.of()));

        Usuario editado = usuarioService.actualizar(u.getId(),
                new UsuarioRequest("Otro nombre", username, username, null, null, List.of(Rol.VENTAS)));

        assertThat(Usuarios.excepciones(editado, UsuarioPermiso.Tipo.CONCEDER)).containsExactly(Permiso.REPORTES_READ);
    }

    // ---------------------------------------------------------------- protecciones

    @Test
    void noSePuedeQuitarAlRolAdministradorLaAdministracionSiNadieMasLaTiene() {
        Rol admin = rolRepository.findByNombre(Rol.ADMINISTRADOR).orElseThrow();
        List<String> sinUsuariosWrite = admin.getPermisos().stream()
                .map(Permiso::getCodigo)
                .filter(c -> !c.equals(Permiso.USUARIOS_WRITE))
                .toList();

        assertThatThrownBy(() -> rolService.actualizar(admin.getId(), new RolRequest("Administrador", null, sinUsuariosWrite)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("sin ningun usuario activo");
    }

    @Test
    @WithMockUser(username = "yo.mismo@huata.pe")
    void nadiePuedeQuitarseASiMismoLaAdministracionDeUsuarios() {
        Usuario yo = usuarioService.crearDesdeAdministracion(
                solicitud("yo.mismo@huata.pe", List.of(Rol.ADMINISTRADOR), null, null));

        assertThatThrownBy(() -> usuarioService.actualizarDesdeAdministracion(yo.getId(),
                        solicitud("yo.mismo@huata.pe", List.of(Rol.ADMINISTRADOR), List.of(),
                                List.of(Permiso.USUARIOS_WRITE))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("ti mismo");
    }

    @Test
    @WithMockUser(username = "me.inactivo@huata.pe")
    void nadiePuedeInactivarSuPropiaCuenta() {
        Usuario yo = usuarioService.crearDesdeAdministracion(
                solicitud("me.inactivo@huata.pe", List.of(Rol.ADMINISTRADOR), null, null));

        assertThatThrownBy(() -> usuarioService.inactivarDesdeAdministracion(yo.getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("propia cuenta");
    }

    @Test
    void unaCuentaInactivadaSePuedeReactivar() {
        Usuario u = usuarioService.crearDesdeAdministracion(solicitud(unico("vuelve"), List.of(Rol.VENTAS), null, null));
        usuarioService.inactivarDesdeAdministracion(u.getId());

        Usuario reactivado = usuarioService.reactivar(u.getId());

        assertThat(reactivado.isActivo()).isTrue();
        assertThat(usuarioService.buscar(u.getUsername(), UsuarioService.Estado.INACTIVOS, false, PageRequest.of(0, 5))
                .content()).isEmpty();
    }

    @Test
    void elListadoInternoNoMezclaCuentasDeAcopiadoresNiProveedores() {
        String acopiador = unico("acopiador.listado");
        usuarioService.sincronizarCuentaDeAcceso(null, "Acopiador Listado", acopiador, "951", "Clave2026*", Rol.ACOPIADOR);

        assertThat(usuarioService.buscar(acopiador, UsuarioService.Estado.ACTIVOS, true, PageRequest.of(0, 5)).content())
                .isEmpty();
        assertThat(usuarioService.buscar(acopiador, UsuarioService.Estado.ACTIVOS, false, PageRequest.of(0, 5)).content())
                .hasSize(1);
    }

    // ---------------------------------------------------------------- eliminar

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private org.ramiro.huata.mensajeria.ConversacionProveedorRepository conversacionRepository;

    @Autowired
    private org.ramiro.huata.mensajeria.MensajeProveedorRepository mensajeRepository;

    @Autowired
    private org.ramiro.huata.productores.ProductorRepository productorRepository;

    @Test
    void eliminarSinHistorialBorraLaCuentaDeVerdadYLiberaElCorreo() {
        String username = unico("borrar");
        Usuario u = usuarioService.crearDesdeAdministracion(
                solicitud(username, List.of(Rol.VENTAS), List.of(Permiso.REPORTES_READ), List.of()));

        UsuarioService.ResultadoEliminacion r = usuarioService.eliminarDesdeAdministracion(u.getId());

        assertThat(r.conservaHistorial()).isFalse();
        assertThat(usuarioRepository.findById(u.getId())).isEmpty();
        // El mismo correo se puede volver a usar.
        usuarioService.crearDesdeAdministracion(solicitud(username, List.of(Rol.VENTAS), null, null));
    }

    /**
     * Los mensajes a proveedores son historial del proveedor y exigen autor: la cuenta se da de baja
     * (no entra, sin roles, fuera de los listados, correo libre) pero no se borra la fila.
     */
    @Test
    void eliminarConMensajesConservaElAutorPeroSacaLaCuentaDelSistema() {
        String username = unico("conmensajes");
        Usuario u = usuarioService.crearDesdeAdministracion(solicitud(username, List.of(Rol.SECRETARIA), null, null));
        var productor = productorRepository.findAll().get(0);
        var conversacion = conversacionRepository.findAll().stream()
                .filter(c -> c.getProductor().getId().equals(productor.getId()))
                .findFirst()
                .orElseGet(() -> {
                    var nueva = new org.ramiro.huata.mensajeria.ConversacionProveedor();
                    nueva.setProductor(productor);
                    return conversacionRepository.save(nueva);
                });
        var mensaje = new org.ramiro.huata.mensajeria.MensajeProveedor();
        mensaje.setConversacion(conversacion);
        mensaje.setAutor(u);
        mensaje.setCuerpo("Hola, su pago está listo");
        mensajeRepository.save(mensaje);

        UsuarioService.ResultadoEliminacion r = usuarioService.eliminarDesdeAdministracion(u.getId());

        assertThat(r.conservaHistorial()).isTrue();
        Usuario baja = usuarioRepository.findById(u.getId()).orElseThrow();
        assertThat(baja.isActivo()).isFalse();
        assertThat(baja.getRoles()).isEmpty();
        assertThat(baja.getNombreCompleto()).isEqualTo("Persona de prueba");
        assertThat(usuarioService.buscar(username, UsuarioService.Estado.TODOS, false, PageRequest.of(0, 5)).content())
                .isEmpty();
        assertThatThrownBy(() -> usuarioService.reactivar(u.getId())).isInstanceOf(BusinessRuleException.class);
        usuarioService.crearDesdeAdministracion(solicitud(username, List.of(Rol.VENTAS), null, null));
    }

    @Test
    @WithMockUser(username = "me.borro@huata.pe")
    void nadiePuedeEliminarSuPropiaCuenta() {
        Usuario yo = usuarioService.crearDesdeAdministracion(
                solicitud("me.borro@huata.pe", List.of(Rol.ADMINISTRADOR), null, null));

        assertThatThrownBy(() -> usuarioService.eliminarDesdeAdministracion(yo.getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("propia cuenta");
    }

    @Test
    void laCuentaDeUnAcopiadorNoSeEliminaDesdeUsuarios() {
        Usuario acopiador = usuarioService.sincronizarCuentaDeAcceso(
                null, "Acopiador Borrable", unico("acopiador.borrar"), "951", "Clave2026*", Rol.ACOPIADOR);

        assertThatThrownBy(() -> usuarioService.eliminarDesdeAdministracion(acopiador.getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("su propio modulo");
    }

    // ---------------------------------------------------------------- catalogo de vistas

    /** Un permiso que no aparece en ninguna vista no se podria conceder desde la pantalla. */
    @Test
    void todoPermisoDelSistemaApareceEnAlgunaVista() {
        Set<String> enCatalogo = rolService.catalogo().stream()
                .flatMap(m -> m.vistas().stream())
                .flatMap(v -> v.acciones().stream())
                .map(AccionAccesoResponse::permiso)
                .collect(Collectors.toSet());

        assertThat(enCatalogo).containsAll(
                permisoRepository.findAll().stream().map(Permiso::getCodigo).toList());
    }

    @Test
    void elPermisoCompartidoPorDosVistasLoIndica() {
        AccionAccesoResponse aprobar = rolService.catalogo().stream()
                .flatMap(m -> m.vistas().stream())
                .filter(v -> v.codigo().equals("solicitudes-zona"))
                .flatMap(v -> v.acciones().stream())
                .filter(a -> a.permiso().equals(Permiso.PROVEEDORES_WRITE))
                .findFirst()
                .orElseThrow();

        assertThat(aprobar.compartidaCon()).containsExactly("Proveedores");
    }

    @Test
    void cadaVistaIncluyeSuPermisoDeAccesoEntreSusAcciones() {
        rolService.catalogo().stream().flatMap(m -> m.vistas().stream()).forEach(v -> assertThat(
                        v.acciones().stream().map(AccionAccesoResponse::permiso))
                .as("vista %s", v.codigo())
                .contains(v.permisoAcceso()));
    }
}
