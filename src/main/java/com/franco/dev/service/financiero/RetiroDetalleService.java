package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.RetiroDetalle;
import com.franco.dev.repository.financiero.RetiroDetalleRepository;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class RetiroDetalleService extends CrudService<RetiroDetalle, RetiroDetalleRepository, EmbebedPrimaryKey> {

    private final RetiroDetalleRepository repository;
    @Autowired
    public CambioService cambioService;
    @Autowired
    private MovimientoCajaService movimientoCajaService;
    @Autowired
    private RetiroService retiroService;

    @Override
    public RetiroDetalleRepository getRepository() {
        return repository;
    }

//    public List<RetiroDetalle> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public List<RetiroDetalle> findByRetiroId(Long id, Long sucId) {
        return repository.findByRetiroIdAndSucursalId(id, sucId);
    }

    public Double findByRetiroIdAndMonedaId(Long id, Long monedaId, Long sucId) {
        List<RetiroDetalle> retiroDetalles = repository.findByRetiroIdAndMonedaIdAndSucursalId(id, monedaId, sucId);
        Double total = 0.0;
        for (RetiroDetalle r : retiroDetalles) {
            total += r.getCantidad();
        }
        return total;
    }

    public List<RetiroDetalle> findByCajId(Long id, Long sucId) {
        return repository.findByRetiroCajaSalidaIdAndSucursalId(id, sucId);
    }

    @Override
    public RetiroDetalle save(RetiroDetalle entity) throws GraphQLException {
        RetiroDetalle e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}