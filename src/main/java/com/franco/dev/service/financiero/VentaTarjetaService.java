package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.repository.financiero.VentaTarjetaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class VentaTarjetaService extends CrudService<VentaTarjeta, VentaTarjetaRepository, EmbebedPrimaryKey> {

    private final VentaTarjetaRepository repository;

    @Override
    public VentaTarjetaRepository getRepository() {
        return repository;
    }

    public VentaTarjeta findByVentaIdAndSucursalId(Long ventaId, Long sucursalId) {
        VentaTarjeta pendiente = repository.findFirstByVentaIdAndSucursalIdAndEstadoOrderByIdAsc(ventaId, sucursalId, "PENDIENTE");
        return pendiente != null ? pendiente : repository.findFirstByVentaIdAndSucursalIdOrderByIdAsc(ventaId, sucursalId);
    }

    public VentaTarjeta findByIdAndSucursalId(Long id, Long sucursalId) {
        return repository.findByIdAndSucursalId(id, sucursalId);
    }

    public List<VentaTarjeta> findByCajaIdAndSucursalId(Long cajaId, Long sucursalId) {
        return repository.findByCajaIdAndSucursalIdOrderByCreadoEnDesc(cajaId, sucursalId);
    }

    public List<VentaTarjeta> findAllByVentaIdAndSucursalId(Long ventaId, Long sucursalId) {
        return repository.findByVentaIdAndSucursalIdOrderByIdAsc(ventaId, sucursalId);
    }

    public Long countVentasTarjetaSinRegistrar(Long cajaId, Long sucursalId) {
        return repository.countByCajaIdAndSucursalIdAndEstado(cajaId, sucursalId, "PENDIENTE");
    }

    public Page<VentaTarjeta> filter(Long id, Long ventaId, Long sucursalId, String terminalDescripcion,
                                     String terminalCodigo, String estado,
                                     LocalDateTime fechaDesde, LocalDateTime fechaHasta,
                                     int page, int size) {
        return repository.filterVentaTarjeta(id, ventaId, sucursalId, terminalDescripcion, terminalCodigo,
                estado, fechaDesde, fechaHasta, PageRequest.of(page, size));
    }

    public List<VentaTarjeta> filterList(Long id, Long ventaId, Long sucursalId, String terminalDescripcion,
                                          String terminalCodigo, String estado,
                                          LocalDateTime fechaDesde, LocalDateTime fechaHasta) {
        return repository.filterVentaTarjetaList(id, ventaId, sucursalId, terminalDescripcion, terminalCodigo,
                estado, fechaDesde, fechaHasta);
    }
}
