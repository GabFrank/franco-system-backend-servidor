package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.financiero.enums.EstadoRetiro;
import com.franco.dev.repository.financiero.RetiroRepository;
import com.franco.dev.repository.financiero.RetiroSituacion;
import com.franco.dev.repository.financiero.RetiroVerificacionRepository;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@AllArgsConstructor
public class RetiroService extends CrudService<Retiro, RetiroRepository, EmbebedPrimaryKey> {

    private final RetiroRepository repository;
    private final ApplicationEventPublisher publisher;
    private final RetiroVerificacionRepository verificacionRepository;

    @Override
    public RetiroRepository getRepository() {
        return repository;
    }

    // public List<Retiro> findByDenominacion(String texto){
    // texto = texto.replace(' ', '%');
    // return repository.findByDenominacionIgnoreCaseLike(texto);
    // }

    // public List<Retiro> findByAll(String texto){
    // texto = texto.replace(' ', '%');
    // return repository.findByAll(texto);
    // }

    public List<Retiro> findByCajaSalidaId(Long id) {
        return repository.findByCajaSalidaId(id);
    }

    public List<Retiro> filterRetiros(Long id, Long cajaId, Long sucId, Long responsableId, Long cajeroId,
            Pageable pageable) {
        return repository.findByAll(id, cajaId, sucId, responsableId, cajeroId, pageable);
    }

    public Page<Retiro> filterRetirosPage(Long id, Long cajaId, Long sucId, Long responsableId, Long cajeroId,
            Pageable pageable) {
        return repository.findByAllPage(id, cajaId, sucId, responsableId, cajeroId, pageable);
    }

    public Retiro findByIdAndSucursalId(Long id, Long sucId) {
        return repository.findByIdAndSucursalId(id, sucId);
    }

    /** Retiros flotantes (replicados del PDV, sin caja mayor asignada); filtro por sucursal, caja y fechas. */
    public Page<Retiro> findFlotantes(Long sucId, Long cajaId, java.time.LocalDateTime desde,
                                      java.time.LocalDateTime hasta, Pageable pageable) {
        return repository.findFlotantes(sucId, cajaId, desde, hasta, pageable);
    }

    @Override
    public Retiro save(Retiro entity) {
        Retiro e = super.save(entity);
        publisher.publishEvent(new com.franco.dev.fmc.event.RetiroRealizadoEvent(this, e));
        return e;
    }

    /**
     * Cancela ({@code cancelar = true}) o habilita ({@code false}) un retiro. El pedido dice cómo tiene
     * que quedar, así que repetirlo no cambia nada: antes era un interruptor y un reintento o un doble
     * clic deshacía la cancelación (issue #376).
     *
     * <p>Sin el argumento —un desktop anterior— significa cancelar y nunca habilita: sobre un retiro ya
     * cancelado se rechaza, porque ese pedido puede ser tanto un «Habilitar» como la repetición de un
     * «Cancelar», y adivinar mal vuelve a descontar la plata de la caja.</p>
     *
     * <p>El retiro se toma con el mismo lock que la verificación y el ingreso a caja mayor, y su estado
     * se lee de la base. Solo se cancela un retiro que todavía no entró a la caja mayor: cancelado deja
     * de descontar de la caja del PDV ({@code PdvCajaService.generarBalance} lo ignora) y, si además
     * está acreditado en la caja mayor, la plata queda contada dos veces.</p>
     *
     * <p>No recalcula ningún balance, y la filial hace lo mismo cuando el estado le llega por
     * replicación. No pasa por {@link #save}: su override publica RetiroRealizadoEvent («RETIRO
     * REALIZADO»), y cancelar no es realizar un retiro.</p>
     */
    @Transactional
    public Boolean cancelarRetiro(Long id, Long sucId, Boolean cancelar) {
        repository.lockByIdAndSucursalId(id, sucId)
                .orElseThrow(() -> new GraphQLException("Retiro no encontrado: " + id + "/" + sucId));
        RetiroSituacion s = repository.findSituacion(id, sucId)
                .orElseThrow(() -> new GraphQLException("Retiro no encontrado: " + id + "/" + sucId));

        if (Boolean.FALSE.equals(cancelar)) {
            if (s.estaCancelado()) repository.marcarConcluido(id, sucId);
            return true;
        }
        if (s.estaCancelado()) {
            if (cancelar == null) {
                throw new GraphQLException("El retiro #" + id + " ya está cancelado. Para habilitarlo actualizá el sistema.");
            }
            return true;
        }
        if (s.getMovimientoCajaVirtualId() != null
                || esVerificado(s.getEstado())
                || verificacionRepository.findVigente(id, sucId).isPresent()) {
            throw new GraphQLException("El retiro #" + id + " ya entró a la caja mayor: anulá primero su verificación.");
        }
        if (s.getCajaVirtualId() != null) {
            throw new GraphQLException("El retiro #" + id + " ya tiene una caja mayor asignada: no se puede cancelar.");
        }
        // Lista blanca: un retiro EN_PROCESO todavía está abierto en el PDV, y habilitarlo después lo
        // dejaría CONCLUIDO y verificable a medio cargar.
        if (s.getEstado() != null && s.getEstado() != EstadoRetiro.CONCLUIDO) {
            throw new GraphQLException("El retiro #" + id + " está en estado " + s.getEstado() + ": no se puede cancelar.");
        }
        repository.marcarCancelado(id, sucId);
        return true;
    }

    private static boolean esVerificado(EstadoRetiro estado) {
        return estado == EstadoRetiro.VERIFICADO_CONCLUIDO_SIN_PROBLEMA
                || estado == EstadoRetiro.VERIFICADO_CONCLUIDO_CON_PROBLEMA;
    }
}
