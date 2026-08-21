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

    public Page<CajaVirtual> filter(String nombre, CajaVirtualTipo tipo, Long sucursalId, Boolean activo, Pageable pageable) {
        String n = (nombre != null && !nombre.trim().isEmpty()) ? nombre.trim() : null;
        String tipoStr = tipo != null ? tipo.name() : null;
        return repository.filter(n, tipoStr, sucursalId, activo, pageable);
    }

    public List<CajaVirtual> findBySucursalId(Long sucursalId) {
        return repository.findBySucursalId(sucursalId);
    }

    public List<CajaVirtual> findActivas() {
        return repository.findByActivoTrue();
    }

    // ── Variantes acotadas por el ACL ──
    //
    // Reciben la lista de ids visibles. {@code null} = ve todas (superusuario o proceso de
    // sistema); lista vacia = no ve ninguna, que NO es lo mismo y devuelve vacio de verdad.

    public Page<CajaVirtual> findAll(List<Long> visibles, Pageable pageable) {
        if (visibles == null) return repository.findAll(pageable);
        if (visibles.isEmpty()) return Page.empty(pageable);
        return repository.findAllVisibles(visibles, pageable);
    }

    public Page<CajaVirtual> filter(List<Long> visibles, String nombre, CajaVirtualTipo tipo,
                                    Long sucursalId, Boolean activo, Pageable pageable) {
        String n = (nombre != null && !nombre.trim().isEmpty()) ? nombre.trim() : null;
        String tipoStr = tipo != null ? tipo.name() : null;
        if (visibles == null) return repository.filter(n, tipoStr, sucursalId, activo, pageable);
        if (visibles.isEmpty()) return Page.empty(pageable);
        return repository.filterVisibles(visibles, n, tipoStr, sucursalId, activo, pageable);
    }

    public List<CajaVirtual> findByTipo(List<Long> visibles, CajaVirtualTipo tipo) {
        if (visibles == null) return repository.findByTipo(tipo);
        if (visibles.isEmpty()) return java.util.Collections.emptyList();
        return repository.findByTipoAndIdIn(tipo, visibles);
    }

    public List<CajaVirtual> findBySucursalId(List<Long> visibles, Long sucursalId) {
        if (visibles == null) return repository.findBySucursalId(sucursalId);
        if (visibles.isEmpty()) return java.util.Collections.emptyList();
        return repository.findBySucursalIdAndIdIn(sucursalId, visibles);
    }

    public List<CajaVirtual> findActivas(List<Long> visibles) {
        if (visibles == null) return repository.findByActivoTrue();
        if (visibles.isEmpty()) return java.util.Collections.emptyList();
        return repository.findByActivoTrueAndIdIn(visibles);
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
