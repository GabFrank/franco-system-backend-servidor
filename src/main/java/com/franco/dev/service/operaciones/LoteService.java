package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Lote;
import com.franco.dev.domain.operaciones.enums.EstadoLote;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.repository.operaciones.LoteRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Maestro de lotes. Ver {@link Lote} para el racional del diseño.
 */
@Service
@AllArgsConstructor
public class LoteService extends CrudService<Lote, LoteRepository, Long> {

    private final LoteRepository repository;

    @Override
    public LoteRepository getRepository() {
        return repository;
    }

    /**
     * Normaliza el número de lote: trim + mayúsculas.
     *
     * Es la única implementación de esta regla en el sistema: evita que " lote2026101 " y
     * "LOTE2026101" terminen siendo dos lotes distintos. Se aplica en el backend y no solo en la
     * UI porque mobile y las llamadas directas al GraphQL son puertas de entrada independientes.
     *
     * @return el número normalizado, o null si venía vacío.
     */
    public static String normalizarNumeroLote(String numeroLote) {
        if (numeroLote == null) return null;
        String limpio = numeroLote.trim().toUpperCase();
        return limpio.isEmpty() ? null : limpio;
    }

    /**
     * Devuelve el lote existente para (producto, número) o lo crea si es la primera vez que entra.
     *
     * Esta es la pieza que hace que dos recepciones del mismo lote sumen en el mismo saldo en vez
     * de generar dos lotes distintos: la segunda vez reutiliza la fila y respeta la fecha de
     * vencimiento ya registrada.
     *
     * Si el lote ya existe pero no tenía fecha de vencimiento y ahora sí viene una, se completa.
     * Nunca se pisa una fecha ya cargada: corregirla es una acción explícita del usuario.
     */
    @Transactional
    public Lote obtenerOCrear(Producto producto, String numeroLote, LocalDate fechaVencimiento,
                              Proveedor proveedor, Usuario usuario) {
        String numero = normalizarNumeroLote(numeroLote);
        if (producto == null || producto.getId() == null || numero == null) {
            return null;
        }

        Lote existente = repository.findByProductoIdAndNumeroLote(producto.getId(), numero).orElse(null);
        if (existente != null) {
            boolean modificado = false;
            if (existente.getFechaVencimiento() == null && fechaVencimiento != null) {
                existente.setFechaVencimiento(fechaVencimiento);
                existente.setFechaRetiro(calcularFechaRetiro(producto, fechaVencimiento));
                modificado = true;
            }
            if (existente.getProveedor() == null && proveedor != null) {
                existente.setProveedor(proveedor);
                modificado = true;
            }
            if (modificado) {
                existente.setActualizadoEn(LocalDateTime.now());
                return repository.save(existente);
            }
            return existente;
        }

        Lote lote = new Lote();
        lote.setProducto(producto);
        lote.setNumeroLote(numero);
        lote.setFechaVencimiento(fechaVencimiento);
        lote.setFechaRetiro(calcularFechaRetiro(producto, fechaVencimiento));
        lote.setProveedor(proveedor);
        lote.setUsuario(usuario);
        lote.setEstado(EstadoLote.LIBERADO);
        lote.setCreadoEn(LocalDateTime.now());
        return repository.save(lote);
    }

    /**
     * Fecha a partir de la cual la mercadería debe salir de stock, derivada de los días
     * configurados en el producto. FEFO ordena por esta fecha, no por el vencimiento: la idea es
     * sacar el producto antes de que efectivamente venza, no el último día.
     *
     * Sin vencimiento o sin días configurados no hay fecha de retiro, y FEFO cae al vencimiento.
     */
    private LocalDate calcularFechaRetiro(Producto producto, LocalDate fechaVencimiento) {
        if (fechaVencimiento == null || producto == null) return null;
        Integer dias = producto.getDiasVencimiento();
        if (dias == null || dias <= 0) return null;
        return fechaVencimiento.minusDays(dias);
    }

    /**
     * Cambia el estado de un lote. Es el mecanismo de recall: pasar a BLOQUEADO saca el lote de
     * FEFO y del mostrador en todas las sucursales, sin tocar el stock físico.
     */
    @Transactional
    public Lote cambiarEstado(Long loteId, EstadoLote estado, String observacion, Usuario usuario) {
        Lote lote = repository.findById(loteId)
                .orElseThrow(() -> new IllegalArgumentException("Lote no encontrado: " + loteId));
        lote.setEstado(estado);
        if (observacion != null) {
            lote.setObservacion(observacion);
        }
        if (usuario != null) {
            lote.setUsuario(usuario);
        }
        lote.setActualizadoEn(LocalDateTime.now());
        return repository.save(lote);
    }

    public List<Lote> findByProductoId(Long productoId) {
        return repository.findByProductoId(productoId);
    }
}
