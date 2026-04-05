package com.franco.dev.service.financiero;
 
import com.franco.dev.domain.EmbebedPrimaryKey;

import com.franco.dev.domain.financiero.EnteCuota;
import com.franco.dev.domain.financiero.EnteFinanciero;
import com.franco.dev.domain.financiero.PreGasto;
import com.franco.dev.domain.financiero.enums.EstadoPreGasto;
import com.franco.dev.repository.financiero.PreGastoRepository;
import com.franco.dev.service.CrudService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PreGastoService extends CrudService<PreGasto, PreGastoRepository, EmbebedPrimaryKey> {

    private final PreGastoRepository repository;
    private final EnteFinancieroService enteFinancieroService;
    private final EnteCuotaService enteCuotaService;

    @Value("${sucursalId:0}")
    private Long currentSucursalId;

    @Override
    public PreGastoRepository getRepository() {
        return repository;
    }

    @Override
    public PreGasto save(PreGasto entity) {
        if (entity.getId() == null) {
            Long sucursalId = entity.getSucursalId() != null ? entity.getSucursalId() : currentSucursalId; // Default sucursal from config
            Long maxId = repository.findMaxId(sucursalId);
            if(maxId == null) maxId = 0L;
            entity.setId(maxId + 1);
            entity.setSucursalId(sucursalId);
            entity.setCreadoEn(LocalDateTime.now());
            entity.setEstado(EstadoPreGasto.PENDIENTE);
        }
        if (entity.getCreadoEn() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        return super.save(entity);
    }

    public List<PreGasto> buscarPorEstado(EstadoPreGasto estado) {
        return repository.buscarPorEstado(estado.name());
    }

    public PreGasto findByIdAndSucursalId(Long id, Long sucursalId) {
        return repository.findByIdAndSucursalId(id, sucursalId);
    }

    public List<PreGasto> buscarPorEstadoYSucursal(EstadoPreGasto estado, Long sucursalId) {
        return repository.buscarPorEstadoYSucursal(estado.name(), sucursalId);
    }

    public List<PreGasto> buscarPorFuncionario(Long funcionarioId) {
        return repository.buscarPorFuncionario(funcionarioId);
    }

    public List<PreGasto> buscarPorTexto(String texto, Long sucursalId) {
        return repository.buscarPorTexto(texto, sucursalId);
    }

    public PreGasto autorizar(Long id, Long autorizadorId, Long sucId) {
        PreGasto preGasto = repository.findByIdAndSucursalId(id, sucId);
        if (preGasto == null) return null;
        preGasto.setEstado(EstadoPreGasto.AUTORIZADO);
        preGasto.setQrToken(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        
        // Descontar cuota si el PreGasto tiene un ente asociado
        if (preGasto.getEnte() != null && preGasto.getEnte().getId() != null) {
            descontarCuota(preGasto);
        }
        
        return super.save(preGasto);
    }

    public PreGasto rechazar(Long id, String motivo, Long sucId) {
        PreGasto preGasto = repository.findByIdAndSucursalId(id, sucId);
        if (preGasto == null) return null;
        preGasto.setEstado(EstadoPreGasto.RECHAZADO);
        preGasto.setMotivoRechazo(motivo);
        return super.save(preGasto);
    }

    public PreGasto enviarATramite(Long id, Long sucId) {
        PreGasto preGasto = repository.findByIdAndSucursalId(id, sucId);
        if (preGasto == null) return null;
        preGasto.setEstado(EstadoPreGasto.TRAMITE);
        return super.save(preGasto);
    }

    public PreGasto completar(Long id, Long sucId) {
        PreGasto preGasto = repository.findByIdAndSucursalId(id, sucId);
        if (preGasto == null) return null;
        preGasto.setEstado(EstadoPreGasto.COMPLETADO);
        BigDecimal montoRetirado = preGasto.getMontoRetirado() != null ? preGasto.getMontoRetirado() : BigDecimal.ZERO;
        BigDecimal montoGastado = preGasto.getMontoGastado() != null ? preGasto.getMontoGastado() : BigDecimal.ZERO;
        preGasto.setSaldoDevolver(montoRetirado.subtract(montoGastado));
        return super.save(preGasto);
    }

    /**
     * Descuenta la siguiente cuota pendiente del EnteFinanciero asociado al ente del PreGasto.
     * Marca la cuota como pagada y actualiza el montoYaPagado del EnteFinanciero.
     */
    private void descontarCuota(PreGasto preGasto) {
        Optional<EnteFinanciero> optFinanciero = enteFinancieroService.findByEnteId(preGasto.getEnte().getId());
        if (!optFinanciero.isPresent()) return;
        
        EnteFinanciero financiero = optFinanciero.get();
        
        // Buscar la próxima cuota pendiente (no pagada)
        List<EnteCuota> cuotasPendientes = enteCuotaService.findPendientesByEnteFinancieroId(financiero.getId());
        if (cuotasPendientes.isEmpty()) return;
        
        // Tomar la primera cuota pendiente (están ordenadas por numero_cuota asc)
        EnteCuota cuota = cuotasPendientes.get(0);
        cuota.setPagado(true);
        enteCuotaService.save(cuota);
        
        // Actualizar el monto ya pagado del EnteFinanciero
        BigDecimal montoYaPagado = financiero.getMontoYaPagado() != null ? financiero.getMontoYaPagado() : BigDecimal.ZERO;
        BigDecimal montoCuota = cuota.getMonto() != null ? cuota.getMonto() : BigDecimal.ZERO;
        financiero.setMontoYaPagado(montoYaPagado.add(montoCuota));
        enteFinancieroService.save(financiero);
    }
}
