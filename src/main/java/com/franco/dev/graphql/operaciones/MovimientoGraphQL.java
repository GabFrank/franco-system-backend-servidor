package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.dto.StockPorTipoMovimientoDto;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.graphql.operaciones.input.MovimientoStockInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.CodigoService;
import com.franco.dev.service.productos.ProductoService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class MovimientoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private MovimientoStockService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private CodigoService codigoService;

    public Page<MovimientoStock> findMovimientoStockByFilters(String inicio,
                                               String fin,
                                               List<Long> sucursalList,
                                               Long productoId,
                                               List<TipoMovimiento> tipoMovimientoList,
                                               Long usuarioId,
                                               Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        if(productoId==null || inicio == null || fin == null) return null;
        return service.findMovimientoStockWithFilters(stringToDate(inicio), stringToDate(fin), sucursalList, productoId, tipoMovimientoList, usuarioId, pageable);
    }

    public Optional<MovimientoStock> movimientoStock(Long id, Long sucId) {
        return service.findById(id);
    }

    public List<MovimientoStock> movimientosStock(int page, int size, Long sucId) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public MovimientoStock saveMovimientoStock(MovimientoStockInput input) {
        ModelMapper m = new ModelMapper();
        MovimientoStock e = m.map(input, MovimientoStock.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setProducto(productoService.findById(input.getProductoId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteMovimientoStock(Long id, Long sucId) {
        return service.deleteById(id);
    }

    public Long countMovimientoStock() {
        return service.count();
    }

    public List<MovimientoStock> movimientoStockByFecha(String inicio, String fin, Long sucId) {
        return service.findByDate(inicio, fin);
    }

//    public void findByTipoMovimientoAndReferencia(TipoMovimiento tipoMovimiento, Long referencia, Long sucId) {
//        MovimientoStock movimientoStock = service.findByTipoMovimientoAndReferenciaAndSucursalId(tipoMovimiento, referencia, sucId);
//        if (movimientoStock != null) {
//            deleteMovimientoStock(movimientoStock.getId(), movimientoStock.getSucursalId());
//        }
//    }

    public Double stockPorProducto(Long id, Long sucId) {
        return service.stockByProductoId(id);
    }

    public Double findStockWithFilters(String inicio,
                                  String fin,
                                  List<Long> sucursalList,
                                  Long productoId,
                                  List<TipoMovimiento> tipoMovimientoList,
                                  Long usuarioId){
        if(productoId==null || inicio == null || fin == null) return null;
        return service.findStockWithFilters(stringToDate(inicio), stringToDate(fin), sucursalList, productoId, tipoMovimientoList, usuarioId);
    }

    public List<StockPorTipoMovimientoDto> findStockPorTipoMovimiento(String inicio,
                                                           String fin,
                                                           List<Long> sucursalList,
                                                           Long productoId,
                                                           List<TipoMovimiento> tipoMovimientoList,
                                                           Long usuarioId){
        if(productoId==null || inicio == null || fin == null) return null;
        return service.findStockPorTipoMovimiento(stringToDate(inicio), stringToDate(fin), sucursalList, productoId, tipoMovimientoList, usuarioId);
    }

}
