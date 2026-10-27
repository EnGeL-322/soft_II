package org.ramiro.huata.acopiadores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.rutas.RutaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AsignacionZonaAcopiadorTests {

    @Autowired
    private AcopiadorService acopiadorService;

    @Autowired
    private RutaRepository rutaRepository;

    /** Con DNI valido a proposito: lo que se prueba aqui es la zona, no el documento. */
    @Test
    void crearAcopiadorExigeUnaZonaYRuta() {
        assertThatThrownBy(() -> acopiadorService.crear(
                        new AcopiadorRequest("Juan", "Mamani", "70707071", null, null, "juan@huata.pe", "Huata2026*")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("zona y ruta");
    }

    /** El DNI es opcional, pero si se escribe tiene que ser valido, aunque la zona este bien. */
    @Test
    void crearAcopiadorExigeUnDniDeOchoDigitos() {
        assertThatThrownBy(() -> acopiadorService.crear(
                        new AcopiadorRequest("Juan", "Mamani", "1234567", null, null, "juan2@huata.pe", "Huata2026*")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("8 dígitos");
    }

    @Test
    void zonaDelAcopiadorSeObtieneDeLaRuta() {
        Ruta ruta = rutaRepository.findAll().stream()
                .filter(r -> r.getZona() != null && !r.getZona().isBlank())
                .findFirst()
                .orElseThrow();

        Acopiador creado = acopiadorService.crear(
                new AcopiadorRequest(
                        "Juan", "Mamani", "44556677", "951000111", ruta.getId(), "juan@huata.pe", "Huata2026*"));

        assertThat(creado.getRuta()).isEqualTo(ruta);
        assertThat(AcopiadorResponse.from(creado).zona()).isEqualTo(ruta.getZona());
        assertThat(creado.getUsuario().getUsername()).isEqualTo("juan@huata.pe");
        assertThat(creado.getUsuario().getRoles()).extracting("nombre").containsExactly("ACOPIADOR");
    }

    /** DNI y telefono son opcionales (pedido del cliente, 2026-10-24): vacios se guardan como null. */
    @Test
    void crearAcopiadorSinDniNiTelefono() {
        Ruta ruta = rutaRepository.findAll().stream()
                .filter(r -> r.getZona() != null && !r.getZona().isBlank())
                .findFirst()
                .orElseThrow();

        Acopiador creado = acopiadorService.crear(
                new AcopiadorRequest("Rosa", "Quispe", "", "  ", ruta.getId(), "rosa.sin.dni@huata.pe", "Huata2026*"));

        assertThat(creado.getDocumentoIdentidad()).isNull();
        assertThat(creado.getTelefono()).isNull();
        assertThat(creado.getUsuario().getUsername()).isEqualTo("rosa.sin.dni@huata.pe");
    }
}
