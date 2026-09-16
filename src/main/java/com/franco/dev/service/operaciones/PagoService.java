package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Pago;
import com.franco.dev.domain.operaciones.enums.PagoEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.operaciones.PagoRepository;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;

@Service
@AllArgsConstructor
public class PagoService extends CrudService<Pago, PagoRepository, Long> {
    private final PagoRepository repository;

    /** Unicos estados que se asignan a mano; el resto lo fijan el motor de pago y la anulacion desde la caja. */
    private static final EnumSet<PagoEstado> ESTADOS_MANUALES = EnumSet.of(PagoEstado.ABIERTO, PagoEstado.PENDIENTE);

    @Override
    public PagoRepository getRepository() {
        return repository;
    }

    @Override
    public Pago save(Pago entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
            entity.setEstado(PagoEstado.ABIERTO);
        }
        return super.save(entity);
    }
    
    /**
     * Alta o edicion manual de un {@link Pago} (mutation savePago, pantalla vieja de pagos).
     *
     * <p>Un pago CONCLUIDO, PARCIAL o CANCELADO es un evento del motor de tesoreria: cambiarlo a mano dejaria la caja
     * sin revertir (issue #304), asi que no se edita. En una edicion, {@code usuario} y {@code creadoEn} se conservan
     * de la base.</p>
     */
    @Transactional
    public Pago guardarManual(Long id, PagoEstado estado, Boolean programado, Usuario usuario, Usuario autorizadoPor) {
        if (estado != null && !ESTADOS_MANUALES.contains(estado)) {
            throw new GraphQLException("El estado " + estado + " no se asigna a mano: los pagos se concluyen y se anulan"
                    + " desde la caja.");
        }
        if (id == null) {
            Pago nuevo = new Pago();
            nuevo.setUsuario(usuario);
            nuevo.setAutorizadoPor(autorizadoPor);
            nuevo.setProgramado(programado);
            return save(nuevo);
        }
        Pago actual = repository.findById(id).orElseThrow(() -> new GraphQLException("Pago no encontrado"));
        if (!ESTADOS_MANUALES.contains(actual.getEstado())) {
            throw new GraphQLException("El pago #" + id + " está " + actual.getEstado() + ": se gestiona desde la caja"
                    + " (para revertirlo, anular desde su movimiento).");
        }
        if (estado != null) actual.setEstado(estado);
        if (programado != null) actual.setProgramado(programado);
        actual.setAutorizadoPor(autorizadoPor);
        return save(actual);
    }

    // Note: This method is removed because we now use many-to-many relationship
    // Use PagoSolicitudPagoService to find relationships
}

