package org.ramiro.huata.calidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Foto del voucher: se sube, se guarda con su texto OCR y se descarga con autorizacion.
 *
 * <p>Por HTTP y contra la cadena de seguridad real: lo que importa aqui es tanto que funcione como
 * que no se pueda abusar (subir algo que no es imagen, bajar sin permiso, duplicar desde la cola).
 */
@SpringBootTest
@Transactional
class VoucherTests {

    /** Cabecera minima de un JPEG real (FF D8 FF E0 ... JFIF). */
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1};

    @Autowired WebApplicationContext context;
    @Autowired ControlCalidadRepository controles;

    MockMvc mvc;
    Long controlId;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        controlId = controles.findAll().get(0).getId();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor testeador() {
        return user("calidad.demo@huata.pe").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(Permiso.CALIDAD_READ),
                new org.springframework.security.core.authority.SimpleGrantedAuthority(Permiso.CALIDAD_WRITE));
    }

    private static MockMultipartFile foto(byte[] bytes, String nombre, String tipo) {
        return new MockMultipartFile("foto", nombre, tipo, bytes);
    }

    @Test
    void subeLaFotoConSuTextoOcrYSeDescargaIgual() throws Exception {
        var respuesta = mvc.perform(multipart("/api/v1/calidad/controles/{id}/voucher", controlId)
                        .file(foto(JPEG, "voucher.jpg", "image/jpeg"))
                        .param("clientUuid", UUID.randomUUID().toString())
                        .param("textoOcr", "Densidad.....23.9")
                        .param("valoresOcr", "{\"densidad\":23.9}")
                        .param("confianzaOcr", "0.87")
                        .with(testeador()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("VOUCHER"))
                .andExpect(jsonPath("$.tieneArchivo").value(true))
                .andExpect(jsonPath("$.tipoContenido").value("image/jpeg"))
                .andExpect(jsonPath("$.textoOcr").value("Densidad.....23.9"))
                .andExpect(jsonPath("$.confianzaOcr").value(0.87))
                .andExpect(jsonPath("$.verificadoPor").value("calidad.demo@huata.pe"))
                .andReturn();
        Integer id = JsonPath.read(respuesta.getResponse().getContentAsString(), "$.id");
        String url = JsonPath.read(respuesta.getResponse().getContentAsString(), "$.urlArchivo");
        assertThat(url).isEqualTo("/api/v1/calidad/evidencias/" + id + "/archivo");

        mvc.perform(get(url).with(testeador()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(JPEG));
    }

    /** La cola offline reintenta con el mismo clientUuid: no debe quedar una evidencia duplicada. */
    @Test
    void reintentarLaMismaSubidaNoDuplica() throws Exception {
        String uuid = UUID.randomUUID().toString();
        String primera = mvc.perform(multipart("/api/v1/calidad/controles/{id}/voucher", controlId)
                        .file(foto(JPEG, "v.jpg", "image/jpeg")).param("clientUuid", uuid).with(testeador()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String segunda = mvc.perform(multipart("/api/v1/calidad/controles/{id}/voucher", controlId)
                        .file(foto(JPEG, "v.jpg", "image/jpeg")).param("clientUuid", uuid).with(testeador()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertThat((Integer) JsonPath.read(segunda, "$.id")).isEqualTo(JsonPath.read(primera, "$.id"));
    }

    /** El tipo se decide por el contenido, no por el nombre ni el Content-Type que manda el cliente. */
    @Test
    void rechazaUnArchivoQueNoEsImagenAunqueDigaSerlo() throws Exception {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();
        mvc.perform(multipart("/api/v1/calidad/controles/{id}/voucher", controlId)
                        .file(foto(html, "voucher.jpg", "image/jpeg")).with(testeador()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rechazaUnaFotoVacia() throws Exception {
        mvc.perform(multipart("/api/v1/calidad/controles/{id}/voucher", controlId)
                        .file(foto(new byte[0], "v.jpg", "image/jpeg")).with(testeador()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void sinLaFotoEsErrorDelClienteYNo500() throws Exception {
        mvc.perform(multipart("/api/v1/calidad/controles/{id}/voucher", controlId).with(testeador()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unControlInexistenteDa404() throws Exception {
        mvc.perform(multipart("/api/v1/calidad/controles/{id}/voucher", 999_999_999L)
                        .file(foto(JPEG, "v.jpg", "image/jpeg")).with(testeador()))
                .andExpect(status().isNotFound());
    }

    @Test
    void unIdentificadorDeSubidaMalformadoSeRechaza() throws Exception {
        mvc.perform(multipart("/api/v1/calidad/controles/{id}/voucher", controlId)
                        .file(foto(JPEG, "v.jpg", "image/jpeg")).param("clientUuid", "../../etc/passwd").with(testeador()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void subirExigeCalidadWrite() throws Exception {
        mvc.perform(multipart("/api/v1/calidad/controles/{id}/voucher", controlId)
                        .file(foto(JPEG, "v.jpg", "image/jpeg"))
                        .with(user("x@huata.pe").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority(Permiso.CALIDAD_READ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void descargarExigeCalidadRead() throws Exception {
        String r = mvc.perform(multipart("/api/v1/calidad/controles/{id}/voucher", controlId)
                        .file(foto(JPEG, "v.jpg", "image/jpeg")).with(testeador()))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(r, "$.urlArchivo");

        mvc.perform(get(url).with(user("x@huata.pe").authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority(Permiso.ACOPIO_READ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void detectaLosTiposPorSusBytes() {
        assertThat(VoucherService.TipoImagen.detectar(JPEG)).isEqualTo(VoucherService.TipoImagen.JPEG);
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        assertThat(VoucherService.TipoImagen.detectar(png)).isEqualTo(VoucherService.TipoImagen.PNG);
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        assertThat(VoucherService.TipoImagen.detectar(webp)).isEqualTo(VoucherService.TipoImagen.WEBP);
        assertThat(VoucherService.TipoImagen.detectar("GIF89a".getBytes())).isNull();
    }
}
