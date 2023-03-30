package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.financiero.enums.EstadoVentaCredito;
import com.franco.dev.repository.financiero.VentaCreditoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class VentaCreditoService extends CrudService<VentaCredito, VentaCreditoRepository, Long> {

    private final VentaCreditoRepository repository;

    @Override
    public VentaCreditoRepository getRepository() {
        return repository;
    }

//    public List<VentaCredito> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public List<VentaCredito> findByClienteAndVencimiento(Long id, LocalDateTime inicio, LocalDateTime fin) {
        return repository.findAllByClienteIdAndCreadoEnLessThanEqualAndCreadoEnGreaterThanEqualOrderByCreadoEnDesc(id, inicio, fin);
    }

    public List<VentaCredito> findByClienteId(Long id, EstadoVentaCredito estado, Pageable pageable) {
        return repository.findAllByClienteIdAndEstadoOrderByCreadoEnDesc(id, estado, pageable);
    }

    public List<VentaCredito> findByClienteId(Long id, EstadoVentaCredito estado) {
        return repository.findAllByClienteIdAndEstadoOrderByCreadoEnDesc(id, estado);
    }

    public Long countByClienteIdAndEstado(Long id, EstadoVentaCredito estado){
        return repository.countByClienteIdAndEstado(id, estado);
    }

    @Override
    public VentaCredito save(VentaCredito entity) {
        VentaCredito e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}