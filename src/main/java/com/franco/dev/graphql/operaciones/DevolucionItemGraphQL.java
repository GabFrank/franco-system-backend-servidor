package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Devolucion;
import com.franco.dev.domain.operaciones.DevolucionItem;
import com.franco.dev.domain.operaciones.Lote;
import com.franco.dev.graphql.operaciones.input.DevolucionItemInput;
import com.franco.dev.service.operaciones.DevolucionEdicionValidator;
import com.franco.dev.service.operaciones.DevolucionItemService;
import com.franco.dev.service.operaciones.DevolucionService;
import com.franco.dev.service.operaciones.LoteService;
import com.franco.dev.service.operaciones.RecepcionMercaderiaItemService;
import com.franco.dev.service.productos.ProductoService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class DevolucionItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final Logger logger = LoggerFactory.getLogger(DevolucionItemGraphQL.class);

    @Autowired
    private DevolucionItemService service;

    @Autowired
    private DevolucionService devolucionService;

    @Autowired
    private LoteService loteService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private com.franco.dev.service.productos.CostosPorProductoService costosPorProductoService;

    @Autowired
    private com.franco.dev.service.productos.PresentacionService presentacionService;

    @Autowired
    private com.franco.dev.service.operaciones.MotivoAveriaService motivoAveriaService;

    // Usar @Lazy para evitar problemas de dependencia circular
    @Autowired
    @Lazy
    private RecepcionMercaderiaItemService recepcionMercaderiaItemService;

    // ===== QUERY OPERATIONS =====

    public Optional<DevolucionItem> devolucionItem(Long id) {
        return service.findById(id);
    }

    public List<DevolucionItem> devolucionItemsPorDevolucion(Long devolucionId) {
        logger.info("Buscando items de devolución para Devolucion ID: {}", devolucionId);
        try {
            return service.findByDevolucionId(devolucionId);
        } catch (Exception ex) {
            logger.error("Error al buscar items por devolución: {}", ex.getMessage());
            throw new GraphQLException("Error al buscar items de devolución: " + ex.getMessage());
        }
    }

    public List<DevolucionItem> devolucionItemsPorProducto(Long productoId) {
        logger.info("Buscando items de devolución para Producto ID: {}", productoId);
        try {
            return service.findByProductoId(productoId);
        } catch (Exception ex) {
            logger.error("Error al buscar items por producto: {}", ex.getMessage());
            throw new GraphQLException("Error al buscar items de devolución: " + ex.getMessage());
        }
    }

    public List<DevolucionItem> devolucionItemsPorRecepcionItem(Long recepcionItemId) {
        logger.info("Buscando items de devolución para RecepcionItem ID: {}", recepcionItemId);
        try {
            return service.findByRecepcionMercaderiaItemId(recepcionItemId);
        } catch (Exception ex) {
            logger.error("Error al buscar items por recepción item: {}", ex.getMessage());
            throw new GraphQLException("Error al buscar items de devolución: " + ex.getMessage());
        }
    }

    public Long countDevolucionItem() {
        return service.count();
    }

    // ===== MUTATION OPERATIONS =====

    /** Costo medio actual del producto (0.0 si no hay costo registrado). */
    private Double resolverCostoMedio(Long productoId) {
        if (productoId == null) return 0.0;
        try {
            com.franco.dev.domain.productos.CostoPorProducto c =
                    costosPorProductoService.findLastByProductoId(productoId);
            if (c != null && c.getCostoMedio() != null && c.getCostoMedio() > 0.0) {
                return c.getCostoMedio();
            }
        } catch (Exception ex) {
            logger.warn("No se pudo resolver costo medio para producto {}: {}", productoId, ex.getMessage());
        }
        return 0.0;
    }

    public DevolucionItem saveDevolucionItem(DevolucionItemInput input) {
        logger.info("=== Starting DevolucionItem save operation ===");
        
        try {
            // El estado se lee de la devolucion de la fila (si el item ya existe), no del input.
            DevolucionItem existente = null;
            if (input.getId() != null) {
                existente = service.findById(input.getId())
                        .orElseThrow(() -> new GraphQLException("Item de devolucion no encontrado con ID: " + input.getId()));
            }
            Devolucion devolucion = existente != null ? existente.getDevolucion() : null;
            if (devolucion == null) {
                if (input.getDevolucionId() == null) {
                    throw new GraphQLException("devolucionId es requerido");
                }
                devolucion = devolucionService.findById(input.getDevolucionId())
                        .orElseThrow(() -> new GraphQLException("Devolucion no encontrada con ID: " + input.getDevolucionId()));
            }
            if (existente != null && DevolucionEdicionValidator.admiteDatosDeCanje(devolucion.getEstado())) {
                return guardarDatosDeCanje(existente, input);
            }
            String errorEdicion = DevolucionEdicionValidator.validarEdicion(devolucion.getEstado());
            if (errorEdicion != null) {
                throw new GraphQLException(errorEdicion);
            }

            DevolucionItem entity = new DevolucionItem();

            // Map basic fields
            entity.setId(input.getId());
            entity.setCantidad(input.getCantidad());
            entity.setLote(input.getLote());
            entity.setMotivo(input.getMotivo());
            entity.setCostoUnitario(input.getCostoUnitario());
            entity.setCantidadReingresada(input.getCantidadReingresada());
            if (input.getVencimiento() != null) {
                entity.setVencimiento(stringToDate(input.getVencimiento()).toLocalDate());
            }
            if (input.getVencimientoReingreso() != null) {
                entity.setVencimientoReingreso(stringToDate(input.getVencimientoReingreso()).toLocalDate());
            }

            // Map navigation properties
            entity.setDevolucion(devolucion);

            if (input.getProductoId() != null) {
                entity.setProducto(productoService.findById(input.getProductoId())
                    .orElseThrow(() -> new GraphQLException("Producto no encontrado con ID: " + input.getProductoId())));
            }

            if (input.getPresentacionId() != null) {
                entity.setPresentacion(presentacionService.findById(input.getPresentacionId()).orElse(null));
            }

            // El costo unitario siempre debe quedar cargado (con o sin proveedor): alimenta
            // el valor de la devolucion y la merma. Si el front no lo envia, se toma el costo
            // medio actual del producto. El costo medio es POR UNIDAD BASE; costoUnitario es
            // por unidad de la presentacion del item (se multiplica por costo x cantidad, y
            // cantidad esta en unidades de presentacion), por eso se escala por el factor.
            if (entity.getCostoUnitario() == null || entity.getCostoUnitario() <= 0.0) {
                Long prodId = entity.getProducto() != null ? entity.getProducto().getId() : input.getProductoId();
                double factor = entity.getPresentacion() != null && entity.getPresentacion().getCantidad() != null
                        ? entity.getPresentacion().getCantidad() : 1.0;
                entity.setCostoUnitario(resolverCostoMedio(prodId) * factor);
            }

            // Lote del maestro: tiene que existir y ser de este producto. Su numero pasa al
            // texto del item (lo que muestran pantallas y etiquetas) y, si el item no trae
            // vencimiento, tambien el suyo.
            if (input.getLoteId() != null) {
                Lote loteMaestro = loteService.findById(input.getLoteId())
                        .orElseThrow(() -> new GraphQLException("Lote no encontrado con ID: " + input.getLoteId()));
                Long productoDelLote = loteMaestro.getProducto() != null ? loteMaestro.getProducto().getId() : null;
                if (entity.getProducto() == null || !entity.getProducto().getId().equals(productoDelLote)) {
                    throw new GraphQLException("El lote " + loteMaestro.getNumeroLote() + " no es de este producto");
                }
                entity.setLoteMaestro(loteMaestro);
                entity.setLote(loteMaestro.getNumeroLote());
                if (entity.getVencimiento() == null) {
                    entity.setVencimiento(loteMaestro.getFechaVencimiento());
                }
            } else if (existente != null && existente.getLoteMaestro() != null
                    && existente.getLoteMaestro().getNumeroLote().equals(
                            LoteService.normalizarNumeroLote(input.getLote()))) {
                // Un cliente que todavia no conoce loteId reenvia el item con el mismo texto de
                // lote: se conserva el lote elegido en vez de borrarlo. Si cambio el texto, se
                // toma como que lo cambio a mano y el item queda sin lote del maestro.
                entity.setLoteMaestro(existente.getLoteMaestro());
            }

            if (input.getMotivoAveriaId() != null) {
                entity.setMotivoAveria(motivoAveriaService.findById(input.getMotivoAveriaId()).orElse(null));
            }

            if (input.getRecepcionMercaderiaItemId() != null) {
                entity.setRecepcionMercaderiaItem(recepcionMercaderiaItemService.findById(input.getRecepcionMercaderiaItemId())
                    .orElseThrow(() -> new GraphQLException("RecepcionMercaderiaItem no encontrado con ID: " + input.getRecepcionMercaderiaItemId())));
            }

            logger.info("=== DevolucionItem mapping completed successfully ===");
            
            DevolucionItem savedEntity = service.save(entity);
            
            logger.info("=== DevolucionItem saved with ID: {} ===", savedEntity.getId());
            
            return savedEntity;
            
        } catch (Exception ex) {
            logger.error("=== ERROR during DevolucionItem save ===");
            logger.error("Exception: {}", ex.getMessage());
            throw new GraphQLException("Error al guardar item de devolución: " + ex.getMessage());
        }
    }

    /**
     * Con la devolucion RETIRADA, de un item solo se cargan los datos del canje. El resto
     * del input se ignora: lo que salio del stock al separar no puede cambiar.
     */
    private DevolucionItem guardarDatosDeCanje(DevolucionItem item, DevolucionItemInput input) {
        String producto = item.getProducto() != null ? item.getProducto().getDescripcion() : "el item";
        String error = DevolucionEdicionValidator.validarReingreso(
                item.getCantidad(), input.getCantidadReingresada(), producto);
        if (error != null) {
            throw new GraphQLException(error);
        }
        item.setCantidadReingresada(input.getCantidadReingresada());
        item.setVencimientoReingreso(input.getVencimientoReingreso() != null
                ? stringToDate(input.getVencimientoReingreso()).toLocalDate() : null);
        // Se guarda normalizado, igual que el maestro de lotes, para que al canjear caiga en el
        // mismo lote si ya existia.
        // Un cliente que no conoce el campo no lo manda: se conserva lo que habia.
        if (input.getLoteReingreso() != null) {
            item.setLoteReingreso(LoteService.normalizarNumeroLote(input.getLoteReingreso()));
        }
        return service.save(item);
    }

    public Boolean deleteDevolucionItem(Long id) {
        logger.info("Eliminando DevolucionItem con ID: {}", id);
        try {
            DevolucionItem item = service.findById(id).orElse(null);
            if (item != null && item.getDevolucion() != null) {
                String errorEdicion = DevolucionEdicionValidator.validarEdicion(item.getDevolucion().getEstado());
                if (errorEdicion != null) {
                    throw new GraphQLException(errorEdicion);
                }
            }
            return service.deleteById(id);
        } catch (Exception ex) {
            logger.error("Error al eliminar DevolucionItem: {}", ex.getMessage());
            throw new GraphQLException("Error al eliminar item de devolución: " + ex.getMessage());
        }
    }
} 