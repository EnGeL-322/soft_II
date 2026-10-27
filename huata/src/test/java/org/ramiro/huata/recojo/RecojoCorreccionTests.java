package org.ramiro.huata.recojo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pedido del cliente (2026-09-24, ver CLAUDE.md): la app dejaba re-registrar un proveedor ya
 * acopiado ese dia, lo que choca contra "una entrega diaria por proveedor" ({@link
 * RecojoService#registrar}). {@link RecojoService#corregir} es la via oficial para arreglar un
 * litros mal tecleado: exige motivo y deja el recojo marcado {@code observado} para revision, en vez
 * de crear un segundo recojo o cambiar el numero en silencio.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "calidad.demo@huata.pe")
class RecojoCorreccionTests {

    // Un recojo en ruta exige el acopiador que lo hizo (RecojoService, 2026-10-14).
    @org.springframework.beans.factory.annotation.Autowired
    private org.ramiro.huata.acopiadores.AcopiadorRepository acopiadoresDePrueba;

    private Long acopiadorDePrueba() {
        return acopiadoresDePrueba.findByActivoTrueOrderByNombresAsc().stream()
                .findFirst()
                .orElseGet(() -> {
                    var a = new org.ramiro.huata.acopiadores.Acopiador();
                    a.setNombres("Acopiador");
                    a.setApellidos("De prueba");
                    return acopiadoresDePrueba.save(a);
                })
                .getId();
    }


    @Autowired
    private RecojoService recojoService;

    @Autowired
    private ProductorRepository productorRepository;

    @Autowired
    private RecojoRepository recojoRepository;

    private Productor proveedor;

    @BeforeEach
    void escenario() {
        proveedor = productorRepository.findAll().stream()
                .filter(Productor::isActivo)
                .findFirst()
                .orElseThrow();
    }

    private Recojo registrarOriginal(double litros) {
        return recojoService.registrar(new RecojoRequest(
                UUID.randomUUID().toString(),
                proveedor.getId(),
                acopiadorDePrueba(),
                null,
                Instant.parse("2035-06-01T12:00:00Z"),
                litros,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));
    }

    @Test
    void corregirCambiaLosLitrosYQuedaObservado() {
        Recojo original = registrarOriginal(50.0);

        Recojo corregido = recojoService.corregir(original.getId(), new RecojoCorreccionRequest(65.0, "Se me paso apuntar 15 litros"));

        assertThat(corregido.getId()).isEqualTo(original.getId());
        assertThat(corregido.getLitros()).isEqualTo(65.0);
        assertThat(corregido.isObservado()).isTrue();
        assertThat(corregido.getMotivoObservacion()).isEqualTo("Se me paso apuntar 15 litros");
    }

    @Test
    void unRecojoSinCorregirNoQuedaObservado() {
        Recojo original = registrarOriginal(50.0);

        assertThat(original.isObservado()).isFalse();
        assertThat(original.getMotivoObservacion()).isNull();
    }

    @Test
    void exigeMotivo() {
        Recojo original = registrarOriginal(50.0);

        assertThatThrownBy(() -> recojoService.corregir(original.getId(), new RecojoCorreccionRequest(65.0, "  ")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("motivo");
        assertThatThrownBy(() -> recojoService.corregir(original.getId(), new RecojoCorreccionRequest(65.0, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void noPermiteCorregirUnRecojoCancelado() {
        Recojo original = registrarOriginal(50.0);
        recojoService.cancelar(original.getId(), new RecojoCancelRequest("duplicado"));

        assertThatThrownBy(() -> recojoService.corregir(original.getId(), new RecojoCorreccionRequest(65.0, "motivo")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cancelado");
    }

    @Test
    void unRecojoInexistenteLanza404() {
        assertThatThrownBy(() -> recojoService.corregir(999_999L, new RecojoCorreccionRequest(65.0, "motivo")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void corregirNoCreaUnSegundoRecojo() {
        // El proveedor ya trae historial sembrado por DemoDataSeeder: se compara el total antes y
        // despues, no un numero fijo, para no depender de cuantas filas haya de antemano.
        long totalAntesDeRegistrar = recojoRepository.count();

        Recojo original = registrarOriginal(50.0);
        long totalTrasRegistrar = recojoRepository.count();

        recojoService.corregir(original.getId(), new RecojoCorreccionRequest(65.0, "motivo"));
        long totalTrasCorregir = recojoRepository.count();

        assertThat(totalTrasRegistrar).isEqualTo(totalAntesDeRegistrar + 1);
        assertThat(totalTrasCorregir).isEqualTo(totalTrasRegistrar);
    }
}
