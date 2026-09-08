package org.ramiro.huata.config;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.calidad.ControlCalidad;
import org.ramiro.huata.calidad.ControlCalidadRepository;
import org.ramiro.huata.calidad.EstadoIncidencia;
import org.ramiro.huata.calidad.IncidenciaCalidad;
import org.ramiro.huata.calidad.IncidenciaCalidadRepository;
import org.ramiro.huata.calidad.ParametroCalidad;
import org.ramiro.huata.calidad.ParametroCalidadRepository;
import org.ramiro.huata.calidad.ResultadoCalidad;
import org.ramiro.huata.liquidaciones.TarifaLeche;
import org.ramiro.huata.liquidaciones.TarifaLecheRepository;
import org.ramiro.huata.notificaciones.NotificacionService;
import org.ramiro.huata.parametros.ParametroNegocio;
import org.ramiro.huata.parametros.ParametroNegocioRepository;
import org.ramiro.huata.productores.EstadoProductor;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.quesos.Queso;
import org.ramiro.huata.quesos.QuesoRepository;
import org.ramiro.huata.recojo.EstadoRecojo;
import org.ramiro.huata.recojo.Recojo;
import org.ramiro.huata.recojo.RecojoRepository;
import org.ramiro.huata.rutas.AsignacionRuta;
import org.ramiro.huata.rutas.AsignacionRutaRepository;
import org.ramiro.huata.rutas.CentroAcopio;
import org.ramiro.huata.rutas.CentroAcopioRepository;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.rutas.RutaRepository;
import org.ramiro.huata.usuarios.Rol;
import org.ramiro.huata.usuarios.RolRepository;
import org.ramiro.huata.usuarios.Usuario;
import org.ramiro.huata.usuarios.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Siembra datos DEMO solo cuando huata.seed.demo-data=true (perfil dev por defecto; jamas en
 * postgres/produccion salvo que se fuerce explicitamente). Sigue la misma convencion que ya usa
 * el prototipo HTML de Huata: todo dato sintetico se identifica como "DEMO" para no confundirlo
 * jamas con datos reales de la cooperativa (ver 00_LEEME_PRIMERO.md).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "huata.seed", name = "demo-data", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private final RolRepository rolRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final RutaRepository rutaRepository;
    private final CentroAcopioRepository centroAcopioRepository;
    private final AcopiadorRepository acopiadorRepository;
    private final AsignacionRutaRepository asignacionRutaRepository;
    private final ProductorRepository productorRepository;
    private final RecojoRepository recojoRepository;
    private final ParametroCalidadRepository parametroCalidadRepository;
    private final ControlCalidadRepository controlCalidadRepository;
    private final IncidenciaCalidadRepository incidenciaCalidadRepository;
    private final TarifaLecheRepository tarifaLecheRepository;
    private final ParametroNegocioRepository parametroNegocioRepository;
    private final NotificacionService notificacionService;
    private final QuesoRepository quesoRepository;
    private final AuditoriaService auditoriaService;

    @Override
    @Transactional
    public void run(String... args) {
        if (rolRepository.count() > 0) {
            return; // ya sembrado (o base de datos persistente reutilizada)
        }

        Rol admin = rolRepository.save(new Rol(
                Rol.ADMINISTRADOR,
                "Administra el sistema; supervisa y consulta, no duplica el registro de campo/planta.",
                "Todo el sistema"));
        Rol acopiadorRol = rolRepository.save(new Rol(
                Rol.ACOPIADOR,
                "Registra recojos en ruta desde la aplicacion (movil en Fase 2).",
                "Recepción de ruta"));
        Rol calidadRol = rolRepository.save(new Rol(
                Rol.CALIDAD,
                "Registra densidad/adulteracion, resultado y observaciones; consulta historial.",
                "Calidad y consulta"));

        Usuario usuarioAdmin = crearUsuario("Administrador Huata", "admin@huata.pe", Set.of(admin));
        Usuario usuarioAcopiador = crearUsuario("Acopiador Demo A", "acopiador.demo@huata.pe", Set.of(acopiadorRol));
        crearUsuario("Responsable Calidad Demo", "calidad.demo@huata.pe", Set.of(calidadRol));

        Ruta ruta1 = crearRuta("Ruta 1", "Quillallaka", "Ruta DEMO de referencia (validar contra rutas reales de Huata).");
        Ruta ruta2 = crearRuta("Ruta 2", "I Collana", "Ruta DEMO de referencia (validar contra rutas reales de Huata).");

        CentroAcopio centro1 = crearCentro("Centro Quillallaka", "Quillallaka, Huata", ruta1);
        crearCentro("Centro I Collana", "I Collana, Huata", ruta2);

        Acopiador acopiadorDemoA = crearAcopiador("Demo", "Acopiador A", usuarioAcopiador);
        crearAcopiador("Demo", "Acopiador B", null);

        AsignacionRuta asignacion = new AsignacionRuta();
        asignacion.setRuta(ruta1);
        asignacion.setAcopiador(acopiadorDemoA);
        asignacion.setFecha(LocalDate.now());
        asignacionRutaRepository.save(asignacion);

        Productor productorA = crearProductor("DEMO-001", "Proveedor", "Demo A", ruta1, centro1, EstadoProductor.ACTIVO);
        Productor productorB = crearProductor("DEMO-002", "Proveedor", "Demo B", ruta1, centro1, EstadoProductor.ACTIVO);
        Productor productorC = crearProductor("DEMO-003", "Proveedor", "Demo C", ruta2, null, EstadoProductor.EN_REVISION);

        crearParametroCalidad("Densidad", 1.0296, 1.0340, "g/mL a 15°C", "Referencia operativa DEMO (seccion 5 del contexto); confirmar con Huata antes de produccion.");
        crearParametroCalidad("Punto de congelacion", -0.560, -0.520, "°C", "Valor de referencia ~-0.540°C; se mueve hacia 0°C si se agrega agua.");
        crearParametroCalidad("Acidez", 0.13, 0.17, "%", "Rango de referencia DEMO.");
        crearParametroCalidad("Grasa", 3.2, null, "%", "Minimo de referencia DEMO.");
        crearParametroCalidad("Solidos no grasos", 8.2, null, "%", "Minimo de referencia DEMO.");
        crearParametroCalidad("Solidos totales", 11.4, null, "%", "Minimo de referencia DEMO.");

        TarifaLeche tarifa = new TarifaLeche();
        tarifa.setPrecioNormal(1.75);
        tarifa.setPrecioObservada(1.30);
        tarifa.setVigenteDesde(LocalDate.of(2026, 8, 19));
        tarifaLecheRepository.save(tarifa);

        crearParametroNegocio(
                "TEMPERATURA_LIMITE_TRANSPORTE_C", "8",
                "Limite referencial DEMO de temperatura de transporte; pendiente de confirmar con Huata (C-03).");
        crearParametroNegocio(
                "CANAL_NOTIFICACIONES", "EMAIL",
                "Canal por defecto DEMO; falta confirmar si sera email, push o ambos.");

        Recojo recojo1 = registrarRecojo(productorA, acopiadorDemoA, ruta1, 5, 68.5, EstadoRecojo.SYNCED);
        Recojo recojo2 = registrarRecojo(productorB, acopiadorDemoA, ruta1, 4, 54.0, EstadoRecojo.SYNCED);
        Recojo recojo3 = registrarRecojo(productorC, acopiadorDemoA, ruta2, 3, 39.5, EstadoRecojo.SYNCED);
        registrarRecojo(productorA, acopiadorDemoA, ruta1, 1, 71.0, EstadoRecojo.SYNCED);

        guardarControl(recojo1, ResultadoCalidad.ACEPTADA, "Aspecto normal, sin observaciones.", 1.0310, -0.535);
        guardarControl(recojo3, ResultadoCalidad.OBSERVADA, "Olor agrio y presencia de grumos.", 1.0270, -0.480);

        IncidenciaCalidad incidencia = new IncidenciaCalidad();
        incidencia.setProductor(productorC);
        incidencia.setRecojo(recojo3);
        incidencia.setTipo("Adulteracion");
        incidencia.setDescripcion("Caso sintetico DEMO para probar el flujo de seguimiento de incidencias.");
        incidencia.setFecha(Instant.now());
        incidencia.setEstado(EstadoIncidencia.ABIERTA);
        IncidenciaCalidad incidenciaGuardada = incidenciaCalidadRepository.save(incidencia);

        notificacionService.crear(
                null,
                "Incidencia de calidad registrada",
                "Se registro una incidencia DEMO para " + productorC.getCodigo() + " (" + productorC.getNombres() + ").",
                "INCIDENCIA_CALIDAD",
                incidenciaGuardada.getId());

        crearQueso(LocalDate.now().minusDays(3), "Leche propia", 42.0, 30.0, 450.0, "Lote DEMO para probar el modulo de produccion de quesos.");
        crearQueso(LocalDate.now().minusDays(1), "Leche propia", 18.5, 0.0, null, "Produccion DEMO sin venta registrada todavia.");

        auditoriaService.registrar(
                "Carga DEMO", "Calidad", "Se sembraron controles e incidencias sinteticos identificados como DEMO.");
        auditoriaService.registrar(
                "Carga DEMO", "Reportes", "Se completaron datos DEMO para dashboard, reportes y pruebas de interfaz.");
    }

    private void crearQueso(LocalDate fecha, String fuente, double producidosKg, double vendidosKg, Double totalVenta, String observaciones) {
        Queso queso = new Queso();
        queso.setFecha(fecha);
        queso.setFuente(fuente);
        queso.setProducidosKg(producidosKg);
        queso.setVendidosKg(vendidosKg);
        queso.setTotalVenta(totalVenta);
        queso.setObservaciones(observaciones);
        quesoRepository.save(queso);
    }

    private Usuario crearUsuario(String nombreCompleto, String username, Set<Rol> roles) {
        Usuario usuario = new Usuario();
        usuario.setNombreCompleto(nombreCompleto);
        usuario.setUsername(username);
        usuario.setEmail(username);
        usuario.setPassword(passwordEncoder.encode("Huata2026*"));
        usuario.setRoles(roles);
        return usuarioRepository.save(usuario);
    }

    private Ruta crearRuta(String nombre, String zona, String descripcion) {
        Ruta ruta = new Ruta();
        ruta.setNombre(nombre);
        ruta.setZona(zona);
        ruta.setDescripcion(descripcion);
        return rutaRepository.save(ruta);
    }

    private CentroAcopio crearCentro(String nombre, String ubicacion, Ruta ruta) {
        CentroAcopio centro = new CentroAcopio();
        centro.setNombre(nombre);
        centro.setUbicacion(ubicacion);
        centro.setRuta(ruta);
        return centroAcopioRepository.save(centro);
    }

    private Acopiador crearAcopiador(String nombres, String apellidos, Usuario usuario) {
        Acopiador acopiador = new Acopiador();
        acopiador.setNombres(nombres);
        acopiador.setApellidos(apellidos);
        acopiador.setUsuario(usuario);
        return acopiadorRepository.save(acopiador);
    }

    private Productor crearProductor(String codigo, String nombres, String apellidos, Ruta ruta, CentroAcopio centro, EstadoProductor estado) {
        Productor productor = new Productor();
        productor.setCodigo(codigo);
        productor.setNombres(nombres);
        productor.setApellidos(apellidos);
        productor.setRuta(ruta);
        productor.setCentroAcopio(centro);
        productor.setEstado(estado);
        productor.setNotas("Registro sintetico DEMO para probar recojos, calidad y liquidaciones.");
        return productorRepository.save(productor);
    }

    private void crearParametroCalidad(String nombre, Double min, Double max, String unidad, String descripcion) {
        ParametroCalidad parametro = new ParametroCalidad();
        parametro.setNombre(nombre);
        parametro.setValorMin(min);
        parametro.setValorMax(max);
        parametro.setUnidad(unidad);
        parametro.setDescripcion(descripcion);
        parametroCalidadRepository.save(parametro);
    }

    private void crearParametroNegocio(String clave, String valor, String descripcion) {
        ParametroNegocio parametro = new ParametroNegocio();
        parametro.setClave(clave);
        parametro.setValor(valor);
        parametro.setDescripcion(descripcion);
        parametroNegocioRepository.save(parametro);
    }

    private Recojo registrarRecojo(Productor productor, Acopiador acopiador, Ruta ruta, int diasAtras, double litros, EstadoRecojo estado) {
        Recojo recojo = new Recojo();
        recojo.setClientUuid(UUID.randomUUID().toString());
        recojo.setProductor(productor);
        recojo.setAcopiador(acopiador);
        recojo.setRuta(ruta);
        recojo.setFechaHora(Instant.now().minus(diasAtras, ChronoUnit.DAYS));
        recojo.setLitros(litros);
        recojo.setTemperatura(4.5);
        recojo.setEstado(estado);
        return recojoRepository.save(recojo);
    }

    private void guardarControl(Recojo recojo, ResultadoCalidad resultado, String revisionVisual, double densidad, double puntoCongelacion) {
        ControlCalidad control = new ControlCalidad();
        control.setRecojo(recojo);
        control.setResultado(resultado);
        control.setRevisionVisual(revisionVisual);
        control.setDensidad(densidad);
        control.setPuntoCongelacion(puntoCongelacion);
        control.setAcidez(0.15);
        control.setTemperatura(recojo.getTemperatura());
        control.setEquipoUtilizado("Crioscopio DEMO");
        control.setResponsable("Responsable Calidad Demo");
        controlCalidadRepository.save(control);
    }
}
