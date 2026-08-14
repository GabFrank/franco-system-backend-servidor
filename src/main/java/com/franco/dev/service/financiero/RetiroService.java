package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.financiero.enums.EstadoRetiro;
import com.franco.dev.repository.financiero.RetiroRepository;
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
     * Cancela o rehabilita un retiro, igual que VentaService.cancelarVenta: es un
     * toggle CANCELADO <-> CONCLUIDO.
     *
     * No recalcula ningun balance. El monto vuelve a la caja porque
     * PdvCajaService.generarBalance ignora los detalles de retiros cancelados, y la
     * filial hace lo mismo cuando el nuevo estado le llega por replicacion.
     *
     * Persiste con repository.save() a proposito, y no con this.save(): el override
     * de save() publica RetiroRealizadoEvent, que dispara la push notification
     * "RETIRO REALIZADO". Cancelar no es realizar un retiro.
     */
    @Transactional
    public Boolean cancelarRetiro(Retiro retiro) {
        try {
            if (retiro.getEstado() == EstadoRetiro.CANCELADO) {
                retiro.setEstado(EstadoRetiro.CONCLUIDO);
            } else {
                retiro.setEstado(EstadoRetiro.CANCELADO);
            }
            repository.save(retiro);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            throw new GraphQLException("No se pudo cancelar el retiro");
        }
    }
}