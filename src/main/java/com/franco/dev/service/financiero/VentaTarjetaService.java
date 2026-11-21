package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.ConfiguracionVentaTarjeta;
import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.repository.financiero.ConfiguracionVentaTarjetaRepository;
import com.franco.dev.repository.financiero.VentaTarjetaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class VentaTarjetaService extends CrudService<VentaTarjeta, VentaTarjetaRepository, EmbebedPrimaryKey> {

    private final VentaTarjetaRepository repository;

    /** Solo para leer la ventana del chequeo por codigo de autorizacion. */
    private final ConfiguracionVentaTarjetaRepository configuracionRepository;

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

    /**
     * El cupon de este cobro ya esta COMPLETADO en otra venta vigente de la sucursal (#195).
     * <p>
     * Lo pregunta la reactivacion de una venta antes de devolver su cobro a COMPLETADO: mientras la venta
     * estuvo cancelada su cupon quedo libre, y si otra venta lo uso, revivirlo deja dos cobros con el mismo
     * cupon, que ademas baja al filial por la replica. Es la regla de motivoCuponNoUsable del filial (QR, o
     * codigo de autorizacion en la misma terminal, en la ventana, con el mismo monto o sin monto), contra
     * COMPLETADO solamente. La referencia del proveedor no entra: el central no la guarda en venta_tarjeta.
     */
    public boolean cuponCompletadoEnOtra(VentaTarjeta vt) {
        String qr = vt.getQrCrudo() != null ? vt.getQrCrudo().trim() : "";
        if (!qr.isEmpty() && repository.countQrCompletadoEnOtra(vt.getSucursalId(), qr, vt.getId(),
                VentaEstado.CANCELADA) > 0) {
            return true;
        }
        String codigo = vt.getCodigoAutorizacion() != null ? vt.getCodigoAutorizacion().trim().toUpperCase() : "";
        if (codigo.isEmpty()) return false;
        Long terminalPosId = vt.getTerminalPos() != null ? vt.getTerminalPos().getId() : null;
        LocalDateTime desde = LocalDateTime.now().minusHours(horasVentanaDuplicado());
        for (VentaTarjeta otro : repository.completadosPorCodigo(vt.getSucursalId(), codigo, terminalPosId,
                desde, vt.getId(), VentaEstado.CANCELADA)) {
            BigDecimal a = vt.getMontoEscaneado();
            BigDecimal b = otro.getMontoEscaneado();
            // Si falta un monto no alcanza para descartar: se prefiere avisar de mas (igual que el filial).
            if (a == null || b == null || a.compareTo(b) == 0) return true;
        }
        return false;
    }

    /**
     * La ventana del duplicado por codigo. Se lee sin findOrCreate(), que crearia la configuracion dentro
     * de una cancelacion; 24 h si no hay fila o si vale 0 o menos, como horasVentanaDuplicadoEfectivo del
     * filial.
     */
    int horasVentanaDuplicado() {
        List<ConfiguracionVentaTarjeta> lista = configuracionRepository.findAllByOrderByIdAsc();
        Integer horas = lista.isEmpty() ? null : lista.get(0).getHorasVentanaDuplicado();
        return horas == null || horas <= 0 ? 24 : horas;
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
