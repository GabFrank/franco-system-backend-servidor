package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipo;
import com.franco.dev.repository.financiero.CajaVirtualRepository;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class CajaVirtualService {

    private final CajaVirtualRepository repository;

    public Optional<CajaVirtual> findById(Long id) {
        return repository.findById(id);
    }

    public Page<CajaVirtual> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    public List<CajaVirtual> findByTipo(CajaVirtualTipo tipo) {
        return repository.findByTipo(tipo);
    }

    public List<CajaVirtual> findBySucursalId(Long sucursalId) {
        return repository.findBySucursalId(sucursalId);
    }

    public List<CajaVirtual> findActivas() {
        return repository.findByActivoTrue();
    }

    public CajaVirtual save(CajaVirtual entity) {
        if (entity.getSaldoGs() == null) entity.setSaldoGs(0.0);
        if (entity.getSaldoRs() == null) entity.setSaldoRs(0.0);
        if (entity.getSaldoDs() == null) entity.setSaldoDs(0.0);
        if (entity.getActivo() == null) entity.setActivo(true);
        return repository.save(entity);
    }

    public Boolean deleteById(Long id) {
        try {
            repository.deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // El manejo de saldo (por (caja, moneda), con lock y control de descubierto)
    // vive en TesoreriaService. Las columnas saldo_gs/rs/ds quedan como shim
    // derivado, sincronizadas por TesoreriaService.
}
