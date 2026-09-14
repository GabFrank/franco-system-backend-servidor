package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;
import static com.franco.dev.utilitarios.DateUtils.stringToDateEndOfDay;

/**
 * Consultas de movimientos de caja virtual + fachada de escritura hacia
 * {@link TesoreriaService} (el núcleo que toca el saldo con lock). Los consumidores
 * existentes (RRHH, GraphQL) siguen llamando {@code registrarMovimiento}/
 * {@code realizarTransferencia} sin cambios; la lógica de saldo vive en TesoreriaService.
 */
@Service
@AllArgsConstructor
public class MovimientoCajaVirtualService {

    private final MovimientoCajaVirtualRepository repository;
    private final TesoreriaService tesoreriaService;

    public Optional<MovimientoCajaVirtual> findById(Long id) {
        return repository.findById(id);
    }

    public Page<MovimientoCajaVirtual> findByCajaVirtualId(Long cajaVirtualId, Pageable pageable) {
        return repository.findByCajaVirtualIdOrderByCreadoEnDesc(cajaVirtualId, pageable);
    }

    public Page<MovimientoCajaVirtual> findByCajaVirtualIdAndFecha(Long cajaVirtualId, String inicio, String fin, Pageable pageable) {
        return repository.findByCajaVirtualIdAndCreadoEnBetweenOrderByCreadoEnDesc(
                cajaVirtualId, stringToDate(inicio), stringToDate(fin), pageable);
    }

    /**
     * Filtro combinado de movimientos: fecha, tipo, moneda (todos opcionales) y soloActivos (oculta anulados).
     * {@code fin} es inclusivo: el datepicker manda el día a las 00:00, y sin llevarlo al fin del día
     * los movimientos del día elegido como "Hasta" quedaban afuera.
     */
    public Page<MovimientoCajaVirtual> filter(Long cajaVirtualId, String desde, String fin,
                                              com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento tipo,
                                              Long monedaId, boolean soloActivos, Pageable pageable) {
        return repository.filter(cajaVirtualId, inicioRango(desde), finRango(fin),
                tipo != null ? tipo.name() : null, monedaId, soloActivos, pageable);
    }

    /** Igual que {@link #filter} pero sin paginar, para el reporte de movimientos. */
    public List<MovimientoCajaVirtual> filterList(Long cajaVirtualId, String desde, String fin,
                                                  com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento tipo,
                                                  Long monedaId, boolean soloActivos) {
        return repository.filterList(cajaVirtualId, inicioRango(desde), finRango(fin),
                tipo != null ? tipo.name() : null, monedaId, soloActivos);
    }

    static LocalDateTime inicioRango(String desde) {
        return (desde != null && !desde.trim().isEmpty()) ? stringToDate(desde) : null;
    }

    static LocalDateTime finRango(String fin) {
        return (fin != null && !fin.trim().isEmpty()) ? stringToDateEndOfDay(fin) : null;
    }

    /** Registra un movimiento y actualiza el saldo de la caja de forma atómica (delega en TesoreriaService). */
    public MovimientoCajaVirtual registrarMovimiento(MovimientoCajaVirtual movimiento) {
        return tesoreriaService.registrar(movimiento);
    }

    /** Transferencia entre dos cajas virtuales (delega en TesoreriaService, con lock en orden canónico). */
    public Boolean realizarTransferencia(Long origenId, Long destinoId, Double cantidad,
                                         Moneda moneda, String descripcion, Usuario usuario) {
        return tesoreriaService.transferir(origenId, destinoId, cantidad, moneda, descripcion, usuario);
    }

    /** Anula un movimiento manual con contra-movimiento (bloquea si proviene de otro módulo). */
    public MovimientoCajaVirtual anularMovimiento(Long movimientoId, String motivo, Usuario usuario) {
        return tesoreriaService.anular(movimientoId, motivo, usuario);
    }

    /**
     * Contra-movimiento que revierte el efecto de un movimiento, sin el guard cross-módulo:
     * lo llama el módulo dueño de la operación al anularla (RRHH, CPP...).
     *
     * <p>Existe para que los dueños no armen el AJUSTE a mano. Hacerlo a mano se ve simétrico
     * pero no lo es: el egreso entra por {@code abs().negate()} y el AJUSTE conserva el signo,
     * así que copiar el monto sin negar solo revierte cuando el monto es positivo — con un
     * monto negativo vuelve a descontar. {@code TesoreriaService.revertir} recalcula el efecto
     * y lo niega, y además marca el original como inactivo.</p>
     */
    public MovimientoCajaVirtual revertirMovimiento(Long movimientoId, String motivo, Usuario usuario) {
        return tesoreriaService.revertir(tesoreriaService.findMovimiento(movimientoId), motivo, usuario);
    }
}
