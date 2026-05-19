package com.franco.dev.service.financiero;

import com.franco.dev.domain.activos.Ente;
import com.franco.dev.domain.financiero.GastoRendicion;
import com.franco.dev.domain.financiero.PreGasto;
import com.franco.dev.repository.financiero.GastoRendicionRepository;
import com.franco.dev.service.CrudService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GastoRendicionService extends CrudService<GastoRendicion, GastoRendicionRepository, Long> {

    private final GastoRendicionRepository repository;
    private final PreGastoService preGastoService;

    @Override
    public GastoRendicionRepository getRepository() {
        return repository;
    }

    @Override
    @Transactional
    public GastoRendicion save(GastoRendicion entity) {
        if (entity.getCreadoEn() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        
        // Si tiene preGasto, heredar el ente si no lo tiene (Punto 3 de la solicitud)
        if (entity.getPreGasto() != null && entity.getEnte() == null) {
            PreGasto pg = preGastoService.findByIdAndSucursalId(entity.getPreGasto().getId(), entity.getPreGasto().getSucursalId());
            if (pg != null) {
                entity.setEnte(pg.getEnte());
            }
        }

        GastoRendicion saved = super.save(entity);
        
        // Actualizar el PreGasto asociado
        if (saved.getPreGasto() != null) {
            actualizarMontoPreGasto(saved.getPreGasto().getId(), saved.getPreGasto().getSucursalId());
        }
        
        return saved;
    }

    @Transactional
    public void actualizarMontoPreGasto(Long preGastoId, Long sucursalId) {
        PreGasto preGasto = preGastoService.findByIdAndSucursalId(preGastoId, sucursalId);
        if (preGasto != null) {
            List<GastoRendicion> rendiciones = repository.findByPreGastoIdAndSucursalId(preGastoId, sucursalId);
            BigDecimal totalGastado = rendiciones.stream()
                    .map(r -> r.getMontoTotal() != null ? r.getMontoTotal() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            preGasto.setMontoGastado(totalGastado);
            BigDecimal montoRetirado = preGasto.getMontoRetirado() != null ? preGasto.getMontoRetirado() : BigDecimal.ZERO;
            preGasto.setSaldoDevolver(montoRetirado.subtract(totalGastado));
            preGastoService.save(preGasto);
        }
    }

    public List<GastoRendicion> findByPreGasto(Long preGastoId, Long sucursalId) {
        return repository.findByPreGastoIdAndSucursalId(preGastoId, sucursalId);
    }
}
