package org.ramiro.huata.productores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.auditoria.AuditoriaRepository;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.rutas.RutaRepository;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Traer o mover a un proveedor a una ruta desde "Rutas y mapa" (pedido del cliente, 2026-10-01): igual
 * que con los acopiadores, sin rehacer la ficha. Sobre todo tiene que servir para un proveedor heredado
 * sin DNI valido, que es justo el que la edicion completa rechaza.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "admin@huata.pe")
class ProveedorRutaTests {

    @Autowired private ProductorService service;
    @Autowired private ProductorRepository productores;
    @Autowired private RutaRepository rutas;
    @Autowired private AcopiadorRepository acopiadores;
    @Autowired private AuditoriaRepository auditoria;
    @Autowired private WebApplicationContext contexto;

    private Ruta origen;
    private Ruta destino;
    private Acopiador acopiadorDestino;
    private Productor heredado;

    @BeforeEach
    void escenario() {
        origen = rutaNueva("Ruta origen de prueba", "ZONA ORIGEN");
        destino = rutaNueva("Ruta destino de prueba", "ZONA DESTINO");
        acopiadorDestino = acopiadores.findAll().stream().filter(Acopiador::isActivo).findFirst().orElseThrow();
        acopiadorDestino.setRuta(destino);
        acopiadores.save(acopiadorDestino);

        heredado = new Productor();
        heredado.setCodigo("HEREDADO-T1");
        heredado.setNombres("Rosa");
        heredado.setApellidos("Ramos");
        heredado.setEstado(EstadoProductor.ACTIVO);
        heredado.setRuta(origen);
        heredado.setZonaPrincipal(origen.getZona());
        heredado.setOrdenEnRuta(4);
        heredado = productores.save(heredado);
        auditoria.deleteAll();
    }

    private Ruta rutaNueva(String nombre, String zona) {
        Ruta ruta = new Ruta();
        ruta.setNombre(nombre);
        ruta.setZona(zona);
        return rutas.save(ruta);
    }

    @Test
    void unProveedorHeredadoSinDniCambiaDeRutaSinRehacerSuFicha() {
        Productor movido = service.asignarRuta(heredado.getId(), new ProductorRutaRequest(destino.getId(), 2));

        assertThat(movido.getRuta().getId()).isEqualTo(destino.getId());
        assertThat(movido.getOrdenEnRuta()).isEqualTo(2);
        assertThat(movido.getCodigo()).isEqualTo("HEREDADO-T1");
        assertThat(movido.getDocumentoIdentidad()).isNull();
    }

    /** La zona no se escribe a mano: sale de la ruta, como en la ficha y en el cambio de zona. */
    @Test
    void laZonaSaleDeLaRutaYElAcopiadorResponsableEsElDeLaRutaNueva() {
        Productor movido = service.asignarRuta(heredado.getId(), new ProductorRutaRequest(destino.getId(), null));

        assertThat(movido.getZonaPrincipal()).isEqualTo("ZONA DESTINO");
        assertThat(movido.getAcopiadorResponsable()).isNotNull();
        assertThat(movido.getAcopiadorResponsable().getRuta().getId()).isEqualTo(destino.getId());
        assertThat(movido.getOrdenEnRuta()).isNull();
    }

    @Test
    void elCambioQuedaEnLaBitacoraConDeDondeVieneYAdondeVa() {
        service.asignarRuta(heredado.getId(), new ProductorRutaRequest(destino.getId(), 1));

        assertThat(auditoria.findAllByOrderByFechaDesc(PageRequest.of(0, 10)).getContent())
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.getAccion()).isEqualTo("Cambio de ruta");
                    assertThat(r.getActor()).isEqualTo("admin@huata.pe");
                    assertThat(r.getDetalle())
                            .contains("HEREDADO-T1")
                            .contains("Ruta origen de prueba -> Ruta destino de prueba")
                            .contains("ZONA DESTINO");
                });
    }

    /** Volver a guardar lo mismo no ensucia la bitacora con un "cambio" que no cambio nada. */
    @Test
    void laMismaRutaYElMismoOrdenNoDejanRegistro() {
        service.asignarRuta(heredado.getId(), new ProductorRutaRequest(origen.getId(), 4));

        assertThat(auditoria.count()).isZero();
    }

    @Test
    void reordenarDentroDeLaMismaRutaSiQuedaRegistrado() {
        Productor reordenado = service.asignarRuta(heredado.getId(), new ProductorRutaRequest(origen.getId(), 1));

        assertThat(reordenado.getOrdenEnRuta()).isEqualTo(1);
        assertThat(auditoria.count()).isEqualTo(1);
    }

    @Test
    void noSeAsignaAUnaRutaDadaDeBaja() {
        destino.setActivo(false);
        rutas.save(destino);

        assertThatThrownBy(() -> service.asignarRuta(heredado.getId(), new ProductorRutaRequest(destino.getId(), null)))
                .isInstanceOf(NotFoundException.class);
    }

    /** Una ruta sin zona es un callejon (CLAUDE.md 36.3): el proveedor quedaria sin zona. */
    @Test
    void noSeAsignaAUnaRutaSinZona() {
        Ruta sinZona = rutaNueva("Ruta sin zona", null);

        assertThatThrownBy(() -> service.asignarRuta(heredado.getId(), new ProductorRutaRequest(sinZona.getId(), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no tiene una zona");
    }

    @Test
    void elOrdenEmpiezaEnUno() {
        assertThatThrownBy(() -> service.asignarRuta(heredado.getId(), new ProductorRutaRequest(destino.getId(), 0)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void unProveedorInexistenteResponde404() {
        assertThatThrownBy(() -> service.asignarRuta(999_999L, new ProductorRutaRequest(destino.getId(), null)))
                .isInstanceOf(NotFoundException.class);
    }

    /** Mover proveedores es escribir sobre el padron: con solo proveedores.read se recibe 403. */
    @Test
    void cambiarLaRutaExigeProveedoresWrite() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(SecurityMockMvcConfigurers.springSecurity()).build();
        String cuerpo = "{\"rutaId\":" + destino.getId() + ",\"ordenEnRuta\":1}";

        mvc.perform(patch("/api/v1/productores/" + heredado.getId() + "/ruta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo)
                        .with(SecurityMockMvcRequestPostProcessors.user("lector")
                                .authorities(new SimpleGrantedAuthority(Permiso.PROVEEDORES_READ))))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/v1/productores/" + heredado.getId() + "/ruta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo)
                        .with(SecurityMockMvcRequestPostProcessors.user("admin@huata.pe")
                                .authorities(new SimpleGrantedAuthority(Permiso.PROVEEDORES_READ),
                                        new SimpleGrantedAuthority(Permiso.PROVEEDORES_WRITE))))
                .andExpect(status().isOk());
    }
}
