package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.RecepcionMercaderiaItem;
import com.franco.dev.graphql.operaciones.input.RecepcionMercaderiaItemInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.NotaRecepcionItemService;
import com.franco.dev.service.operaciones.RecepcionMercaderiaItemService;
import com.franco.dev.service.operaciones.RecepcionMercaderiaService;
import com.franco.dev.service.productos.ProductoService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class RecepcionMercaderiaItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private RecepcionMercaderiaItemService service;

    @Autowired
    private RecepcionMercaderiaService recepcionMercaderiaService;

    @Autowired
    private NotaRecepcionItemService notaRecepcionItemService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private SucursalService sucursalService;

    /**
     * Obtiene un ítem de recepción por ID
     */
    public RecepcionMercaderiaItem recepcionMercaderiaItem(Long id) {
        if (id == null) {
            throw new GraphQLException("ID es requerido");
        }
        return service.findById(id).orElse(null);
    }

    /**
     * Obtiene ítems por ID de recepción de mercadería
     */
    public List<RecepcionMercaderiaItem> recepcionMercaderiaItemsPorRecepcion(Long recepcionId) {
        if (recepcionId == null) {
            throw new GraphQLException("ID de recepción es requerido");
        }
        return service.findByRecepcionMercaderiaId(recepcionId);
    }

    /**
     * Obtiene ítems por producto y sucursal
     */
    public List<RecepcionMercaderiaItem> recepcionMercaderiaItemsPorProductoYSucursal(Long productoId, Long sucursalId) {
        if (productoId == null || sucursalId == null) {
            throw new GraphQLException("ID de producto y sucursal son requeridos");
        }
        return service.findByProductoIdAndSucursalEntregaId(productoId, sucursalId);
    }

    /**
     * Guarda un ítem de recepción de mercadería
     */
    @Transactional
    public RecepcionMercaderiaItem saveRecepcionMercaderiaItem(RecepcionMercaderiaItemInput input) {
        ModelMapper mapper = new ModelMapper();
        RecepcionMercaderiaItem entity = mapper.map(input, RecepcionMercaderiaItem.class);

        // Mapear relaciones
        if (input.getRecepcionMercaderiaId() != null) {
            entity.setRecepcionMercaderia(recepcionMercaderiaService.findById(input.getRecepcionMercaderiaId()).orElse(null));
        }

        if (input.getNotaRecepcionItemId() != null) {
            entity.setNotaRecepcionItem(notaRecepcionItemService.findById(input.getNotaRecepcionItemId()).orElse(null));
        }

        if (input.getProductoId() != null) {
            entity.setProducto(productoService.findById(input.getProductoId()).orElse(null));
        }

        if (input.getSucursalEntregaId() != null) {
            entity.setSucursalEntrega(sucursalService.findById(input.getSucursalEntregaId()).orElse(null));
        }

        if (input.getVencimientoRecibido() != null) {
            entity.setVencimientoRecibido(stringToDate(input.getVencimientoRecibido()).toLocalDate());
        }

        // TODO: Handle creadoEn when field is added to entity

        return service.save(entity);
    }
} 