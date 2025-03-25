package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Cheque;
import com.franco.dev.repository.financiero.ChequeRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class ChequeService extends CrudService<Cheque, ChequeRepository, Long> {
    private final ChequeRepository repository;

    @Override
    public ChequeRepository getRepository() {
        return repository;
    }

    @Override
    public Cheque save(Cheque entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        return super.save(entity);
    }

    public List<Cheque> findByAll(String texto) {
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto);
    }
    
    public List<Cheque> findByChequeraId(Long chequeraId) {
        return repository.findByChequeraId(chequeraId);
    }
    
    public Cheque findByPagoDetalleCuotaId(Long pagoDetalleCuotaId) {
        return repository.findByPagoDetalleCuotaId(pagoDetalleCuotaId);
    }
} 