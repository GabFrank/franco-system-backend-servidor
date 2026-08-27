package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Lote;
import com.franco.dev.domain.operaciones.MovimientoStockLote;
import com.franco.dev.domain.operaciones.dto.AjusteStockLoteResultadoDto;
import com.franco.dev.domain.operaciones.dto.ClienteLoteDto;
import com.franco.dev.domain.operaciones.dto.LoteDeProductoDto;
import com.franco.dev.domain.operaciones.dto.LoteSinContarDto;
import com.franco.dev.domain.operaciones.dto.MovimientoLoteDto;
import com.franco.dev.domain.operaciones.dto.ResumenStockLoteDto;
import com.franco.dev.domain.operaciones.dto.StockLoteDto;
import com.franco.dev.domain.operaciones.dto.StockLotePresentacionDto;
import com.franco.dev.domain.operaciones.dto.StockLoteSucursalDto;
import com.franco.dev.domain.operaciones.enums.EstadoLote;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.operaciones.input.AjusteStockLoteInput;
import com.franco.dev.service.operaciones.AjusteStockLoteService;
import com.franco.dev.service.operaciones.InventarioLoteService;
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

    @Autowired
    private AjusteStockLoteService ajusteStockLoteService;

    @Autowired
    private InventarioLoteService inventarioLoteService;

    @Autowired
    private com.franco.dev.service.productos.ProductoService productoService;

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
    public Page<StockLoteDto> buscarStockPorLote(Long productoId, Long sucursalId, Long proveedorId,
                                                  EstadoLote estado, String numeroLote, String texto,
                                                  String vencimientoDesde, String vencimientoHasta,
                                                  int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size > 0 ? size : 20);
        return service.buscarStockPorLote(productoId, sucursalId, proveedorId, estado, numeroLote,
                texto, vencimientoDesde, vencimientoHasta, pageable);
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
     * Historial de un lote: los movimientos que lo tocaron, del mas reciente al mas viejo.
     *
     * Es la contraparte de cambiarEstadoLote: bloquear el lote lo saca de circulacion, pero para
     * que el recall sirva hay que poder decir de que compra vino y a que ventas fue.
     */
    public Page<MovimientoLoteDto> movimientosPorLote(Long loteId, Long sucursalId,
                                                      TipoMovimiento tipoMovimiento,
                                                      int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size > 0 ? size : 20);
        return service.movimientosPorLote(loteId, sucursalId,
                tipoMovimiento != null ? tipoMovimiento.name() : null, pageable);
    }

    /**
     * A que clientes se le vendio el lote, una fila por venta.
     *
     * Es lo que hace accionable el recall: cambiar el estado saca el lote del mostrador, pero
     * avisar exige saber a quien llamar.
     *
     * Con rastreable en true devuelve las ventas con cliente identificado; en false, las de
     * mostrador, que no identifican a nadie pero igual se pueden abrir para ver que se vendio.
     */
    public Page<ClienteLoteDto> clientesPorLote(Long loteId, Long sucursalId, Boolean rastreable,
                                                int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size > 0 ? size : 20);
        return service.clientesPorLote(loteId, sucursalId, rastreable, pageable);
    }

    /**
     * Buscador paginado de lotes de un producto, con el saldo de cada uno en la sucursal.
     *
     * Alimenta el buscador genérico del ajuste de stock. Incluye los lotes con saldo cero en esa
     * sucursal: son los que hacen falta para trazar mercadería que ya estaba sin lote asignado.
     */
    public Page<LoteDeProductoDto> buscarLotesDeProducto(Long productoId, Long sucursalId,
                                                          String texto, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size > 0 ? size : 10);
        return loteService.buscarLotesDeProducto(productoId, sucursalId, texto, pageable);
    }

    /**
     * Las tres cuentas del producto en una sucursal: existencia, lo atribuido a lotes reales y lo
     * que quedó sin trazar. Es lo que la pantalla de ajuste muestra antes de confirmar.
     */
    public ResumenStockLoteDto resumenStockLote(Long productoId, Long sucursalId) {
        return ajusteStockLoteService.resumen(productoId, sucursalId);
    }

    /**
     * Ajusta el stock de UN lote en UNA sucursal, escribiendo el movimiento agregado y su desglose
     * en la misma transacción.
     *
     * Es el camino que faltaba para que una corrección manual de stock de un producto con control
     * de lote no rompa la correspondencia entre las dos cuentas. Ver
     * {@link AjusteStockLoteService} para las dos operaciones que soporta.
     */
    public AjusteStockLoteResultadoDto ajustarStockLote(AjusteStockLoteInput input) {
        try {
            return ajusteStockLoteService.ajustar(input);
        } catch (IllegalArgumentException e) {
            throw new GraphQLException(e.getMessage());
        }
    }

    /**
     * Alta manual de un lote, sin mover stock.
     *
     * Es el camino que faltaba para el conteo: el operador tiene el envase en la mano, el lote no
     * está en el sistema y necesita registrarlo para poder contarlo. Nace con saldo cero; el stock
     * se lo pone la finalización de la toma.
     *
     * ⚠️ <b>El nombre lleva el sufijo del dominio a propósito.</b> Se llamó {@code crearLote} y
     * chocó con {@link com.franco.dev.graphql.financiero.LoteDEGraphQL#crearLote()}, que crea un
     * lote de documentos electrónicos para SIFEN y no lleva argumentos. GraphQL fusiona los
     * {@code extend type Mutation} por nombre de campo: ganaba el de SIFEN, el arranque no se
     * quejaba y la app recibía {@code Unknown field argument productoId @ 'crearLote'}.
     */
    public Lote crearLoteProducto(Long productoId, String numeroLote, String fechaVencimiento,
                                  String fechaRetiro, String observacion, Long usuarioId) {
        if (productoId == null) {
            throw new GraphQLException("productoId es requerido");
        }
        Producto producto = productoService.findById(productoId).orElse(null);
        if (producto == null) {
            throw new GraphQLException("No existe el producto " + productoId + ".");
        }
        Usuario usuario = usuarioId != null ? usuarioService.findById(usuarioId).orElse(null) : null;
        try {
            return loteService.crear(producto, numeroLote, aFecha(fechaVencimiento, "vencimiento"),
                    aFecha(fechaRetiro, "retiro"), observacion, usuario);
        } catch (IllegalArgumentException e) {
            throw new GraphQLException(e.getMessage());
        }
    }

    /**
     * Carga o corrige las fechas del maestro de un lote.
     *
     * Es la puerta que faltaba: la fecha de retiro solo se podía setear al CREAR el lote —desde la
     * recepción o desde el ajuste—, así que un lote viejo sin retiro no había forma de completarlo
     * y uno mal tipeado no había forma de corregirlo.
     *
     * El cambio es GLOBAL: el maestro es uno por (producto, número de lote) y replica MAIN_TO_ALL,
     * así que reordena el FEFO en todas las sucursales. Quien llama tiene que haberlo dicho en
     * pantalla.
     *
     * Las fechas llegan como texto {@code yyyy-MM-dd}, igual que en {@link AjusteStockLoteInput}.
     */
    public Lote actualizarFechasLote(Long loteId, String fechaVencimiento, String fechaRetiro,
                                     String motivo, Long usuarioId) {
        if (loteId == null) {
            throw new GraphQLException("loteId es requerido");
        }
        Usuario usuario = usuarioId != null ? usuarioService.findById(usuarioId).orElse(null) : null;
        try {
            return loteService.actualizarFechas(loteId, aFecha(fechaVencimiento, "vencimiento"),
                    aFecha(fechaRetiro, "retiro"), motivo, usuario);
        } catch (IllegalArgumentException e) {
            throw new GraphQLException(e.getMessage());
        }
    }

    /**
     * Los lotes con saldo que ningún renglón de la toma contó.
     *
     * Existe para poder avisarlo ANTES de finalizar: la finalización deja esos productos enteros
     * fuera del ajuste, y sin esta consulta el operador se entera después de cerrar la toma.
     */
    public List<LoteSinContarDto> lotesSinContar(Long inventarioId) {
        if (inventarioId == null) {
            throw new GraphQLException("inventarioId es requerido");
        }
        return inventarioLoteService.lotesSinContar(inventarioId);
    }

    /**
     * Una fecha vacía es "no la mandé", no un error: el input distingue nulo de dato, y un texto
     * en blanco llega cuando el formulario nunca se tocó.
     */
    private java.time.LocalDate aFecha(String valor, String cual) {
        if (valor == null || valor.trim().isEmpty()) {
            return null;
        }
        try {
            return java.time.LocalDate.parse(valor.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new GraphQLException("La fecha de " + cual + " no tiene el formato yyyy-MM-dd.");
        }
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
