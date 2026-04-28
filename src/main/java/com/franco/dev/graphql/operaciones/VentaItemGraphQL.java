package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.graphql.operaciones.dto.ProductoVendidoEstadistica;
import com.franco.dev.graphql.operaciones.input.VentaItemInput;
import com.franco.dev.service.operaciones.VentaItemService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.productos.PrecioPorSucursalService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.service.productos.ProductoService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class VentaItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private VentaItemService service;

    @Autowired
    private VentaService ventaService;

    @Autowired
    private PresentacionService presentacionService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private PrecioPorSucursalService precioPorSucursalService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Optional<VentaItem> ventaItem(Long id, Long sucId) {
        return service.findById(new EmbebedPrimaryKey(id, sucId));
    }

    public List<VentaItem> ventaItems(int page, int size, Long sucId) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public VentaItem saveVentaItem(VentaItemInput input) {
        ModelMapper m = new ModelMapper();
        Venta venta = ventaService.findById(new EmbebedPrimaryKey(input.getVentaId(), input.getSucursalId()))
                .orElse(null);
        VentaItem e = m.map(input, VentaItem.class);
        if (venta != null) {
            if (e.getUsuario() == null)
                e.setUsuario(venta.getUsuario());
            if (e.getVenta() == null)
                e.setVenta(venta);
        }
        if (input.getProductoId() != null)
            e.setProducto(productoService.findById(input.getProductoId()).orElse(null));
        if (input.getPresentacionId() != null)
            e.setPresentacion(presentacionService.findById(input.getPresentacionId()).orElse(null));
        if (input.getPrecioVentaId() != null)
            e.setPrecioVenta(precioPorSucursalService.findById(input.getPrecioVentaId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteVentaItem(Long id, Long sucId) {
        return service.deleteById(new EmbebedPrimaryKey(id, sucId));
    }

    public Long countVentaItem() {
        return service.count();
    }

    public List<VentaItem> saveVentaItemList(List<VentaItemInput> ventaItemInputList, Long ventaId) {
        List<VentaItem> ventaItemList = new ArrayList<>();
        for (VentaItemInput v : ventaItemInputList) {
            v.setVentaId(ventaId);
            v.setActivo(true);
            VentaItem vi = saveVentaItem(v);
            ventaItemList.add(vi);
        }
        return ventaItemList;
    }

    public Boolean cancelarVentaItens(Long id, Long sucId) {
        List<VentaItem> ventaItemList = service.findByVentaId(id, sucId);
        for (VentaItem vi : ventaItemList) {
            vi.setActivo(false);
            service.save(vi);
        }
        return true;
    }

    public List<VentaItem> ventaItemListPorVentaId(Long id, Long sucId) {
        return service.findByVentaId(id, sucId);
    }

    public List<ProductoVendidoEstadistica> productosMasVendidos(String inicio, String fin, Integer limit,
            Long sucursalId, Long familiaId) {
        if (limit == null)
            limit = 10;
        LocalDateTime fechaInicio = null;
        LocalDateTime fechaFin = null;

        if (inicio != null && !inicio.isEmpty()) {
            fechaInicio = LocalDateTime.parse(inicio, DATE_FORMATTER);
        }
        if (fin != null && !fin.isEmpty()) {
            fechaFin = LocalDateTime.parse(fin, DATE_FORMATTER);
        }

        return service.obtenerProductosMasVendidos(fechaInicio, fechaFin, limit, sucursalId, familiaId);
    }
}
