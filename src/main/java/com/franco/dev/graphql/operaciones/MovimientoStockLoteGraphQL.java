package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Lote;
import com.franco.dev.domain.operaciones.MovimientoStockLote;
import com.franco.dev.domain.operaciones.dto.StockLoteDto;
import com.franco.dev.domain.operaciones.dto.StockLotePresentacionDto;
import com.franco.dev.domain.operaciones.dto.StockLoteSucursalDto;
import com.franco.dev.domain.operaciones.enums.EstadoLote;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.operaciones.LoteService;
import com.franco.dev.service.operaciones.MovimientoStockLoteService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Consultas de stock por lote y administración del maestro de lotes.
 *
 * Las filas del ledger se generan desde el flujo de recepción
 * (RecepcionMercaderiaService.generarMovimientoStock) y, en Fase 2, desde la venta en la filial.
 * Acá solo se consulta y se cambia el estado de un lote.
 */
@Component
public class MovimientoStockLoteGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private MovimientoStockLoteService service;

    @Autowired
    private LoteService loteService;

    @Autowired
    private UsuarioService usuarioService;

    /**
     * Saldo por lote de un producto en una sucursal, ordenado por FEFO.
     */
    public List<StockLoteDto> stockPorLote(Long productoId, Long sucursalId) {
        return service.stockPorLote(productoId, sucursalId);
    }

    /**
     * Saldo por lote convertido a la presentacion con la que carga el operador.
     *
     * La conversion vive en el backend a proposito: es la misma regla que se aplica al persistir
     * la asignacion manual, y tenerla duplicada en la pantalla haria que el saldo mostrado y la
     * cantidad guardada pudieran diferir en el ultimo decimal.
     */
    public Page<StockLotePresentacionDto> stockPorLoteEnPresentacion(Long productoId, Long sucursalId,
                                                                     Long presentacionId, String numeroLote,
                                                                     int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size > 0 ? size : 10);
        return service.stockPorLoteEnPresentacion(productoId, sucursalId, presentacionId, numeroLote, pageable);
    }

    /**
     * Consulta general de stock por lote con filtros opcionales, para la pantalla
     * "Stock por lotes". Todos los filtros son opcionales y el orden es FEFO.
     */
    public Page<StockLoteDto> buscarStockPorLote(Long productoId, Long sucursalId, EstadoLote estado,
                                                  String numeroLote, String texto, String vencimientoHasta,
                                                  int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size > 0 ? size : 20);
        return service.buscarStockPorLote(productoId, sucursalId, estado, numeroLote, texto,
                vencimientoHasta, pageable);
    }

    /**
     * Desglose por sucursal del saldo de un lote. Es lo que abre la fila expandible de la
     * pantalla "Stock por lotes", donde la fila ya representa al lote completo.
     */
    public List<StockLoteSucursalDto> stockLotePorSucursal(Long loteId) {
        return service.stockLotePorSucursal(loteId);
    }

    /**
     * Lotes registrados de un producto, ordenados por FEFO. Incluye bloqueados y en cuarentena.
     */
    public List<Lote> lotesPorProducto(Long productoId) {
        return loteService.findByProductoId(productoId);
    }

    /**
     * Desglose por lote de un movimiento de stock agregado.
     */
    public List<MovimientoStockLote> movimientoStockLotePorMovimiento(Long movimientoStockId, Long sucursalId) {
        return service.findByMovimientoStock(movimientoStockId, sucursalId);
    }

    /**
     * Cambia el estado de un lote (recall). Al pasar a BLOQUEADO o CUARENTENA el lote deja de
     * entrar en FEFO y no se puede vender, pero el stock físico queda intacto y se sigue contando
     * en el inventario.
     */
    public Lote cambiarEstadoLote(Long loteId, EstadoLote estado, String observacion, Long usuarioId) {
        if (loteId == null || estado == null) {
            throw new GraphQLException("loteId y estado son requeridos");
        }
        Usuario usuario = usuarioId != null ? usuarioService.findById(usuarioId).orElse(null) : null;
        try {
            return loteService.cambiarEstado(loteId, estado, observacion, usuario);
        } catch (IllegalArgumentException e) {
            throw new GraphQLException(e.getMessage());
        }
    }
}
