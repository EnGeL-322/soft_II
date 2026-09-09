package org.ramiro.huata.config;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.calidad.AguaAnadida;
import org.ramiro.huata.calidad.Aspecto;
import org.ramiro.huata.calidad.ControlCalidad;
import org.ramiro.huata.calidad.ControlCalidadRepository;
import org.ramiro.huata.calidad.EstadoIncidencia;
import org.ramiro.huata.calidad.IncidenciaCalidad;
import org.ramiro.huata.calidad.IncidenciaCalidadRepository;
import org.ramiro.huata.calidad.Olor;
import org.ramiro.huata.calidad.ParametroCalidad;
import org.ramiro.huata.calidad.ParametroCalidadRepository;
import org.ramiro.huata.calidad.ResultadoCalidad;
import org.ramiro.huata.calidad.ResultadoLugol;
import org.ramiro.huata.liquidaciones.LiquidacionService;
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
import org.ramiro.huata.recojo.FuenteRecojo;
import org.ramiro.huata.recojo.Recojo;
import org.ramiro.huata.recojo.RecojoRepository;
import org.ramiro.huata.rutas.AsignacionRuta;
import org.ramiro.huata.rutas.AsignacionRutaRepository;
import org.ramiro.huata.rutas.CentroAcopio;
import org.ramiro.huata.rutas.CentroAcopioRepository;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.rutas.RutaRepository;
import org.ramiro.huata.ubicaciones.UbicacionGps;
import org.ramiro.huata.ubicaciones.UbicacionGpsRepository;
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
 * Siembra un conjunto de datos DEMO completo (14 días de operación real: rutas, proveedores,
 * recepciones, calidad, incidencias, liquidaciones, quesos y GPS) para que todas las pantallas
 * tengan contenido con el que trabajar.
 *
 * <p>Solo corre cuando huata.seed.demo-data=true y la base está vacía. Todo dato sintético se
 * identifica como DEMO — misma convención del prototipo HTML — para no confundirlo jamás con datos
 * reales de la cooperativa (00_LEEME_PRIMERO.md).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "huata.seed", name = "demo-data", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private static final ZoneId ZONA_HORARIA = ZoneId.of("America/Lima");
    private static final int DIAS_DE_HISTORIA = 14;

    /** Coordenada aproximada del distrito de Huata (Puno) usada solo para ubicar puntos GPS DEMO. */
    private static final double LAT_BASE = -15.652;
    private static final double LON_BASE = -69.985;

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
    private final UbicacionGpsRepository ubicacionGpsRepository;
    private final LiquidacionService liquidacionService;

    /** Semilla fija: los datos DEMO salen iguales en cada instalación limpia. */
    private final Random random = new Random(20260819L);

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
                Rol.ACOPIADOR, "Registra recojos en ruta desde la aplicacion (movil en Fase 2).", "Recepción de ruta"));
        Rol calidadRol = rolRepository.save(new Rol(
                Rol.CALIDAD,
                "Registra densidad/adulteracion, resultado y observaciones; consulta historial.",
                "Calidad y consulta"));

        crearUsuario("Administrador Huata", "admin@huata.pe", Set.of(admin));
        Usuario usuarioAcopiador = crearUsuario("Acopiador Demo 1", "acopiador.demo@huata.pe", Set.of(acopiadorRol));
        crearUsuario("Responsable Calidad Demo", "calidad.demo@huata.pe", Set.of(calidadRol));

        // ---- Rutas y zonas (las 9 zonas históricas viven como texto configurable, no como enum) ----
        Ruta r1 = crearRuta("R-01", "Quillallaka / Santa Bárbara", "Ruta norte, salida 05:30.");
        Ruta r2 = crearRuta("R-02", "I Collana", "Ruta centro, entrega directa en planta.");
        Ruta r3 = crearRuta("R-03", "II Collana", "Ruta centro-sur.");
        Ruta r4 = crearRuta("R-04", "Faon y Yasin", "Ruta este, incluye Yasin Uchuy.");
        Ruta r5 = crearRuta("R-05", "Pancha Pampa", "Ruta sur, incluye Moro Viejo.");
        List<Ruta> rutas = List.of(r1, r2, r3, r4, r5);

        crearCentro("Centro Quillallaka", "Quillallaka, Huata", r1);
        crearCentro("Centro I Collana", "I Collana, Huata", r2);
        crearCentro("Centro II Collana", "II Collana, Huata", r3);
        crearCentro("Acopio Faon", "Faon y Yasin, Huata", r4);
        crearCentro("Planta Huata Lácteos", "Planta central, Huata", null);

        // ---- Acopiadores ----
        List<Acopiador> acopiadores = new ArrayList<>();
        acopiadores.add(crearAcopiador("Acopiador", "DEMO 1", usuarioAcopiador));
        for (int i = 2; i <= 5; i++) {
            acopiadores.add(crearAcopiador("Acopiador", "DEMO " + i, null));
        }

        // ---- Proveedores: 18 repartidos entre las rutas ----
        String[] zonasProveedor = {
            "Quillallaka", "Santa Bárbara", "I Collana", "II Collana", "Faon y Yasin",
            "Yasin Uchuy", "Pancha Pampa", "Moro Viejo", "Planta"
        };
        List<Productor> productores = new ArrayList<>();
        for (int i = 1; i <= 18; i++) {
            Ruta ruta = rutas.get((i - 1) % rutas.size());
            EstadoProductor estado = (i == 5 || i == 12) ? EstadoProductor.EN_REVISION : EstadoProductor.ACTIVO;
            productores.add(crearProductor(
                    String.format("DEMO-%03d", i),
                    "Proveedor",
                    "Demo " + (char) ('A' + ((i - 1) % 26)),
                    ruta,
                    estado,
                    "Registro sintético DEMO · zona de referencia " + zonasProveedor[(i - 1) % zonasProveedor.length]));
        }

        // ---- Parámetros de calidad (rangos de referencia configurables) ----
        crearParametroCalidad("Densidad", 1.0296, 1.0340, "g/mL a 15°C", "Referencia operativa DEMO; confirmar con Huata.");
        crearParametroCalidad("Punto de congelacion", -0.560, -0.520, "°C", "~-0.540 °C; se acerca a 0 si hay agua añadida.");
        crearParametroCalidad("Acidez", 0.13, 0.17, "%", "Rango de referencia DEMO.");
        crearParametroCalidad("Grasa", 3.2, null, "%", "Mínimo de referencia DEMO.");
        crearParametroCalidad("Solidos no grasos", 8.2, null, "%", "Mínimo de referencia DEMO.");
        crearParametroCalidad("Solidos totales", 11.4, null, "%", "Mínimo de referencia DEMO.");

        // ---- Tarifa vigente (valores del levantamiento; configurables, pendientes de confirmar) ----
        TarifaLeche tarifa = new TarifaLeche();
        tarifa.setPrecioNormal(1.70);
        tarifa.setPrecioObservada(1.30);
        tarifa.setVigenteDesde(LocalDate.now(ZONA_HORARIA).minusMonths(2));
        tarifaLecheRepository.save(tarifa);

        crearParametroNegocio(
                "TEMPERATURA_LIMITE_TRANSPORTE_C", "8", "Límite referencial DEMO; pendiente de confirmar con Huata (C-03).");
        crearParametroNegocio("CANAL_NOTIFICACIONES", "EMAIL", "Canal por defecto DEMO; falta confirmar email/push/ambos.");
        crearParametroNegocio("HORARIO_ACOPIO", "08:40-10:00", "Horario referencial DEMO de recepción en planta.");
        crearParametroNegocio("ENVASES_REFERENCIA_L", "22, 35", "Capacidades de envase usadas como referencia.");

        // ---- 14 días de operación: asignaciones, recepciones y controles de calidad ----
        LocalDate hoy = LocalDate.now(ZONA_HORARIA);
        List<Recojo> recojosObservados = new ArrayList<>();

        for (int diasAtras = DIAS_DE_HISTORIA; diasAtras >= 0; diasAtras--) {
            LocalDate dia = hoy.minusDays(diasAtras);

            for (int i = 0; i < rutas.size(); i++) {
                Ruta ruta = rutas.get(i);
                Acopiador acopiador = acopiadores.get(i % acopiadores.size());

                AsignacionRuta asignacion = new AsignacionRuta();
                asignacion.setRuta(ruta);
                asignacion.setAcopiador(acopiador);
                asignacion.setFecha(dia);
                asignacion.setMovilidad(i % 2 == 0 ? "Camioneta DEMO" : "Motocarro DEMO");
                asignacionRutaRepository.save(asignacion);

                List<Productor> deLaRuta = productores.stream()
                        .filter(p -> p.getRuta() != null && p.getRuta().getId().equals(ruta.getId()))
                        .filter(p -> p.getEstado() != EstadoProductor.INACTIVO)
                        .toList();

                for (Productor productor : deLaRuta) {
                    // Un proveedor no entrega absolutamente todos los días.
                    if (random.nextInt(10) < 2) {
                        continue;
                    }

                    double litros = redondear(18 + random.nextDouble() * 55);
                    Instant fechaHora = dia.atTime(LocalTime.of(6 + random.nextInt(4), random.nextInt(60)))
                            .atZone(ZONA_HORARIA)
                            .toInstant();

                    Recojo recojo = new Recojo();
                    recojo.setClientUuid(UUID.randomUUID().toString());
                    recojo.setProductor(productor);
                    recojo.setAcopiador(acopiador);
                    recojo.setRuta(ruta);
                    recojo.setFechaHora(fechaHora);
                    recojo.setLitros(litros);
                    recojo.setTemperatura(redondear(3.5 + random.nextDouble() * 2.5));
                    recojo.setEstado(EstadoRecojo.SYNCED);
                    recojo.setFuente(random.nextInt(10) < 8 ? FuenteRecojo.RUTA : FuenteRecojo.PLANTA);
                    recojo.setNumeroEnvases(1 + random.nextInt(3));
                    recojo.setCapacidadReferenciaL(random.nextBoolean() ? 35.0 : 22.0);
                    recojo.setResponsableRecepcion("Responsable planta DEMO");
                    recojoRepository.save(recojo);

                    // El día de hoy algunas recepciones quedan sin control de calidad (pendientes).
                    if (diasAtras == 0 && random.nextInt(10) < 3) {
                        continue;
                    }

                    ResultadoCalidad resultado = sortearResultado();
                    controlCalidadRepository.save(construirControl(recojo, resultado));
                    if (resultado != ResultadoCalidad.ACEPTADA) {
                        recojosObservados.add(recojo);
                    }
                }
            }
        }

        // ---- Incidencias de calidad sobre las entregas observadas/rechazadas ----
        int incidencias = 0;
        for (Recojo recojo : recojosObservados) {
            if (incidencias >= 6) {
                break;
            }
            IncidenciaCalidad incidencia = new IncidenciaCalidad();
            incidencia.setProductor(recojo.getProductor());
            incidencia.setRecojo(recojo);
            incidencia.setTipo(incidencias % 2 == 0 ? "Adulteracion" : "Baja calidad");
            incidencia.setDescripcion("Caso sintético DEMO para probar el seguimiento de incidencias.");
            incidencia.setFecha(recojo.getFechaHora());
            incidencia.setEstado(incidencias < 3 ? EstadoIncidencia.ABIERTA : EstadoIncidencia.EN_SEGUIMIENTO);
            IncidenciaCalidad guardada = incidenciaCalidadRepository.save(incidencia);
            if (incidencias == 0) {
                notificacionService.crear(
                        null,
                        "Incidencia de calidad registrada",
                        "Se registró una incidencia DEMO para " + recojo.getProductor().getCodigo() + ".",
                        "INCIDENCIA_CALIDAD",
                        guardada.getId());
            }
            incidencias++;
        }

        // ---- Producción de quesos ----
        for (int diasAtras = 12; diasAtras >= 0; diasAtras -= 3) {
            double producidos = redondear(15 + random.nextDouble() * 35);
            double vendidos = redondear(producidos * (0.5 + random.nextDouble() * 0.45));
            crearQueso(
                    hoy.minusDays(diasAtras),
                    random.nextBoolean() ? "Leche propia" : "Leche de acopio",
                    producidos,
                    vendidos,
                    diasAtras == 0 ? null : redondear(vendidos * 12.5),
                    diasAtras == 0 ? "Producción DEMO sin venta registrada todavía." : "Lote DEMO de producción.");
        }

        // ---- Liquidaciones de la semana anterior para los primeros proveedores ----
        LocalDate finSemana = hoy.minusDays(hoy.getDayOfWeek().getValue());
        LocalDate inicioSemana = finSemana.minusDays(6);
        for (int i = 0; i < 6 && i < productores.size(); i++) {
            try {
                liquidacionService.calcular(productores.get(i).getId(), inicioSemana, finSemana);
            } catch (RuntimeException ignorada) {
                // Un proveedor sin entregas en el periodo simplemente no genera liquidación DEMO.
            }
        }

        // ---- Recorrido GPS DEMO de hoy para el primer acopiador ----
        Acopiador acopiadorGps = acopiadores.get(0);
        for (int punto = 0; punto < 12; punto++) {
            UbicacionGps ubicacion = new UbicacionGps();
            ubicacion.setAcopiador(acopiadorGps);
            ubicacion.setLatitud(LAT_BASE + punto * 0.0016 + random.nextDouble() * 0.0004);
            ubicacion.setLongitud(LON_BASE + punto * 0.0021 + random.nextDouble() * 0.0004);
            ubicacion.setPrecisionMetros(redondear(6 + random.nextDouble() * 12));
            ubicacion.setVelocidadMs(redondear(2 + random.nextDouble() * 8));
            ubicacion.setFecha(Instant.now().minusSeconds((long) (12 - punto) * 300));
            ubicacionGpsRepository.save(ubicacion);
        }

        auditoriaService.registrar("Carga DEMO", "Proveedores", "Se sembraron 18 proveedores sintéticos en 5 rutas.");
        auditoriaService.registrar(
                "Carga DEMO", "Recepción", "Se poblaron " + DIAS_DE_HISTORIA + " días de recepciones y controles de calidad.");
        auditoriaService.registrar("Configuración", "Tarifas", "Precio normal S/ 1.70/L y precio de baja calidad S/ 1.30/L.");
        auditoriaService.registrar(
                "Protección de evidencia", "Calidad", "Las incidencias se asignaron solo a proveedores DEMO.");
    }

    private ResultadoCalidad sortearResultado() {
        int dado = random.nextInt(100);
        if (dado < 82) {
            return ResultadoCalidad.ACEPTADA;
        }
        return dado < 95 ? ResultadoCalidad.OBSERVADA : ResultadoCalidad.RECHAZADA;
    }

    private ControlCalidad construirControl(Recojo recojo, ResultadoCalidad resultado) {
        ControlCalidad control = new ControlCalidad();
        control.setRecojo(recojo);
        control.setResultado(resultado);
        control.setTemperatura(recojo.getTemperatura());
        control.setAcidez(redondear(0.13 + random.nextDouble() * 0.04));
        control.setGrasa(redondear(3.2 + random.nextDouble() * 0.8));
        control.setEquipoUtilizado("Lactodensímetro DEMO");
        control.setResponsable("Responsable Calidad Demo");

        switch (resultado) {
            case ACEPTADA -> {
                control.setDensidad(redondear4(1.0296 + random.nextDouble() * 0.0044));
                control.setPuntoCongelacion(redondear3(-0.545 + random.nextDouble() * 0.02));
                control.setAguaAnadida(AguaAnadida.NO_EVIDENCIADA);
                control.setLugol(ResultadoLugol.NEGATIVA);
                control.setOlor(Olor.NORMAL);
                control.setAspecto(Aspecto.NORMAL);
                control.setRevisionVisual("Aspecto normal, sin observaciones.");
            }
            case OBSERVADA -> {
                control.setDensidad(redondear4(1.0270 + random.nextDouble() * 0.0020));
                control.setPuntoCongelacion(redondear3(-0.500 + random.nextDouble() * 0.02));
                control.setAguaAnadida(AguaAnadida.SOSPECHA);
                control.setLugol(ResultadoLugol.NEGATIVA);
                control.setOlor(random.nextBoolean() ? Olor.NORMAL : Olor.AGRIO);
                control.setAspecto(Aspecto.NORMAL);
                control.setRevisionVisual("Densidad por debajo del rango; se aplica tarifa reducida.");
            }
            default -> {
                control.setDensidad(redondear4(1.0240 + random.nextDouble() * 0.0020));
                control.setPuntoCongelacion(redondear3(-0.450 + random.nextDouble() * 0.03));
                control.setAguaAnadida(AguaAnadida.CONFIRMADA);
                control.setLugol(ResultadoLugol.POSITIVA);
                control.setOlor(Olor.AGRIO);
                control.setAspecto(Aspecto.GRUMOS);
                control.setRevisionVisual("Olor agrio y presencia de grumos.");
                control.setMotivoRechazo("Agua añadida confirmada y lugol positivo.");
            }
        }
        return control;
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

    private void crearCentro(String nombre, String ubicacion, Ruta ruta) {
        CentroAcopio centro = new CentroAcopio();
        centro.setNombre(nombre);
        centro.setUbicacion(ubicacion);
        centro.setRuta(ruta);
        centroAcopioRepository.save(centro);
    }

    private Acopiador crearAcopiador(String nombres, String apellidos, Usuario usuario) {
        Acopiador acopiador = new Acopiador();
        acopiador.setNombres(nombres);
        acopiador.setApellidos(apellidos);
        acopiador.setUsuario(usuario);
        return acopiadorRepository.save(acopiador);
    }

    private Productor crearProductor(
            String codigo, String nombres, String apellidos, Ruta ruta, EstadoProductor estado, String notas) {
        Productor productor = new Productor();
        productor.setCodigo(codigo);
        productor.setNombres(nombres);
        productor.setApellidos(apellidos);
        productor.setRuta(ruta);
        productor.setEstado(estado);
        productor.setNotas(notas);
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

    private void crearQueso(
            LocalDate fecha, String fuente, double producidosKg, double vendidosKg, Double totalVenta, String observaciones) {
        Queso queso = new Queso();
        queso.setFecha(fecha);
        queso.setFuente(fuente);
        queso.setProducidosKg(producidosKg);
        queso.setVendidosKg(vendidosKg);
        queso.setTotalVenta(totalVenta);
        queso.setObservaciones(observaciones);
        quesoRepository.save(queso);
    }

    private double redondear(double valor) {
        return Math.round(valor * 10) / 10.0;
    }

    private double redondear3(double valor) {
        return Math.round(valor * 1000) / 1000.0;
    }

    private double redondear4(double valor) {
        return Math.round(valor * 10000) / 10000.0;
    }
}
