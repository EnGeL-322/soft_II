package org.ramiro.huata.productores;

import jakarta.persistence.criteria.JoinType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.DatosPersonales;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.common.PageResponse;
import org.ramiro.huata.common.SearchSpecifications;
import org.ramiro.huata.rutas.RutaRepository;
import org.ramiro.huata.usuarios.Rol;
import org.ramiro.huata.usuarios.UsuarioService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductorService {

    private final ProductorRepository productorRepository;
    private final RutaRepository rutaRepository;
    private final AcopiadorRepository acopiadorRepository;
    private final UsuarioService usuarioService;

    /**
     * Toda escritura sobre un proveedor queda trazada. No es opcional: el padron decide a quien se
     * le paga y con que tarifa, asi que un cambio de estado sin autor registrado es un agujero de
     * responsabilidad, no un detalle de implementacion (DESARROLLO.md, criterio de salida Fase 1).
     */
    private final AuditoriaService auditoriaService;

    /** Solo demostraciones: vivienda al azar dentro de su zona (apagado salvo que se configure). */
    private final UbicacionSimuladaService ubicacionSimulada;

    private static final String ENTIDAD = "Proveedores";

    /** Filtros del padron (pestana Proveedores de Usuarios). Todo es opcional. */
    public record Filtro(String q, EstadoProductor estado, Long rutaId, String zona, Long acopiadorId) {

        public static Filtro texto(String q) {
            return new Filtro(q, null, null, null, null);
        }
    }

    public PageResponse<Productor> buscar(Filtro filtro, Pageable pageable) {
        Specification<Productor> spec = SearchSpecifications.<Productor>buscarPalabras(filtro.q(), "codigo", "documentoIdentidad",
                        "nombres", "apellidos", "telefono", "zonaPrincipal", "ruta.nombre", "ruta.zona",
                        "usuario.username")
                .and(SearchSpecifications.onlyActive())
                .and(SearchSpecifications.equalsIfPresent("estado", filtro.estado()))
                .and(SearchSpecifications.equalsPathIfPresent(filtro.rutaId(), "ruta", "id"))
                .and(SearchSpecifications.equalsIgnoreCaseIfPresent(filtro.zona(), "ruta", "zona"))
                .and(deAcopiador(filtro.acopiadorId()));
        return PageResponse.from(productorRepository.findAll(spec, pageable));
    }

    /**
     * Proveedores que recoge un acopiador: los que lo tienen como responsable directo y los de su ruta
     * que no tienen responsable directo (los recoge el acopiador de la ruta).
     */
    private Specification<Productor> deAcopiador(Long acopiadorId) {
        if (acopiadorId == null) {
            return SearchSpecifications.alwaysTrue();
        }
        Long rutaDelAcopiador = acopiadorRepository.findById(acopiadorId)
                .map(a -> a.getRuta() != null ? a.getRuta().getId() : null)
                .orElse(null);
        return (root, cq, cb) -> {
            var responsable = root.join("acopiadorResponsable", JoinType.LEFT);
            var directo = cb.equal(responsable.get("id"), acopiadorId);
            if (rutaDelAcopiador == null) {
                return directo;
            }
            var porRuta = cb.and(cb.isNull(responsable.get("id")), cb.equal(root.get("ruta").get("id"), rutaDelAcopiador));
            return cb.or(directo, porRuta);
        };
    }

    public Productor obtener(Long id) {
        return productorRepository
                .findById(id)
                .filter(Productor::isActivo)
                .orElseThrow(() -> NotFoundException.of("Productor", id));
    }

    @Transactional
    public Productor crear(ProductorRequest req) {
        // El codigo del proveedor ES su DNI, asi que la unicidad del codigo y la del documento son la
        // misma comprobacion. Se valida aqui y no solo con anotaciones porque es una regla de negocio:
        // un DNI repetido significa dos fichas para la misma persona, y su liquidacion se parte en dos.
        String dni = dniNormalizado(req);
        if (dni != null && (productorRepository.existsByCodigoIgnoreCase(dni) || productorRepository.existsByDocumentoIdentidad(dni))) {
            throw new BusinessRuleException("Ya existe un proveedor registrado con el DNI " + dni + ".");
        }
        Productor productor = new Productor();
        // Nace ACTIVO (decision del cliente, 2026-10-26): quien lo da de alta ya lo revisa, no hay
        // un segundo paso de aprobacion. Los datos se validan igual (@Valid y las reglas de abajo).
        productor.setEstado(EstadoProductor.inicial());
        aplicarDatos(productor, req);
        sincronizarCuenta(productor, req);
        Productor guardado = productorRepository.save(productor);
        auditoriaService.registrar("Alta", ENTIDAD, describir(guardado));
        ubicacionSimulada.simularVivienda(guardado);
        return guardado;
    }

    @Transactional
    public Productor actualizar(Long id, ProductorRequest req) {
        Productor productor = obtener(id);
        String dni = dniNormalizado(req);
        // Al editar se excluye la propia ficha: guardar sin cambiar el DNI no puede chocar consigo misma.
        if (dni != null && productorRepository.existsByDocumentoIdentidadAndIdNot(dni, id)) {
            throw new BusinessRuleException("Otro proveedor ya está registrado con el DNI " + dni + ".");
        }
        String zonaAnterior = productor.getZonaPrincipal();
        aplicarDatos(productor, req);
        sincronizarCuenta(productor, req);
        Productor guardado = productorRepository.save(productor);
        auditoriaService.registrar("Modificacion", ENTIDAD, describir(guardado));
        ubicacionSimulada.alCambiarDeZona(guardado, zonaAnterior);
        return guardado;
    }

    @Transactional
    public Productor cambiarEstado(Long id, ProductorEstadoRequest req) {
        Productor productor = obtener(id);
        // Se lee ANTES de sobrescribirlo: el registro tiene que decir de donde venia, no solo a
        // donde fue. "ACTIVO -> INACTIVO" es informacion; "INACTIVO" a secas, casi ninguna.
        EstadoProductor anterior = productor.getEstado();
        exigirTransicionValida(anterior, req.estado());
        String motivo = exigirMotivo(req.motivo(), "estado");
        if (anterior == EstadoProductor.PENDIENTE_APROBACION && req.estado() == EstadoProductor.ACTIVO) {
            exigirListoParaActivar(productor);
        }

        productor.setEstado(req.estado());
        String notaPrevia = productor.getNotas() == null ? "" : productor.getNotas() + "\n";
        productor.setNotas(notaPrevia + "[" + req.estado() + "] " + motivo);
        Productor guardado = productorRepository.save(productor);
        auditoriaService.registrar(
                "Cambio de estado",
                ENTIDAD,
                describir(guardado) + " \u00b7 " + anterior + " -> " + req.estado() + " \u00b7 " + motivo);
        return guardado;
    }

    /**
     * Aprobar un alta es decir "desde hoy le recogemos la leche". Antes se podia aprobar a alguien sin
     * ruta —el acopiador nunca lo veia— o con un DNI que no cumple la regla, y al intentar corregirlo
     * despues el formulario lo rechazaba. Ahora se dice todo lo que falta de una vez, antes de aprobar.
     */
    private static void exigirListoParaActivar(Productor productor) {
        java.util.List<String> faltan = new java.util.ArrayList<>();
        // El DNI es opcional; solo se pide corregirlo si se escribio uno que no es valido.
        String dni = productor.getDocumentoIdentidad();
        if (dni != null && !dni.isBlank() && !org.ramiro.huata.common.DatosPersonales.esDniValido(dni)) {
            faltan.add("un DNI válido de 8 dígitos (o dejarlo vacío)");
        }
        if (productor.getNombres() == null || !productor.getNombres().trim().matches(
                org.ramiro.huata.common.DatosPersonales.NOMBRE_REGEX)) {
            faltan.add("nombres sin números ni símbolos (al menos 2 letras)");
        }
        if (productor.getApellidos() == null || !productor.getApellidos().trim().matches(
                org.ramiro.huata.common.DatosPersonales.NOMBRE_REGEX)) {
            faltan.add("apellidos sin números ni símbolos (al menos 2 letras)");
        }
        if (productor.getRuta() == null || !productor.getRuta().isActivo()) {
            faltan.add("una ruta asignada (sin ruta ningún acopiador lo visita)");
        }
        if (!faltan.isEmpty()) {
            throw new BusinessRuleException("Para aprobar a " + describir(productor) + " falta: "
                    + String.join("; ", faltan) + ". Complételo con \"Editar\" y vuelva a aprobar.");
        }
    }

    /**
     * Concede o retira la marca de Excelencia (7.4). Va por su propio metodo y no por
     * {@code actualizar} porque no es un dato del proveedor que se corrija al editarlo: es una
     * decision administrativa que cambia lo que se le paga, y como tal exige motivo y se audita.
     */
    @Transactional
    public Productor cambiarExcelencia(Long id, ProductorExcelenciaRequest req) {
        Productor productor = obtener(id);
        if (productor.isExcelencia() == req.excelencia()) {
            throw new BusinessRuleException(
                    "El proveedor ya " + (req.excelencia() ? "tiene" : "no tiene") + " la marca de Excelencia");
        }
        String motivo = exigirMotivo(req.motivo(), "la marca de Excelencia");
        productor.setExcelencia(req.excelencia());
        Productor guardado = productorRepository.save(productor);
        auditoriaService.registrar(
                req.excelencia() ? "Excelencia concedida" : "Excelencia retirada",
                ENTIDAD,
                describir(guardado) + " \u00b7 " + motivo);
        return guardado;
    }

    /**
     * Trae o mueve a un proveedor a una ruta desde "Rutas y mapa", igual que se hace con los
     * acopiadores. Va aparte de {@link #actualizar} a proposito: esa exige la ficha completa (DNI de
     * 8 digitos incluido), y un proveedor heredado sin DNI valido no podria cambiar de ruta hasta que
     * alguien completara su ficha. Cambiar de ruta no toca sus datos personales.
     *
     * <p>Misma regla que la aprobacion de un cambio de zona: la zona del proveedor sale de la ruta, y si
     * su acopiador responsable no recorre la ruta nueva, pasa a ser el primero de esa ruta.
     */
    @Transactional
    public Productor asignarRuta(Long id, ProductorRutaRequest req) {
        Productor productor = obtener(id);
        if (req.rutaId() == null) {
            throw new BusinessRuleException("Selecciona la ruta del proveedor");
        }
        if (req.ordenEnRuta() != null && req.ordenEnRuta() < 1) {
            throw new BusinessRuleException("El orden en la ruta empieza en 1");
        }
        var ruta = rutaRepository
                .findById(req.rutaId())
                .filter(r -> r.isActivo())
                .orElseThrow(() -> NotFoundException.of("Ruta", req.rutaId()));
        if (ruta.getZona() == null || ruta.getZona().isBlank()) {
            throw new BusinessRuleException("La ruta " + ruta.getNombre() + " no tiene una zona configurada");
        }
        var rutaAnterior = productor.getRuta();
        String zonaAnterior = productor.getZonaPrincipal();
        boolean mismaRuta = rutaAnterior != null && rutaAnterior.getId().equals(ruta.getId());
        if (mismaRuta && java.util.Objects.equals(productor.getOrdenEnRuta(), req.ordenEnRuta())) {
            return productor;
        }
        productor.setRuta(ruta);
        productor.setZonaPrincipal(ruta.getZona().trim());
        productor.setOrdenEnRuta(req.ordenEnRuta());
        var responsable = productor.getAcopiadorResponsable();
        if (responsable == null || responsable.getRuta() == null || !responsable.getRuta().getId().equals(ruta.getId())) {
            productor.setAcopiadorResponsable(acopiadorRepository
                    .findByRuta_IdAndActivoTrueOrderByNombresAsc(ruta.getId())
                    .stream()
                    .findFirst()
                    .orElse(null));
        }
        Productor guardado = productorRepository.save(productor);
        String desde = rutaAnterior == null ? "sin ruta" : rutaAnterior.getNombre();
        auditoriaService.registrar(
                "Cambio de ruta",
                ENTIDAD,
                describir(guardado) + " · " + desde + " -> " + ruta.getNombre() + " (" + ruta.getZona().trim() + ")"
                        + (req.ordenEnRuta() == null ? "" : " · orden " + req.ordenEnRuta()));
        ubicacionSimulada.alCambiarDeZona(guardado, zonaAnterior);
        return guardado;
    }

    @Transactional
    public void inactivar(Long id) {
        Productor productor = obtener(id);
        productor.setActivo(false);
        if (productor.getUsuario() != null) {
            usuarioService.inactivar(productor.getUsuario().getId());
        }
        productorRepository.save(productor);
        auditoriaService.registrar("Inactivacion", ENTIDAD, describir(productor));
    }

    /**
     * Unica definicion de "este proveedor puede entregar leche" (7.3). La usan tanto el registro
     * manual de recepcion como la ingesta automatica del equipo de medicion: si cada una llevara su
     * propia comprobacion, bastaria con olvidarse en un sitio para que la regla dejara de existir.
     *
     * @throws BusinessRuleException si el proveedor no esta ACTIVO.
     */
    public void exigirQuePuedeEntregar(Productor productor) {
        if (!productor.getEstado().puedeEntregar()) {
            throw new BusinessRuleException("El proveedor " + productor.getCodigo() + " no puede entregar leche: esta "
                    + productor.getEstado() + ". Solo un proveedor ACTIVO recibe recepciones.");
        }
    }

    /**
     * El estado no se asigna, se transita. Permitir cualquier salto convierte el enum en una
     * etiqueta de texto: se podria reactivar a un retirado o dar por aprobada un alta ya inactiva
     * sin que nadie lo note.
     */
    private static void exigirTransicionValida(EstadoProductor desde, EstadoProductor hasta) {
        if (hasta == null) {
            throw new BusinessRuleException("Falta el estado de destino");
        }
        if (desde == hasta) {
            throw new BusinessRuleException("El proveedor ya esta " + hasta);
        }
        if (!desde.permiteIr(hasta)) {
            String posibles = desde.destinosPosibles().isEmpty()
                    ? "ninguno, es un estado final"
                    : desde.destinosPosibles().toString();
            throw new BusinessRuleException(
                    "Transicion no permitida: " + desde + " -> " + hasta + ". Desde " + desde + " solo se puede ir a "
                            + posibles + ".");
        }
    }

    /**
     * 7.3 pide motivo obligatorio y que toda transicion quede auditada con el suyo. Un cambio de
     * estado sin motivo deja una bitacora que dice que paso pero no por que, que es justo la
     * pregunta que se le hace a una bitacora meses despues.
     */
    private static String exigirMotivo(String motivo, String queCambio) {
        if (motivo == null || motivo.isBlank()) {
            throw new BusinessRuleException("El cambio de " + queCambio + " exige un motivo");
        }
        return motivo.trim();
    }

    /** Codigo y nombre: lo justo para identificar al proveedor sin volcar sus datos personales. */
    private static String describir(Productor productor) {
        return productor.getCodigo() + " \u00b7 " + productor.getNombres() + " " + productor.getApellidos();
    }

    /** DNI sin espacios alrededor. La forma (8 digitos) ya la garantizo la validacion del request. */
    /** El DNI limpio, o null si no se escribio. Si se escribio, tiene que ser valido. */
    private static String dniNormalizado(ProductorRequest req) {
        String dni = req.documentoIdentidad() == null ? "" : req.documentoIdentidad().trim();
        if (dni.isEmpty()) {
            return null;
        }
        if (!DatosPersonales.esDniValido(dni)) {
            // Red de seguridad: si alguien llama al servicio sin pasar por la validacion del controlador
            // (otro servicio, una importacion), la regla sigue aplicando.
            throw new BusinessRuleException(DatosPersonales.DNI_MENSAJE);
        }
        return dni;
    }

    private static final String PREFIJO_CODIGO = "PRV-";

    /**
     * Codigo para un proveedor sin DNI: PRV-00001, PRV-00002... El codigo es obligatorio y unico en la
     * base (lo usan el padron, la importacion y los comprobantes), asi que sin DNI hace falta otro.
     */
    private String codigoGenerado() {
        long n = productorRepository.count() + 1;
        String codigo;
        do {
            codigo = PREFIJO_CODIGO + String.format("%05d", n++);
        } while (productorRepository.existsByCodigoIgnoreCase(codigo));
        return codigo;
    }

    private void aplicarDatos(Productor productor, ProductorRequest req) {
        String dni = dniNormalizado(req);
        productor.setNombres(req.nombres().trim());
        productor.setApellidos(req.apellidos().trim());
        productor.setDocumentoIdentidad(dni);
        // El codigo no se recibe: con DNI es el DNI (corregir el DNI corrige el codigo, son el mismo
        // dato); sin DNI se conserva el generado, o se genera uno si el que tenia era el DNI quitado.
        if (dni != null) {
            productor.setCodigo(dni);
        } else if (productor.getCodigo() == null || DatosPersonales.esDniValido(productor.getCodigo())) {
            productor.setCodigo(codigoGenerado());
        }
        productor.setTelefono(req.telefono() == null || req.telefono().isBlank() ? null : req.telefono().trim());
        productor.setNotas(req.notas());
        if (req.rutaId() == null) {
            throw new BusinessRuleException("Selecciona la zona y ruta de acopio del proveedor");
        }
        var ruta = rutaRepository
                .findById(req.rutaId())
                .filter(r -> r.isActivo())
                .orElseThrow(() -> NotFoundException.of("Ruta", req.rutaId()));
        if (ruta.getZona() == null || ruta.getZona().isBlank()) {
            throw new BusinessRuleException("La ruta seleccionada no tiene una zona configurada");
        }
        boolean cambiaDeRuta = productor.getRuta() == null || !productor.getRuta().getId().equals(ruta.getId());
        productor.setRuta(ruta);
        // Orden de visita y acopiador responsable NO son datos de la ficha: el orden se define en
        // "Rutas y mapa" y el acopiador sale de la ruta. Si no vienen se conservan (antes, editar la
        // ficha borraba el orden puesto en el mapa); al cambiar de ruta se descartan, porque eran de
        // la ruta anterior.
        if (cambiaDeRuta) {
            productor.setOrdenEnRuta(null);
            if (productor.getAcopiadorResponsable() != null
                    && (productor.getAcopiadorResponsable().getRuta() == null
                            || !productor.getAcopiadorResponsable().getRuta().getId().equals(ruta.getId()))) {
                productor.setAcopiadorResponsable(null);
            }
        }
        if (req.acopiadorResponsableId() != null) {
            var acopiador = acopiadorRepository
                    .findById(req.acopiadorResponsableId())
                    .filter(a -> a.isActivo())
                    .orElseThrow(() -> NotFoundException.of("Acopiador", req.acopiadorResponsableId()));
            if (acopiador.getRuta() == null || !acopiador.getRuta().getId().equals(ruta.getId())) {
                throw new BusinessRuleException("El acopiador responsable debe pertenecer a la misma zona y ruta");
            }
            productor.setAcopiadorResponsable(acopiador);
        }
        // La zona se deriva de la ruta para evitar textos distintos que rompan la agrupacion territorial.
        productor.setZonaPrincipal(ruta.getZona().trim());
        if (req.ordenEnRuta() != null) {
            productor.setOrdenEnRuta(req.ordenEnRuta());
        }
    }

    private void sincronizarCuenta(Productor productor, ProductorRequest req) {
        productor.setUsuario(usuarioService.sincronizarCuentaDeAcceso(
                productor.getUsuario(),
                req.nombres().trim() + " " + req.apellidos().trim(),
                req.username(),
                req.telefono(),
                req.password(),
                Rol.PROVEEDOR));
    }

    /** El proveedor de la app movil, resuelto desde el login (mismo patron que Acopiador.usuario). */
    public Productor obtenerPorUsuario(String username) {
        return productorRepository
                .findByUsuario_UsernameIgnoreCase(username)
                .orElseThrow(() -> new BusinessRuleException(
                        "El usuario " + username + " no tiene un proveedor asociado."));
    }

    /** "Mis proveedores hoy", en orden de visita: los de esta ruta (2.1.6). */
    public List<Productor> proveedoresDeRuta(Long rutaId) {
        return productorRepository.findByRuta_IdAndActivoTrueOrderByOrdenEnRutaAscNombresAsc(rutaId);
    }
}
