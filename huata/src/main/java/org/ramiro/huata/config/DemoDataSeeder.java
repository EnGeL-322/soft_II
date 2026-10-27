package org.ramiro.huata.config;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
import org.ramiro.huata.compras.CompraItemRequest;
import org.ramiro.huata.compras.CompraRequest;
import org.ramiro.huata.compras.CompraService;
import org.ramiro.huata.compras.ProveedorCompra;
import org.ramiro.huata.compras.ProveedorCompraRepository;
import org.ramiro.huata.inventario.MateriaPrima;
import org.ramiro.huata.inventario.MateriaPrimaRepository;
import org.ramiro.huata.inventario.UnidadMedida;
import org.ramiro.huata.liquidaciones.LiquidacionService;
import org.ramiro.huata.liquidaciones.TarifaLeche;
import org.ramiro.huata.liquidaciones.TarifaLecheRepository;
import org.ramiro.huata.notificaciones.NotificacionService;
import org.ramiro.huata.parametros.ParametroNegocio;
import org.ramiro.huata.parametros.ParametroNegocioRepository;
import org.ramiro.huata.productores.EstadoProductor;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.produccion.Producto;
import org.ramiro.huata.produccion.ProductoRepository;
import org.ramiro.huata.produccion.RecetaItem;
import org.ramiro.huata.produccion.RecetaQueso;
import org.ramiro.huata.produccion.RecetaQuesoRepository;
import org.ramiro.huata.produccion.TrabajadorProduccion;
import org.ramiro.huata.produccion.TrabajadorProduccionRepository;
import org.ramiro.huata.quesos.CondicionVenta;
import org.ramiro.huata.quesos.LoteQueso;
import org.ramiro.huata.quesos.LoteQuesoRequest;
import org.ramiro.huata.quesos.ManoObraItemRequest;
import org.ramiro.huata.quesos.MedioCobro;
import org.ramiro.huata.quesos.OrigenLeche;
import org.ramiro.huata.quesos.Queso;
import org.ramiro.huata.quesos.QueseriaService;
import org.ramiro.huata.quesos.QuesoRepository;
import org.ramiro.huata.quesos.TarifaQuesoRequest;
import org.ramiro.huata.quesos.TipoComprador;
import org.ramiro.huata.quesos.VentaQuesoRequest;
import org.ramiro.huata.recojo.EstadoRecojo;
import org.ramiro.huata.recojo.FuenteRecojo;
import org.ramiro.huata.recojo.Recojo;
import org.ramiro.huata.recojo.RecojoRepository;
import org.ramiro.huata.rutas.AsignacionRuta;
import org.ramiro.huata.rutas.AsignacionRutaRepository;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.rutas.RutaRepository;
import org.ramiro.huata.ubicaciones.UbicacionGps;
import org.ramiro.huata.ubicaciones.UbicacionGpsRepository;
import org.ramiro.huata.usuarios.CatalogoVistas;
import org.ramiro.huata.usuarios.MatrizPermisos;
import org.ramiro.huata.usuarios.Modulo;
import org.ramiro.huata.usuarios.Vista;
import org.ramiro.huata.usuarios.VistaAccion;
import org.ramiro.huata.usuarios.Permiso;
import org.ramiro.huata.usuarios.PermisoRepository;
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
    private final PermisoRepository permisoRepository;
    private final org.ramiro.huata.usuarios.ModuloRepository moduloRepository;
    private final org.ramiro.huata.usuarios.VistaRepository vistaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final RutaRepository rutaRepository;
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
    private final org.ramiro.huata.contabilidad.PlanCuentaRepository planCuentaRepository;
    private final MateriaPrimaRepository materiaPrimaRepository;
    private final ProveedorCompraRepository proveedorCompraRepository;
    private final CompraService compraService;
    private final RecetaQuesoRepository recetaQuesoRepository;
    private final ProductoRepository productoRepository;
    private final TrabajadorProduccionRepository trabajadorProduccionRepository;
    private final QueseriaService queseriaService;
    private final org.springframework.core.env.Environment environment;

    /** Semilla fija: los datos DEMO salen iguales en cada instalación limpia. */
    private final Random random = new Random(20260819L);

    @Override
    @Transactional
    public void run(String... args) {
        // Roles y permisos son catalogo del esquema y normalmente ya existen: los siembran
        // V2__roles_base.sql y V8__permisos_por_recurso.sql. Solo se crean aqui donde Flyway no
        // corre (perfil "test" sobre H2, cuyas migraciones estan en dialecto PostgreSQL).
        sembrarRolesYPermisosSiFaltan();

        // Plan de cuentas: catalogo del esquema igual que roles/permisos (V21__contabilidad_de_queseria.sql
        // en Postgres real); solo se crea aqui donde Flyway no corre (perfil "test" sobre H2).
        sembrarPlanCuentasSiFaltan();

        // Los tres quesos que antes eran el enum TipoQueso: en Postgres los crea V28 y aqui solo se
        // crean si faltan (perfil "test" sobre H2). Buscar antes de insertar es lo que evita la caida
        // por clave duplicada que ya hubo con IGV_PORCENTAJE (CLAUDE.md seccion 36.11).
        sembrarProductosSiFaltan();

        // La leche del acopio (V31): donde entra lo que planta recibe de las rutas. Igual que los
        // productos, solo se crea si falta (en Postgres la crea la migracion).
        sembrarLecheAcopioSiFalta();

        // Las cuentas van ANTES del centinela y se crean una a una si faltan.
        //
        // POR QUE NO DESPUES: el centinela era "ya hay usuarios, no siembres nada". Sobre una base
        // creada antes de que existieran los ocho roles, eso significaba salir en la primera linea y
        // no crear jamas las cuentas de los cinco roles nuevos: quien probara la aplicacion solo
        // podia entrar como administrador, acopiador o calidad, y concluia —con razon— que los
        // permisos por rol "no se ven". Crear solo lo que falta es idempotente y arregla tambien las
        // bases que ya estaban a medias.
        Usuario usuarioAcopiador = crearUsuariosDeDemoQueFalten();

        // El centinela del resto de datos DEMO es el padron de proveedores, no los usuarios: los
        // usuarios ya existen a estas alturas por la linea de arriba.
        if (productorRepository.count() > 0) {
            return; // ya sembrado (o base de datos persistente reutilizada)
        }

        // ---- Rutas y zonas ----
        // Una ruta por cada zona REAL del C.S. Huata: las mismas cuatro cuyo poligono verificado
        // carga V20 en zona_poligonos. Antes el seeder inventaba nombres de zona ("Quillallaka /
        // Santa Barbara", "I Collana", "Pancha Pampa"...) que no correspondian a ningun poligono,
        // asi que en el mapa de Rutas y movilidad los territorios dibujados y las rutas parecian
        // dos sistemas distintos. La zona sigue siendo texto libre (decision C-05): lo que se
        // corrige aqui es el DATO demo, no el modelo.
        Ruta r1 = crearRuta("Ruta COLLANA I", "COLLANA I", "Ruta norte, salida 05:30.");
        Ruta r2 = crearRuta("Ruta COLLANA II", "COLLANA II", "Ruta centro, entrega directa en planta.");
        Ruta r3 = crearRuta("Ruta FAON", "FAON", "Ruta este.");
        Ruta r4 = crearRuta("Ruta YASIN", "YASIN", "Ruta sur.");
        List<Ruta> rutas = List.of(r1, r2, r3, r4);

        // ---- Acopiadores: uno por ruta ----
        // Se les asigna la ruta aqui. Antes quedaban con ruta_id null y solo la migracion V16 se la
        // rellenaba a posteriori desde asignaciones_ruta, asi que en una base nueva las zonas se
        // veian sin ningun acopiador hasta que alguien lo editaba a mano.
        List<Acopiador> acopiadores = new ArrayList<>();
        acopiadores.add(crearAcopiador("Acopiador", "DEMO 1", usuarioAcopiador, r1));
        for (int i = 2; i <= rutas.size(); i++) {
            acopiadores.add(crearAcopiador("Acopiador", "DEMO " + i, null, rutas.get(i - 1)));
        }

        // ---- Proveedores: 18 repartidos entre las rutas ----
        // La zona del proveedor sale de su ruta, no de una lista aparte: si fueran dos fuentes
        // distintas volverian a divergir (que es justo lo que pasaba antes).
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
                    "Registro sintético DEMO · zona de referencia " + ruta.getZona()));
        }

        // "Proveedor Demo" (proveedor.demo@huata.pe) necesita un Productor propio para poder probar
        // /productores/me desde la app movil, igual que "Acopiador Demo 1" ya tiene su Acopiador
        // (linea de arriba, crearAcopiador). Sin esto la cuenta existe pero /me responde 422.
        usuarioRepository
                .findByUsernameIgnoreCaseAndActivoTrue("proveedor.demo@huata.pe")
                .ifPresent(usuarioProveedor -> {
                    Productor productorDemo = productores.get(0);
                    productorDemo.setUsuario(usuarioProveedor);
                    productorRepository.save(productorDemo);
                });

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
        crearParametroNegocio(
                "IGV_PORCENTAJE", "18", "Porcentaje de IGV aplicado a la venta de queso. Confirmar con contabilidad antes de uso oficial.");

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

        // ---- Contabilidad de queseria (V21): compras, lotes y ventas reales para que el libro
        // diario/mayor no aparezcan vacios. A diferencia de crearQueso() (tabla plana historica), esto
        // pasa por CompraService/QueseriaService, asi que genera los asientos automaticos de verdad.
        //
        // NO en el perfil "test": crea LoteQueso/VentaQueso/Compra reales y varios tests existentes
        // (QueseriaServiceTests.resumen(), lotesConStock()...) asumen que esos totales no tienen mas
        // datos que los que ellos mismos crean. En dev/prod/docker (perfil distinto de "test") si se
        // siembra, que es donde hace falta ver Contabilidad con contenido.
        if (!environment.acceptsProfiles(org.springframework.core.env.Profiles.of("test"))) {
            sembrarContabilidadDemo(hoy);
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
        // Tres decimales: con uno, 0.13-0.17 quedaba siempre en 0.1 o 0.2, fuera del rango estandar.
        control.setAcidez(redondear3(0.13 + random.nextDouble() * 0.04));
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

    /**
     * Una cuenta por rol, creando solo las que falten. Sin esto no hay forma de que otro integrante
     * pruebe un rol distinto al suyo, ni de verificar que un rol insuficiente recibe 403 (criterio
     * de salida de la Fase 1).
     *
     * @return la cuenta del acopiador, que el resto del sembrado necesita para asociarle un
     *     acopiador de campo.
     */
    private Usuario crearUsuariosDeDemoQueFalten() {
        crearUsuario("Administrador Huata", "admin@huata.pe", Set.of(rolExistente(Rol.ADMINISTRADOR)));
        crearUsuario("Responsable Calidad Demo", "calidad.demo@huata.pe", Set.of(rolExistente(Rol.CALIDAD)));
        crearUsuario("Secretaría Demo", "secretaria.demo@huata.pe", Set.of(rolExistente(Rol.SECRETARIA)));
        crearUsuario("Proveedor Demo", "proveedor.demo@huata.pe", Set.of(rolExistente(Rol.PROVEEDOR)));
        crearUsuario("Productor de Queso Demo", "productorqueso.demo@huata.pe", Set.of(rolExistente(Rol.PRODUCTOR_QUESO)));
        return crearUsuario("Acopiador Demo 1", "acopiador.demo@huata.pe", Set.of(rolExistente(Rol.ACOPIADOR)));
    }

    /**
     * Idempotente: si la cuenta ya existe se devuelve tal cual, sin tocarle la contrasena ni los
     * roles. Reasignarlos en cada arranque pisaria cualquier ajuste hecho desde la aplicacion, que
     * es justo lo que el modelo de permisos quiere permitir.
     */
    private Usuario crearUsuario(String nombreCompleto, String username, Set<Rol> roles) {
        Optional<Usuario> existente = usuarioRepository.findByUsernameIgnoreCaseAndActivoTrue(username);
        if (existente.isPresent()) {
            return existente.get();
        }
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

    private Acopiador crearAcopiador(String nombres, String apellidos, Usuario usuario, Ruta ruta) {
        Acopiador acopiador = new Acopiador();
        acopiador.setNombres(nombres);
        acopiador.setApellidos(apellidos);
        acopiador.setUsuario(usuario);
        acopiador.setRuta(ruta);
        return acopiadorRepository.save(acopiador);
    }

    private Productor crearProductor(
            String codigo, String nombres, String apellidos, Ruta ruta, EstadoProductor estado, String notas) {
        Productor productor = new Productor();
        productor.setCodigo(codigo);
        productor.setNombres(nombres);
        productor.setApellidos(apellidos);
        productor.setRuta(ruta);
        // Misma razon que en el acopiador: sin esto la zona del proveedor quedaba null en una base
        // nueva y solo la rellenaba la migracion V16 en las bases que ya existian.
        productor.setZonaPrincipal(ruta != null ? ruta.getZona() : null);
        productor.setUsuario(crearUsuario(
                nombres + " " + apellidos,
                "proveedor." + codigo.toLowerCase() + "@huata.pe",
                Set.of(rolExistente(Rol.PROVEEDOR))));
        productor.setEstado(estado);
        productor.setNotas(notas);
        return productorRepository.save(productor);
    }

    /**
     * Obtiene un rol del catalogo, creandolo si no existe.
     *
     * <p>En dev y en produccion los roles ya estan: los siembra V2__roles_base.sql y esto solo los
     * lee. El caso en que hay que crearlos es el perfil "test", que corre sobre H2 con
     * ddl-auto=create-drop y con Flyway apagado (las migraciones estan en dialecto PostgreSQL), asi
     * que alli nadie los ha insertado.
     *
     * <p>Se hace get-or-create y no un fallo duro justamente para no acoplar los tests unitarios a
     * que haya un PostgreSQL levantado. Quien comprueba que la migracion realmente siembra los roles
     * es MigracionesFlywayIT, contra PostgreSQL de verdad y con el seeder desactivado.
     */
    /**
     * Crea el catalogo de permisos y la matriz rol -> permiso solo si la tabla de permisos esta
     * vacia, es decir, solo donde Flyway no ha corrido. En dev, prod y en los tests con
     * Testcontainers no toca nada: alli manda V8, y la asignacion puede haberse ajustado despues
     * desde la aplicacion sin que este seeder deba revertirla.
     */
    private void sembrarRolesYPermisosSiFaltan() {
        if (permisoRepository.count() > 0) {
            return;
        }
        Map<String, Permiso> porCodigo = new LinkedHashMap<>();
        MatrizPermisos.CATALOGO.forEach((codigo, descripcion) -> {
            Permiso permiso = new Permiso();
            permiso.setCodigo(codigo);
            permiso.setRecurso(codigo.substring(0, codigo.indexOf('.')));
            permiso.setAccion(codigo.substring(codigo.indexOf('.') + 1));
            permiso.setDescripcion(descripcion);
            porCodigo.put(codigo, permisoRepository.save(permiso));
        });

        for (MatrizPermisos.DefinicionRol definicion : MatrizPermisos.ROLES) {
            Rol rol = rolRepository
                    .findByNombre(definicion.codigo())
                    .orElseGet(() -> new Rol(definicion.codigo(), definicion.etiqueta(), definicion.descripcion(),
                            definicion.accesos()));
            rol.setEtiqueta(definicion.etiqueta());
            rol.setDescripcion(definicion.descripcion());
            rol.setAccesos(definicion.accesos());
            rol.setSistema(true);
            rol.setOrden(definicion.orden());
            rol.setPermisos(MatrizPermisos.POR_ROL.get(definicion.codigo()).stream()
                    .map(porCodigo::get)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
            rolRepository.save(rol);
        }
        sembrarVistas(porCodigo);
    }

    /** Mismo catalogo que V46__accesos_dinamicos_por_vista.sql (ver CatalogoVistas). */
    private void sembrarVistas(Map<String, Permiso> porCodigo) {
        Map<String, Modulo> modulos = new LinkedHashMap<>();
        for (CatalogoVistas.DefModulo def : CatalogoVistas.MODULOS) {
            Modulo modulo = new Modulo();
            modulo.setCodigo(def.codigo());
            modulo.setNombre(def.nombre());
            modulo.setOrden(def.orden());
            modulos.put(def.codigo(), moduloRepository.save(modulo));
        }
        for (CatalogoVistas.DefVista def : CatalogoVistas.VISTAS) {
            Vista vista = new Vista();
            vista.setCodigo(def.codigo());
            vista.setNombre(def.nombre());
            vista.setDescripcion(def.descripcion());
            vista.setModulo(modulos.get(def.modulo()));
            vista.setRuta(def.ruta());
            vista.setIcono(def.icono());
            vista.setOrden(def.orden());
            vista.setPermisoAcceso(porCodigo.get(def.permisoAcceso()));
            for (CatalogoVistas.DefAccion defAccion : def.acciones()) {
                VistaAccion accion = new VistaAccion();
                accion.setVista(vista);
                accion.setPermiso(porCodigo.get(defAccion.permiso()));
                accion.setEtiqueta(defAccion.etiqueta());
                accion.setOrden(defAccion.orden());
                vista.getAcciones().add(accion);
            }
            vistaRepository.save(vista);
        }
    }

    /** Misma materia prima que V31__leche_del_acopio_en_inventario.sql. */
    private void sembrarLecheAcopioSiFalta() {
        if (materiaPrimaRepository.findFirstByLecheAcopioTrue().isPresent()) {
            return;
        }
        MateriaPrima leche = materiaPrimaRepository.findFirstByNombreIgnoreCase("Leche fresca").orElseGet(() -> {
            MateriaPrima nueva = new MateriaPrima();
            nueva.setNombre("Leche fresca");
            nueva.setUnidadMedida(UnidadMedida.LITRO);
            nueva.setCostoUnitarioReferencial(BigDecimal.valueOf(1.70));
            return nueva;
        });
        leche.setLecheAcopio(true);
        materiaPrimaRepository.save(leche);
    }

    /** Mismos productos que V28__productos_dinamicos.sql. */
    private void sembrarProductosSiFaltan() {
        for (String nombre : List.of("Queso fresco", "Queso paria", "Queso andino madurado")) {
            if (productoRepository.findByNombreIgnoreCase(nombre).isEmpty()) {
                Producto producto = new Producto();
                producto.setNombre(nombre);
                producto.setUnidadVenta("unidad");
                productoRepository.save(producto);
            }
        }
    }

    /** Mismos codigos PCGE que V21__contabilidad_de_queseria.sql: ver el comentario de esa migracion. */
    private void sembrarPlanCuentasSiFaltan() {
        if (planCuentaRepository.count() > 0) {
            return;
        }
        Object[][] plan = {
            {"1011", "Caja", org.ramiro.huata.contabilidad.TipoCuenta.ACTIVO},
            {"1041", "Cuentas corrientes operativas", org.ramiro.huata.contabilidad.TipoCuenta.ACTIVO},
            {"1212", "Facturas, boletas y otros comprobantes por cobrar - en cartera", org.ramiro.huata.contabilidad.TipoCuenta.ACTIVO},
            {"2111", "Productos terminados - manufacturados", org.ramiro.huata.contabilidad.TipoCuenta.ACTIVO},
            {"2311", "Productos en proceso - manufactura", org.ramiro.huata.contabilidad.TipoCuenta.ACTIVO},
            {"2411", "Materias primas para productos manufacturados", org.ramiro.huata.contabilidad.TipoCuenta.ACTIVO},
            {"2521", "Materiales auxiliares", org.ramiro.huata.contabilidad.TipoCuenta.ACTIVO},
            {"3331", "Maquinaria y equipo de explotacion", org.ramiro.huata.contabilidad.TipoCuenta.ACTIVO},
            {"3913", "Depreciacion acumulada - maquinaria", org.ramiro.huata.contabilidad.TipoCuenta.ACTIVO},
            {"40111", "IGV - Cuenta propia", org.ramiro.huata.contabilidad.TipoCuenta.PASIVO},
            {"4111", "Sueldos y salarios por pagar", org.ramiro.huata.contabilidad.TipoCuenta.PASIVO},
            {"4212", "Facturas, boletas y otros comprobantes por pagar - emitidas", org.ramiro.huata.contabilidad.TipoCuenta.PASIVO},
            {"5011", "Capital social", org.ramiro.huata.contabilidad.TipoCuenta.PATRIMONIO},
            {"5911", "Resultados acumulados - utilidades", org.ramiro.huata.contabilidad.TipoCuenta.PATRIMONIO},
            {"6021", "Compras - materias primas", org.ramiro.huata.contabilidad.TipoCuenta.GASTO},
            {"6121", "Variacion de existencias - materias primas", org.ramiro.huata.contabilidad.TipoCuenta.GASTO},
            {"6211", "Sueldos y salarios", org.ramiro.huata.contabilidad.TipoCuenta.GASTO},
            {"6361", "Energia electrica, agua y gas", org.ramiro.huata.contabilidad.TipoCuenta.GASTO},
            {"6921", "Costo de ventas - productos manufacturados", org.ramiro.huata.contabilidad.TipoCuenta.GASTO},
            {"7021", "Ventas - productos terminados", org.ramiro.huata.contabilidad.TipoCuenta.INGRESO},
        };
        for (Object[] fila : plan) {
            org.ramiro.huata.contabilidad.PlanCuenta cuenta = new org.ramiro.huata.contabilidad.PlanCuenta();
            cuenta.setCodigo((String) fila[0]);
            cuenta.setNombre((String) fila[1]);
            cuenta.setTipo((org.ramiro.huata.contabilidad.TipoCuenta) fila[2]);
            planCuentaRepository.save(cuenta);
        }
    }

    /**
     * Compras, lotes y ventas DEMO reales (no la tabla plana {@code Queso}), para que Contabilidad
     * (plan de cuentas, libro diario, libro mayor) tenga contenido: los asientos se generan solos al
     * pasar por {@link CompraService} y {@link QueseriaService}, igual que en produccion.
     */
    private void sembrarContabilidadDemo(LocalDate hoy) {
        MateriaPrima cuajo = new MateriaPrima();
        cuajo.setNombre("Cuajo DEMO");
        cuajo.setUnidadMedida(UnidadMedida.ML);
        cuajo.setCostoUnitarioReferencial(BigDecimal.valueOf(2.50));
        cuajo = materiaPrimaRepository.save(cuajo);

        MateriaPrima sal = new MateriaPrima();
        sal.setNombre("Sal DEMO");
        sal.setUnidadMedida(UnidadMedida.KG);
        sal.setCostoUnitarioReferencial(BigDecimal.valueOf(3.20));
        sal = materiaPrimaRepository.save(sal);

        ProveedorCompra proveedorCompra = new ProveedorCompra();
        proveedorCompra.setCodigo("PC-DEMO01");
        proveedorCompra.setNombre("Insumos Andinos SAC (DEMO)");
        proveedorCompra = proveedorCompraRepository.save(proveedorCompra);

        compraService.registrar(new CompraRequest(
                proveedorCompra.getId(), hoy.minusDays(6), "F001-DEMO-01", "Compra DEMO de insumos.",
                List.of(
                        new CompraItemRequest(cuajo.getId(), BigDecimal.valueOf(200), BigDecimal.valueOf(2.50)),
                        new CompraItemRequest(sal.getId(), BigDecimal.valueOf(20), BigDecimal.valueOf(3.20)))));
        compraService.registrar(new CompraRequest(
                proveedorCompra.getId(), hoy.minusDays(5), "F001-DEMO-02", "Compra DEMO de insumos.",
                List.of(
                        new CompraItemRequest(cuajo.getId(), BigDecimal.valueOf(100), BigDecimal.valueOf(2.60)),
                        new CompraItemRequest(sal.getId(), BigDecimal.valueOf(10), BigDecimal.valueOf(3.30)))));

        Producto quesoFresco = productoRepository.findByNombreIgnoreCase("Queso fresco").orElseThrow();
        RecetaQueso receta = new RecetaQueso();
        receta.setProducto(quesoFresco);
        receta.setVigenteDesde(hoy.minusYears(1));
        RecetaItem itemCuajo = new RecetaItem();
        itemCuajo.setReceta(receta);
        itemCuajo.setMateriaPrima(cuajo);
        itemCuajo.setCantidadPorUnidad(BigDecimal.ONE);
        RecetaItem itemSal = new RecetaItem();
        itemSal.setReceta(receta);
        itemSal.setMateriaPrima(sal);
        itemSal.setCantidadPorUnidad(BigDecimal.valueOf(0.05));
        receta.getItems().add(itemCuajo);
        receta.getItems().add(itemSal);
        receta = recetaQuesoRepository.save(receta);

        TrabajadorProduccion trabajador = new TrabajadorProduccion();
        trabajador.setNombre("Ana Ccama (DEMO)");
        trabajador = trabajadorProduccionRepository.save(trabajador);

        queseriaService.guardarTarifa(
                new TarifaQuesoRequest(quesoFresco.getId(), 20.0, 15.0, 13.0, hoy.minusYears(1), null, null, null));

        // El consumo (cuajo y sal) lo calcula el servidor con la receta. La receta DEMO no lleva leche
        // (se sembraria sin stock de leche en una base nueva), por eso los litros van a mano.
        LoteQueso lote1 = queseriaService.crearLote(new LoteQuesoRequest(
                hoy.minusDays(4), quesoFresco.getId(), OrigenLeche.ACOPIO_DEL_DIA, 200.0, 20, "Quesería turno A (DEMO)",
                "Lote DEMO para probar Contabilidad.", receta.getId(), 20,
                List.of(new ManoObraItemRequest(trabajador.getId(), BigDecimal.valueOf(80))),
                null, null, null));
        LoteQueso lote2 = queseriaService.crearLote(new LoteQuesoRequest(
                hoy.minusDays(2), quesoFresco.getId(), OrigenLeche.ACOPIO_DEL_DIA, 150.0, 15, "Quesería turno A (DEMO)",
                "Lote DEMO para probar Contabilidad.", receta.getId(), 15,
                List.of(new ManoObraItemRequest(trabajador.getId(), BigDecimal.valueOf(60))),
                1, 0, 0.0));

        // La venta ya no acepta fecha propia (siempre es HOY segun el servidor): las dos quedan
        // fechadas el dia en que corre el seeder, ya no una "hace 3 dias" simulada.
        queseriaService.registrarVenta(new VentaQuesoRequest(
                lote1.getId(), "Mercado Huata (DEMO)", 8, TipoComprador.PUBLICO_GENERAL,
                CondicionVenta.CONTADO, MedioCobro.EFECTIVO));
        queseriaService.registrarVenta(new VentaQuesoRequest(
                lote2.getId(), "Bodega Central (DEMO)", 6, TipoComprador.MAYORISTA,
                CondicionVenta.CREDITO_7D, MedioCobro.TRANSFERENCIA));
    }

    private Rol rolExistente(String nombre) {
        return rolRepository
                .findByNombre(nombre)
                .orElseThrow(() -> new IllegalStateException("Falta el rol " + nombre + " en el catalogo"));
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

    /**
     * No reinserta una clave que ya existe, igual que el {@code ON CONFLICT (clave) DO NOTHING} con el
     * que la siembran las migraciones.
     *
     * <p>Sin esto, un despliegue NUEVO con {@code HUATA_SEED_DEMO=true} no arrancaba: V1 ya siembra
     * {@code IGV_PORCENTAJE}, este metodo lo insertaba otra vez y la clave unica de
     * {@code parametros_negocio} abortaba el contexto. Y como {@code run(...)} es transaccional, el
     * fallo revertia el resto del sembrado, dejando {@code productores} vacio — asi que la guarda de
     * "ya sembrado" no protegia y el backend volvia a intentarlo y a caerse en cada reinicio.
     *
     * <p>No se veia en las pruebas porque el perfil "test" crea el esquema con Hibernate (sin correr
     * las migraciones) y {@code MigracionesFlywayIT} corre con los datos DEMO desactivados: los dos
     * unicos entornos que combinan Flyway con el seeder son dev y un despliegue real.
     */
    private void crearParametroNegocio(String clave, String valor, String descripcion) {
        if (parametroNegocioRepository.findByClave(clave).isPresent()) {
            return;
        }
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
