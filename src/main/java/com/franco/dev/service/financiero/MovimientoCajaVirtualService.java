package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

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

    /** Filtro combinado de movimientos: fecha, tipo, moneda (todos opcionales) y soloActivos (oculta anulados). */
    public Page<MovimientoCajaVirtual> filter(Long cajaVirtualId, String desde, String fin,
                                              com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento tipo,
                                              Long monedaId, boolean soloActivos, Pageable pageable) {
        return repository.filter(cajaVirtualId,
                (desde != null && !desde.isEmpty()) ? stringToDate(desde) : null,
                (fin != null && !fin.isEmpty()) ? stringToDate(fin) : null,
                tipo != null ? tipo.name() : null, monedaId, soloActivos, pageable);
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
}
