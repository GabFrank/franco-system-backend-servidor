package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Necesidad;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.enums.EtapaTransferencia;
import com.franco.dev.domain.operaciones.enums.TipoTransferencia;
import com.franco.dev.domain.operaciones.enums.TransferenciaEstado;
import com.franco.dev.repository.operaciones.NecesidadRepository;
import com.franco.dev.repository.operaciones.TransferenciaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class TransferenciaService extends CrudService<Transferencia, TransferenciaRepository, Long> {
    private final TransferenciaRepository repository;

    @Override
    public TransferenciaRepository getRepository() {
        return repository;
    }

    public List<Transferencia> findBySucursalOrigenId(Long id){
        return  repository.findBySucursalOrigenId(id);
    }

    public List<Transferencia> findBySucursalDestinoId(Long id){
        return  repository.findBySucursalDestinoId(id);
    }

    public List<Transferencia> findByDate(String start, String end){
        return repository.findByDate(start, end);
    }

    public List<Transferencia> findByUsuario(Long id){
        return  repository.findByUsuario(id);
    }

    public Page<Transferencia> findByFilter(Long sucursalOrigenId, Long sucursalDestinoId, TransferenciaEstado estado,
                                            TipoTransferencia tipo, EtapaTransferencia etapa, Boolean isOrigen, Boolean isDestino, LocalDateTime creadoDesde,
                                            LocalDateTime creadoHasta, Pageable pageable) {
        return repository.findByFilter(sucursalOrigenId, sucursalDestinoId, estado, tipo, etapa, isOrigen, isDestino, creadoDesde, creadoHasta, pageable);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Transferencia save(Transferencia entity) {
        if(entity.getId()==null) entity.setCreadoEn(LocalDateTime.now());
        if(entity.getEtapa()==null) entity.setEtapa(EtapaTransferencia.PRE_TRANSFERENCIA_CREACION);
        Transferencia e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}