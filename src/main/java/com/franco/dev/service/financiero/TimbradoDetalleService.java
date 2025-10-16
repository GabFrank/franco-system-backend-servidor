package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.repository.financiero.BancoRepository;
import com.franco.dev.repository.financiero.TimbradoDetalleRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class TimbradoDetalleService extends CrudService<TimbradoDetalle, TimbradoDetalleRepository, EmbebedPrimaryKey> {

    private final TimbradoDetalleRepository repository;

    @Override
    public TimbradoDetalleRepository getRepository() {
        return repository;
    }

    public List<TimbradoDetalle> findByTimbradoId(Long id){
        return repository.findByTimbradoId(id);
    }

    public List<TimbradoDetalle> findByPuntoDeVentaId(Long id){
        return repository.findByPuntoDeVentaId(id);
    }

    public List<TimbradoDetalle> findBySucursalId(Long id){
        return repository.findBySucursalId(id);
    }

    public Page<TimbradoDetalle> findByTimbradoId(Long timbradoId, Pageable pageable){
        return repository.findByTimbradoId(timbradoId, pageable);
    }

    public Optional<TimbradoDetalle> findByIdAndSucursalId(Long id, Long sucId){
        return repository.findByIdAndSucursalId(id, sucId);
    }

    @Override
    public TimbradoDetalle save(TimbradoDetalle entity) {
        TimbradoDetalle e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}