package org.ramiro.huata.tiemporeal;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.proxy.HibernateProxy;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.common.BaseEntity;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.usuarios.Usuario;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Detecta todo lo que se guarda (alta, cambio, baja) en cualquier entidad del dominio y, al confirmarse
 * la transaccion, lo difunde por el canal de cambios ({@link CambiosWebSocketHandler}).
 *
 * <p><b>Un solo punto y no un aviso en cada servicio.</b> Escucha los eventos de Hibernate: un
 * servicio nuevo, o uno que cambia, queda en tiempo real sin acordarse de nada. El catalogo de que se
 * difunde y a quien esta en {@link ModulosDeCambio}.
 *
 * <p><b>Despues del commit.</b> Los cambios se juntan por transaccion (sin repetir la misma fila) y se
 * envian en {@code afterCommit}: avisar antes mostraria algo que todavia puede revertirse. Si la
 * transaccion se revierte, no se envia nada.
 *
 * <p><b>Un fallo aqui nunca rompe la operacion.</b> Lo guardado ya esta guardado; a lo sumo una
 * pantalla se entera en su siguiente recarga.
 */
@Slf4j
@Component
public class RegistroCambios {

    private static final Object CLAVE_TRANSACCION = new Object();
    private static final Map<Class<?>, Alcance> ALCANCES = new ConcurrentHashMap<>();

    private final CambiosWebSocketHandler canal;
    private final EntityManagerFactory entityManagerFactory;

    public RegistroCambios(CambiosWebSocketHandler canal, EntityManagerFactory entityManagerFactory) {
        this.canal = canal;
        this.entityManagerFactory = entityManagerFactory;
    }

    /**
     * Se engancha a los eventos de alta, cambio y baja de Hibernate de ESTE contexto (en las pruebas
     * conviven varios; un listener estatico mezclaria los canales).
     */
    @PostConstruct
    void escucharHibernate() {
        EventListenerRegistry eventos = entityManagerFactory.unwrap(SessionFactoryImplementor.class)
                .getServiceRegistry()
                .getService(EventListenerRegistry.class);
        DetectorHibernate detector = new DetectorHibernate();
        eventos.appendListeners(EventType.POST_INSERT, detector);
        eventos.appendListeners(EventType.POST_UPDATE, detector);
        eventos.appendListeners(EventType.POST_DELETE, detector);
    }

    private final class DetectorHibernate
            implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

        @Override
        public void onPostInsert(PostInsertEvent evento) {
            registrar(evento.getEntity(), Cambio.Accion.CREADO);
        }

        @Override
        public void onPostUpdate(PostUpdateEvent evento) {
            registrar(evento.getEntity(), Cambio.Accion.ACTUALIZADO);
        }

        @Override
        public void onPostDelete(PostDeleteEvent evento) {
            registrar(evento.getEntity(), Cambio.Accion.ELIMINADO);
        }

        /** Se juntan por transaccion y se envian en afterCommit (ver {@link #encolar}). */
        @Override
        public boolean requiresPostCommitHandling(EntityPersister persister) {
            return false;
        }
    }

    void registrar(Object entidad, Cambio.Accion accion) {
        if (!(entidad instanceof BaseEntity base) || base.getId() == null) return;
        try {
            describir(base, accion).ifPresent(this::encolar);
        } catch (RuntimeException e) {
            log.debug("No se pudo registrar el cambio de {}: {}", entidad.getClass().getSimpleName(), e.toString());
        }
    }

    /** Que cambio y a quien le importa. Publico para las pruebas. */
    public static Optional<Cambio> describir(BaseEntity entidad, Cambio.Accion accion) {
        Class<?> clase = claseReal(entidad);
        return ModulosDeCambio.de(clase).map(modulo -> {
            Alcance alcance = ALCANCES.computeIfAbsent(clase, Alcance::de);
            return new Cambio(
                    modulo.nombre(),
                    clase.getSimpleName(),
                    entidad.getId(),
                    accion,
                    modulo.permisos(),
                    entidad instanceof Productor ? entidad.getId() : alcance.idDe(alcance.productor, entidad),
                    entidad instanceof Acopiador ? entidad.getId() : alcance.idDe(alcance.acopiador, entidad),
                    entidad instanceof Usuario ? entidad.getId() : alcance.idDe(alcance.usuario, entidad));
        });
    }

    private void encolar(Cambio cambio) {
        if (canal.oyentes() == 0) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            difundir(List.of(cambio));
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Cambio> pendientes = (Map<String, Cambio>) TransactionSynchronizationManager.getResource(CLAVE_TRANSACCION);
        if (pendientes == null) {
            Map<String, Cambio> nuevos = new LinkedHashMap<>();
            TransactionSynchronizationManager.bindResource(CLAVE_TRANSACCION, nuevos);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    difundir(new ArrayList<>(nuevos.values()));
                }

                @Override
                public void afterCompletion(int estado) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(CLAVE_TRANSACCION);
                }
            });
            pendientes = nuevos;
        }
        // Creado y luego actualizado en la misma transaccion sigue siendo "creado"; eliminado manda.
        pendientes.merge(cambio.clave(), cambio, (antes, ahora) ->
                ahora.accion() == Cambio.Accion.ELIMINADO || antes.accion() != Cambio.Accion.CREADO ? ahora : antes);
    }

    private void difundir(List<Cambio> cambios) {
        try {
            canal.publicar(cambios);
        } catch (RuntimeException e) {
            log.warn("No se pudieron difundir {} cambio(s): {}", cambios.size(), e.toString());
        }
    }

    private static Class<?> claseReal(Object entidad) {
        return entidad instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : entidad.getClass();
    }

    /**
     * Campos {@code productor}, {@code acopiador} y {@code usuario} de la entidad, si los tiene: dicen a
     * que proveedor, acopiador o cuenta le toca el cambio aunque no tenga el permiso del modulo.
     */
    private record Alcance(Field productor, Field acopiador, Field usuario) {
        static Alcance de(Class<?> clase) {
            return new Alcance(campo(clase, "productor", Productor.class),
                    campo(clase, "acopiador", Acopiador.class),
                    campo(clase, "usuario", Usuario.class));
        }

        private static Field campo(Class<?> clase, String nombre, Class<?> tipo) {
            for (Class<?> c = clase; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField(nombre);
                    if (!tipo.isAssignableFrom(f.getType())) return null;
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException e) {
                    // sigue con la superclase
                } catch (RuntimeException e) {
                    return null;
                }
            }
            return null;
        }

        /** El id de la entidad asociada sin cargarla: un proxy perezoso ya lo conoce. */
        Long idDe(Field campo, Object entidad) {
            if (campo == null) return null;
            try {
                Object valor = campo.get(entidad);
                if (valor instanceof HibernateProxy proxy) {
                    Object id = proxy.getHibernateLazyInitializer().getInternalIdentifier();
                    return id instanceof Long l ? l : null;
                }
                return valor instanceof BaseEntity b ? b.getId() : null;
            } catch (IllegalAccessException e) {
                return null;
            }
        }
    }
}
