package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.financiero.RetiroDetalle;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.repository.financiero.RetiroRepository;
import com.franco.dev.service.empresarial.SucursalService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Procesa un retiro individual hacia la caja mayor, en su propia transacción. Es un
 * bean separado de {@link RetiroTesoreriaScheduler} a propósito: si {@code procesar}
 * viviera en el scheduler y se invocara por {@code this.procesar(...)}, el proxy de
 * Spring no aplicaría {@code @Transactional} (self-invocation) y se perdería la
 * atomicidad — todos los movimientos + el marcado de la fila deben committear juntos
 * para que la idempotencia (guard anti doble-ingreso) sea real.
 */
@Service
@AllArgsConstructor
public class RetiroTesoreriaProcesador {

    private static final Logger log = LoggerFactory.getLogger(RetiroTesoreriaProcesador.class);

    private final RetiroRepository retiroRepository;
    private final RetiroDetalleService retiroDetalleService;
    private final CajaVirtualService cajaVirtualService;
    private final TesoreriaService tesoreriaService;
    private final SucursalService sucursalService;

    /** Postea el ingreso a la caja mayor y marca el retiro, todo en una transacción.
     *  {@code usuario} = responsable del ingreso (operador de tesorería); el poller pasa null. */
    @Transactional
    public boolean procesar(Long retiroId, Long sucursalId, com.franco.dev.domain.personas.Usuario usuario) {
        // Guard anti doble-ingreso: relee dentro de la transacción y confirma que sigue pendiente.
        Retiro fresh = retiroRepository.findByIdAndSucursalId(retiroId, sucursalId);
        if (fresh == null || fresh.getCajaVirtualId() == null || fresh.getMovimientoCajaVirtualId() != null) {
            return false;
        }
        CajaVirtual caja = cajaVirtualService.findById(fresh.getCajaVirtualId()).orElse(null);
        if (caja == null) {
            log.warn("RetiroTesoreriaProcesador: caja mayor {} no existe (retiro {})", fresh.getCajaVirtualId(), fresh.getId());
            return false;
        }

        List<RetiroDetalle> detalles = retiroDetalleService.findByRetiroId(fresh.getId(), fresh.getSucursalId());
        Map<Long, Double> porMoneda = new HashMap<>();
        Map<Long, Moneda> monedas = new HashMap<>();
        for (RetiroDetalle d : detalles) {
            if (d.getMoneda() == null || d.getCantidad() == null) continue;
            porMoneda.merge(d.getMoneda().getId(), d.getCantidad(), Double::sum);
            monedas.putIfAbsent(d.getMoneda().getId(), d.getMoneda());
        }

        String nombreSucursal = sucursalService.findById(fresh.getSucursalId())
                .map(com.franco.dev.domain.empresarial.Sucursal::getNombre)
                .orElse("Sucursal " + fresh.getSucursalId());
        String descripcion = "Retiro #" + fresh.getId() + " - " + nombreSucursal;

        Long ultimoMovId = null;
        for (Map.Entry<Long, Double> e : porMoneda.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) continue;
            MovimientoCajaVirtual mov = new MovimientoCajaVirtual();
            mov.setCajaVirtual(caja);
            mov.setTipoMovimiento(CajaVirtualTipoMovimiento.INGRESO);
            mov.setCantidad(e.getValue());
            mov.setMoneda(monedas.get(e.getKey()));
            mov.setUsuario(usuario);
            mov.setDescripcion(descripcion);
            mov.setReferenciaId(fresh.getId());
            mov.setOrigenTipo(OrigenMovimientoTipo.RETIRO_CAJA);
            mov.setOrigenId(fresh.getId());
            MovimientoCajaVirtual posteado = tesoreriaService.registrar(mov);
            ultimoMovId = posteado.getId();
        }

        fresh.setMovimientoCajaVirtualId(ultimoMovId != null ? ultimoMovId : -1L);
        retiroRepository.save(fresh);
        return true;
    }
}
