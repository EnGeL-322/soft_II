package org.ramiro.huata.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Criterio de salida de la Fase 1: "un usuario con rol insuficiente recibe 403 del backend aunque el
 * boton este oculto".
 *
 * <p>Se prueba contra las autoridades, no contra usuarios sembrados, porque lo que se verifica aqui
 * es el contrato de cada endpoint: que exija el permiso que dice exigir. Que cada rol reciba los
 * permisos correctos es lo que comprueban {@link MatrizPermisosTest} y MigracionesFlywayIT.
 */
@SpringBootTest
class AutorizacionPorPermisoTests {

    @Autowired
    private WebApplicationContext contexto;

    private MockMvc mockMvc;

    // Se construye a mano (igual que IngestaRecepcionesTests) para no depender de
    // @AutoConfigureMockMvc, que cambio de paquete en Spring Boot 4.
    private MockMvc mockMvc() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(contexto)
                    .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                            .springSecurity())
                    .build();
        }
        return mockMvc;
    }

    @Test
    void sinAutenticacionNoSeEntraAUnEndpointInterno() throws Exception {
        mockMvc().perform(get("/api/v1/recojos")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {Permiso.ACOPIO_READ})
    void autenticadoConElPermisoDeLecturaEntra() throws Exception {
        mockMvc().perform(get("/api/v1/recojos")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {Permiso.CALIDAD_READ, Permiso.PAGOS_READ})
    void autenticadoSinElPermisoDelRecursoRecibe403YNo401() throws Exception {
        // Tiene sesion valida y permisos de otros modulos: el rechazo debe ser de autorizacion.
        mockMvc().perform(get("/api/v1/recojos")).andExpect(status().isForbidden());
    }

    /**
     * El caso que de verdad importa: tener lectura de un modulo NO habilita a escribir en el. Es la
     * diferencia entre consultar el acopio y registrar una recepcion a nombre de otro.
     *
     * <p>El cuerpo va completo y valido a proposito. Spring resuelve los argumentos del handler
     * (incluida la validacion de @Valid @RequestBody) ANTES de evaluar @PreAuthorize, asi que un
     * cuerpo invalido devolveria 400 y la prueba pasaria sin haber llegado nunca a la autorizacion.
     * Con un cuerpo valido, el unico motivo posible del rechazo es el permiso que falta.
     */
    @Test
    @WithMockUser(authorities = {Permiso.ACOPIO_READ})
    void leerUnModuloNoHabilitaAEscribirEnEl() throws Exception {
        // Entrega directa en planta: un recojo en ruta exigiria el acopiador que lo hizo, y esta prueba
        // mide permisos, no esa regla.
        // Fecha muy en el futuro a proposito: el proveedor 1 puede tener ya un acopio sembrado por
        // DemoDataSeeder en la fecha de hoy (regla de una entrega diaria), y este cuerpo debe seguir
        // siendo valido sin importar cuando se corra la prueba.
        String recepcionValida =
                "{\"productorId\":1,\"fechaHora\":\"2035-05-10T12:00:00Z\",\"litros\":120.0,\"fuente\":\"PLANTA\"}";

        mockMvc().perform(post("/api/v1/recojos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recepcionValida))
                .andExpect(status().isForbidden());

        // Y con el permiso de escritura sí llega al controlador (no se queda en 403).
        mockMvc().perform(post("/api/v1/recojos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recepcionValida)
                        .with(usuarioCon(Permiso.ACOPIO_WRITE)))
                .andExpect(status().isCreated());
    }

    /** Quien analiza (calidad.write, el testeador) no aprueba: aprobar es calidad.aprobar (V48). */
    @Test
    void soloCalidadAprobarApruebaAnalisis() throws Exception {
        mockMvc().perform(patch("/api/v1/calidad/controles/1/aprobar")
                        .with(usuarioCon(Permiso.CALIDAD_WRITE)))
                .andExpect(status().isForbidden());
        mockMvc().perform(patch("/api/v1/calidad/controles/1/aprobar")
                        .with(usuarioCon(Permiso.CALIDAD_APROBAR)))
                .andExpect(status().isOk());
    }

    /** Planta registra entregas directas (planta.recepcion); el recojo en ruta es solo del acopiador. */
    @Test
    void plantaRegistraEntregasDirectasPeroNoRecojosEnRuta() throws Exception {
        String directa =
                "{\"productorId\":2,\"fechaHora\":\"2035-05-11T12:00:00Z\",\"litros\":40.0,\"fuente\":\"PLANTA\"}";
        String enRuta =
                "{\"productorId\":2,\"fechaHora\":\"2035-05-12T12:00:00Z\",\"litros\":40.0,\"fuente\":\"RUTA\",\"acopiadorId\":1}";

        mockMvc().perform(post("/api/v1/recojos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directa)
                        .with(usuarioCon(Permiso.PLANTA_RECEPCION)))
                .andExpect(status().isCreated());
        mockMvc().perform(post("/api/v1/recojos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enRuta)
                        .with(usuarioCon(Permiso.PLANTA_RECEPCION)))
                .andExpect(status().isForbidden());
    }

    /**
     * Corregir un recojo (pedido del cliente, 2026-09-24) exige el mismo permiso que registrarlo:
     * es otra forma de escribir sobre el acopio, no una consulta. El cuerpo es valido a proposito
     * (ver el javadoc de la prueba anterior): sin permiso el rechazo tiene que venir de
     * @PreAuthorize, no de la validacion del @RequestBody.
     */
    @Test
    @WithMockUser(authorities = {Permiso.ACOPIO_READ})
    void corregirUnRecojoExigeAcopioWrite() throws Exception {
        String correccionValida = "{\"litros\":10.0,\"motivo\":\"prueba de autorizacion\"}";

        mockMvc().perform(patch("/api/v1/recojos/1/corregir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correccionValida))
                .andExpect(status().isForbidden());

        mockMvc().perform(patch("/api/v1/recojos/1/corregir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correccionValida)
                        .with(usuarioCon(Permiso.ACOPIO_WRITE)))
                .andExpect(status().isOk());
    }

    /**
     * ROLE_ADMINISTRADOR ya no abre nada por si solo: quien autoriza es el permiso. Si esta prueba
     * se pusiera en verde con un 200, significaria que quedo un hasRole() suelto en algun controller.
     */
    @Test
    @WithMockUser(roles = {"ADMINISTRADOR"})
    void elRolAdministradorSinPermisosNoAbreNingunModulo() throws Exception {
        mockMvc().perform(get("/api/v1/usuarios")).andExpect(status().isForbidden());
        mockMvc().perform(get("/api/v1/auditoria")).andExpect(status().isForbidden());
        mockMvc().perform(get("/api/v1/liquidaciones")).andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{1} exige {0}")
    @CsvSource({
        "panel.read,        /api/v1/reportes/dashboard",
        "zonas.read,        /api/v1/reportes/zonas",
        "trazabilidad.read, /api/v1/reportes/trazabilidad",
        "acopio.read,       /api/v1/recojos",
        "calidad.read,      /api/v1/calidad/incidencias",
        "proveedores.read,  /api/v1/productores",
        "acopiadores.read,  /api/v1/acopiadores",
        "pagos.read,        /api/v1/liquidaciones",
        "quesos.read,       /api/v1/quesos",
        "rutas.read,        /api/v1/rutas",
        "usuarios.read,     /api/v1/usuarios",
        "usuarios.read,     /api/v1/roles",
        "parametros.read,   /api/v1/parametros-negocio",
        "auditoria.read,    /api/v1/auditoria",
        "respaldo.read,     /api/v1/exportaciones"
    })
    void cadaEndpointDeLecturaExigeSuPropioPermisoYNingunOtro(String permiso, String url) throws Exception {
        // Arrange / Act / Assert: con el permiso, pasa.
        mockMvc().perform(get(url.trim()).with(usuarioCon(permiso.trim()))).andExpect(status().isOk());

        // Con un permiso cualquiera que no sea el suyo, no.
        String ajeno = permiso.trim().equals(Permiso.ZONAS_READ) ? Permiso.PANEL_READ : Permiso.ZONAS_READ;
        mockMvc().perform(get(url.trim()).with(usuarioCon(ajeno))).andExpect(status().isForbidden());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor usuarioCon(String permiso) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("prueba")
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(permiso));
    }

    /**
     * Los permisos de operacion existen justamente para esto: cerrar un periodo sin poder registrar
     * movimientos dentro de el. Si cerrar-ciclo se colara con pagos.write, quien solo deba cerrar
     * acabaria pudiendo liquidar.
     */
    @Test
    void cerrarElCicloDePagoExigeSuPermisoDeOperacionYNoLaEscrituraDePagos() throws Exception {
        String ciclo = "{\"periodoInicio\":\"2026-09-03\",\"periodoFin\":\"2026-09-09\"}";

        // Quien solo escribe pagos NO cierra el ciclo.
        mockMvc().perform(post("/api/v1/liquidaciones/cerrar-ciclo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ciclo)
                        .with(usuarioCon(Permiso.PAGOS_WRITE)))
                .andExpect(status().isForbidden());

        // Y quien puede cerrar el ciclo NO ejecuta pagos ni ajustes.
        mockMvc().perform(post("/api/v1/liquidaciones/calcular")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productorId\":1,\"periodoInicio\":\"2026-09-03\",\"periodoFin\":\"2026-09-09\"}")
                        .with(usuarioCon(Permiso.PAGOS_CERRAR_CICLO)))
                .andExpect(status().isForbidden());
    }

    /**
     * El catalogo de productos lo leen todas las pantallas que eligen un producto, pero solo lo
     * cambia quien administra recetas: el Productor de queso (produccion.write) fabrica, no decide que
     * se fabrica.
     */
    @Test
    void elCatalogoDeProductosLoLeeProduccionPeroSoloLoCambiaQuienEditaRecetas() throws Exception {
        String producto = "{\"nombre\":\"Yogur de prueba " + System.nanoTime() + "\",\"unidadVenta\":\"botella\"}";

        mockMvc().perform(get("/api/v1/productos").with(usuarioCon(Permiso.PRODUCCION_READ)))
                .andExpect(status().isOk());
        mockMvc().perform(get("/api/v1/productos").with(usuarioCon(Permiso.QUESOS_READ)))
                .andExpect(status().isOk());
        mockMvc().perform(get("/api/v1/productos").with(usuarioCon(Permiso.ZONAS_READ)))
                .andExpect(status().isForbidden());

        mockMvc().perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(producto)
                        .with(usuarioCon(Permiso.PRODUCCION_WRITE)))
                .andExpect(status().isForbidden());
        mockMvc().perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(producto)
                        .with(usuarioCon(Permiso.RECETAS_WRITE)))
                .andExpect(status().isCreated());
    }

    /**
     * El acopiador (acopio.write) no puede anotar cuanto recibio planta de su propia ruta: la
     * conciliacion existe para controlarlo, y desde V31 ese numero es la leche que entra al
     * inventario. Lo registra quien tiene planta.recepcion (Calidad y Administrador, V32).
     */
    @Test
    void registrarLoRecibidoEnPlantaNoLoHaceElAcopiador() throws Exception {
        String recepcion = "{\"fecha\":\"2037-01-15\",\"rutaId\":999999,\"litrosRecibidosPlanta\":100.0}";

        mockMvc().perform(post("/api/v1/conciliaciones-ruta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recepcion)
                        .with(usuarioCon(Permiso.ACOPIO_WRITE)))
                .andExpect(status().isForbidden());

        // Con el permiso pasa la puerta: la ruta inexistente da 404, no 403.
        mockMvc().perform(post("/api/v1/conciliaciones-ruta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recepcion)
                        .with(usuarioCon(Permiso.PLANTA_RECEPCION)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cerrarLaJornadaExigeSuPermisoDeOperacionYNoLaEscrituraDeAcopio() throws Exception {
        String cierre = "{\"litrosPlanta\":1200.0,\"observacion\":\"prueba\"}";

        // El acopiador tiene acopio.write; ya no le alcanza para cerrar la jornada de planta.
        mockMvc().perform(put("/api/v1/operacion/2026-09-13/cierre")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cierre)
                        .with(usuarioCon(Permiso.ACOPIO_WRITE)))
                .andExpect(status().isForbidden());

        // Y quien cierra la jornada no puede registrar una recepcion.
        mockMvc().perform(post("/api/v1/recojos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productorId\":1,\"fechaHora\":\"2026-09-13T12:00:00Z\",\"litros\":120.0}")
                        .with(usuarioCon(Permiso.ACOPIO_CERRAR_JORNADA)))
                .andExpect(status().isForbidden());
    }

    /**
     * V52, decision del cliente: el administrador tiene todos los permisos del catalogo, incluidos
     * registrar, aprobar, pagar, producir y vender. Si falta uno, "darle todo" vuelve a no servir.
     */
    @Test
    void elAdministradorTieneTodosLosPermisosDelCatalogo() {
        assertThat(MatrizPermisos.POR_ROL.get(Rol.ADMINISTRADOR))
                .containsExactlyInAnyOrderElementsOf(MatrizPermisos.CATALOGO.keySet())
                .contains(Permiso.ACOPIO_WRITE, Permiso.CALIDAD_WRITE, Permiso.CALIDAD_APROBAR,
                        Permiso.PAGOS_WRITE, Permiso.PRODUCCION_WRITE, Permiso.QUESOS_WRITE);
    }

    /**
     * El cierre de jornada es de planta (regla 7.8): quien esta en ruta no lo decide.
     *
     * <p>Antes la contraparte era OPERADOR, que era quien cerraba en planta. Al quedar el sistema
     * con los seis roles definidos (V25) ese rol desaparecio, y el unico que conserva el permiso es
     * ADMINISTRADOR: sigue habiendo alguien que puede cerrar, y sigue sin ser el acopiador.
     */
    @Test
    void elAcopiadorNoCierraLaJornadaDePlanta() {
        assertThat(MatrizPermisos.POR_ROL.get(Rol.ACOPIADOR)).doesNotContain(Permiso.ACOPIO_CERRAR_JORNADA);
        assertThat(MatrizPermisos.POR_ROL.get(Rol.ADMINISTRADOR)).contains(Permiso.ACOPIO_CERRAR_JORNADA);
    }

    // ---- Pintar ruta del dia vs. predeterminarla (V27) ----

    /**
     * El reparto que pidio el cliente: el acopiador pinta desde la app, administracion decide el
     * predeterminado. Se prueba contra el filtro real porque es el unico sitio donde eso se sostiene
     * — ocultar el boton en la app no impide llamar al endpoint.
     */
    @Test
    @WithMockUser(authorities = {Permiso.RUTAS_READ, Permiso.ACOPIO_WRITE})
    void elAcopiadorPuedeGuardarSuRecorridoPeroNoPredeterminarlo() throws Exception {
        // La fecha va siempre: es obligatoria para que el listado no devuelva el historial completo, y
        // un parametro que falta da 400 antes de llegar a la comprobacion de permisos.
        mockMvc().perform(get("/api/v1/trazados-acopiador").param("fecha", "2026-09-22"))
                .andExpect(status().isOk());
        mockMvc().perform(post("/api/v1/trazados-acopiador/1/predeterminar"))
                .andExpect(status().isForbidden());
    }

    /**
     * Y al contrario: administracion predetermina, pero no pinta un recorrido a nombre de nadie.
     *
     * <p>El cuerpo va <b>valido</b> a proposito. La validacion del @RequestBody corre al resolver los
     * argumentos, antes de que se entre al proxy de seguridad, asi que un cuerpo mal formado devuelve
     * 400 y la prueba pasaria sin haber comprobado el permiso.
     */
    @Test
    @WithMockUser(authorities = {Permiso.RUTAS_READ, Permiso.RUTAS_WRITE})
    void administracionPredeterminaPeroNoPintaPorElAcopiador() throws Exception {
        mockMvc().perform(put("/api/v1/trazados-acopiador/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sigueRutaDesignada\":true,\"puntos\":["
                                + "{\"secuencia\":0,\"latitud\":-15.65,\"longitud\":-69.98},"
                                + "{\"secuencia\":1,\"latitud\":-15.66,\"longitud\":-69.99}]}"))
                .andExpect(status().isForbidden());
    }

    /** Sin rutas.read no se ven los recorridos de nadie, ni los propios. */
    @Test
    @WithMockUser(authorities = {Permiso.PAGOS_READ})
    void sinRutasReadNoSeVenLosRecorridosPintados() throws Exception {
        mockMvc().perform(get("/api/v1/trazados-acopiador").param("fecha", "2026-09-22"))
                .andExpect(status().isForbidden());
        mockMvc().perform(get("/api/v1/trazados-acopiador/me")).andExpect(status().isForbidden());
    }

    /**
     * "Puntos establecidos" (2026-09-30): mismo reparto que el resto de rutas.write -- leer exige
     * rutas.read (lo que ya tiene el acopiador, tanto /puntos-establecidos como /me), crear/editar/
     * desactivar exige rutas.write (solo administracion).
     */
    @Test
    @WithMockUser(username = "acopiador.demo@huata.pe", authorities = {Permiso.RUTAS_READ})
    void elAcopiadorPuedeVerLosPuntosEstablecidosPeroNoCrearlos() throws Exception {
        mockMvc().perform(get("/api/v1/puntos-establecidos")).andExpect(status().isOk());
        mockMvc().perform(get("/api/v1/puntos-establecidos/me")).andExpect(status().isOk());
        mockMvc().perform(post("/api/v1/puntos-establecidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"x\",\"latitud\":-15.65,\"longitud\":-69.98,\"rutaId\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {Permiso.PAGOS_READ})
    void sinRutasReadNoSeVenLosPuntosEstablecidos() throws Exception {
        mockMvc().perform(get("/api/v1/puntos-establecidos")).andExpect(status().isForbidden());
        mockMvc().perform(get("/api/v1/puntos-establecidos/me")).andExpect(status().isForbidden());
    }

    /**
     * "Cuanto puedo producir": Secretaria registra produccion con quesos.write pero no tiene
     * recetas.read ni produccion.read; el endpoint acepta quesos.read igual que listar/crear lotes.
     */
    @Test
    @WithMockUser(authorities = {Permiso.QUESOS_READ})
    void conQuesosReadSeVeLaCapacidadDeProduccion() throws Exception {
        mockMvc().perform(get("/api/v1/produccion/capacidad")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {Permiso.RUTAS_READ, Permiso.ACOPIO_READ})
    void sinProduccionNiQuesosNoSeVeLaCapacidadDeProduccion() throws Exception {
        mockMvc().perform(get("/api/v1/produccion/capacidad")).andExpect(status().isForbidden());
    }

    // ---- Ventas por operacion (V54) ----

    private static final String VENTA_VALIDA = "{\"loteId\":999999,\"cliente\":\"Cliente prueba\",\"unidades\":1,"
            + "\"tipoComprador\":\"PUBLICO_GENERAL\",\"condicion\":\"CONTADO\",\"medioCobro\":\"EFECTIVO\"}";

    private static org.springframework.test.web.servlet.request.RequestPostProcessor vendedorCon(String... permisos) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("prueba")
                .authorities(java.util.stream.Stream.concat(
                                java.util.stream.Stream.of(Permiso.QUESOS_READ), java.util.Arrays.stream(permisos))
                        .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                        .toList());
    }

    /**
     * Registrar, cobrar y anular se conceden por separado: cada permiso abre su operacion y ninguna
     * otra. El "no es 403" de la operacion propia basta: la venta 999999 no existe, asi que el
     * servicio responde con un error de negocio, pero la autorizacion ya dejo pasar.
     */
    @ParameterizedTest
    @CsvSource({
        Permiso.VENTAS_REGISTRAR + ",registrar",
        Permiso.VENTAS_COBRAR + ",cobrar",
        Permiso.VENTAS_ANULAR + ",anular"
    })
    void cadaPermisoDeVentaAbreSoloSuOperacion(String permiso, String propia) throws Exception {
        for (String operacion : java.util.List.of("registrar", "cobrar", "anular")) {
            var peticion = switch (operacion) {
                case "registrar" -> post("/api/v1/quesos/ventas")
                        .contentType(MediaType.APPLICATION_JSON).content(VENTA_VALIDA);
                case "cobrar" -> patch("/api/v1/quesos/ventas/999999/cobrada");
                default -> post("/api/v1/quesos/ventas/999999/anular")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"prueba\"}");
            };
            int estado = mockMvc().perform(peticion.with(vendedorCon(permiso))).andReturn().getResponse().getStatus();
            if (operacion.equals(propia)) {
                assertThat(estado).as("%s con %s", operacion, permiso).isNotEqualTo(403);
            } else {
                assertThat(estado).as("%s con %s", operacion, permiso).isEqualTo(403);
            }
        }
    }

    /** quesos.write ya no vende: solo registra lotes. */
    @Test
    void quesosWriteYaNoRegistraVentas() throws Exception {
        mockMvc().perform(post("/api/v1/quesos/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VENTA_VALIDA)
                        .with(vendedorCon(Permiso.QUESOS_WRITE)))
                .andExpect(status().isForbidden());
    }

    /** Quien vendia antes de V54 sigue pudiendo hacerlo con su rol estandar. */
    @Test
    void losRolesQueVendianConservanLasTresOperaciones() {
        for (String rol : java.util.List.of(Rol.SECRETARIA, Rol.VENTAS, Rol.ADMINISTRADOR)) {
            assertThat(MatrizPermisos.POR_ROL.get(rol))
                    .as("rol %s", rol)
                    .contains(Permiso.VENTAS_REGISTRAR, Permiso.VENTAS_COBRAR, Permiso.VENTAS_ANULAR);
        }
        assertThat(MatrizPermisos.POR_ROL.get(Rol.GERENCIA))
                .doesNotContain(Permiso.VENTAS_REGISTRAR, Permiso.VENTAS_COBRAR, Permiso.VENTAS_ANULAR);
    }

    /** Red de seguridad: ningun permiso declarado en Java puede faltar del catalogo de la matriz. */
    @Test
    void todoPermisoConcedidoAUnRolExisteEnElCatalogo() {
        Set<String> catalogo = MatrizPermisos.CATALOGO.keySet();
        MatrizPermisos.POR_ROL.forEach((rol, permisos) -> assertThat(catalogo)
                .as("permisos del rol %s", rol)
                .containsAll(permisos));
    }
}
