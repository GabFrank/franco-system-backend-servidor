package com.franco.dev.service.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.ColectaDevolucion;
import com.franco.dev.domain.operaciones.Devolucion;
import com.franco.dev.domain.operaciones.DevolucionItem;
import com.franco.dev.domain.operaciones.MotivoAveria;
import com.franco.dev.domain.operaciones.RetiroDevolucion;
import com.franco.dev.domain.operaciones.enums.DevolucionEstado;
import com.franco.dev.domain.operaciones.enums.TipoDevolucion;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.repository.operaciones.DevolucionItemRepository;
import com.franco.dev.repository.operaciones.DevolucionRepository;
import com.franco.dev.repository.operaciones.MotivoAveriaRepository;
import com.franco.dev.repository.personas.ProveedorRepository;
import com.franco.dev.repository.personas.UsuarioRepository;
import com.franco.dev.repository.productos.PresentacionRepository;
import com.franco.dev.repository.empresarial.SucursalRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test de integración de los flujos de devolución contra la DB real (dev).
 * Llama a los servicios directamente (sin auth/GraphQL) y valida efectos reales:
 * stock, estados, cabeceras de operación y revert.
 *
 * NO corre en CI (no hay DB): se activa con -Dit.devolucion=true.
 * Cada test es @Transactional -> rollback automático, no ensucia la DB dev.
 * Si la DB no tiene datos de referencia (sucursales/proveedor/producto/motivo),
 * el test se salta (assumeTrue) en vez de fallar.
 *
 * Correr:  ./mvnw -Dit.devolucion=true -Dtest=DevolucionFlujoIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "user-dev"})
@Transactional
@EnabledIfSystemProperty(named = "it.devolucion", matches = "true")
class DevolucionFlujoIT {

    @Autowired private DevolucionService devolucionService;
    @Autowired private RetiroDevolucionService retiroDevolucionService;
    @Autowired private ColectaDevolucionService colectaDevolucionService;
    @Autowired private MovimientoStockService movimientoStockService;
    @Autowired private DevolucionRepository devolucionRepository;
    @Autowired private DevolucionItemRepository devolucionItemRepository;
    @Autowired private SucursalRepository sucursalRepository;
    @Autowired private ProveedorRepository proveedorRepository;
    @Autowired private PresentacionRepository presentacionRepository;
    @Autowired private MotivoAveriaRepository motivoAveriaRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private Sucursal origen;
    private Sucursal deposito;
    private Proveedor proveedor;
    private Presentacion presentacion;
    private Producto producto;
    private MotivoAveria motivo;
    private Usuario usuario;

    @BeforeEach
    void setup() {
        List<Sucursal> sucs = sucursalRepository.findAll().stream()
                .filter(s -> s.getId() != null && s.getId() != 0L
                        && !"SERVIDOR".equals(s.getNombre()) && !"COMPRAS".equals(s.getNombre()))
                .collect(Collectors.toList());
        assumeTrue(sucs.size() >= 2, "Se requieren 2 sucursales reales");
        origen = sucs.get(0);
        deposito = sucs.get(1);
        proveedor = proveedorRepository.findAll().stream().findFirst().orElse(null);
        assumeTrue(proveedor != null, "Se requiere un proveedor");
        presentacion = presentacionRepository.findAll().stream()
                .filter(p -> p.getProducto() != null).findFirst().orElse(null);
        assumeTrue(presentacion != null, "Se requiere una presentacion con producto");
        producto = presentacion.getProducto();
        motivo = motivoAveriaRepository.findAll().stream().findFirst().orElse(null);
        assumeTrue(motivo != null, "Se requiere un motivo de averia");
        usuario = usuarioRepository.findAll().stream().findFirst().orElse(null);
        assumeTrue(usuario != null, "Se requiere un usuario");
    }

    private Devolucion crear(boolean conProveedor) {
        Devolucion d = new Devolucion();
        d.setTipo(conProveedor ? TipoDevolucion.CON_PROVEEDOR : TipoDevolucion.SIN_PROVEEDOR);
        if (conProveedor) d.setProveedor(proveedor);
        d.setSucursalOrigen(origen);
        d.setUsuario(usuario);
        d = devolucionService.save(d);
        DevolucionItem it = new DevolucionItem();
        it.setDevolucion(d);
        it.setProducto(producto);
        it.setPresentacion(presentacion);
        it.setMotivoAveria(motivo);
        it.setCantidad(2.0);
        it.setCostoUnitario(1000.0);
        devolucionItemRepository.save(it);
        return d;
    }

    private Devolucion reload(Long id) {
        return devolucionRepository.findById(id).orElseThrow(() -> new AssertionError("no encontrada"));
    }

    private double stockOrigen() {
        Double s = movimientoStockService.stockByProductoIdAndSucursalId(producto.getId(), origen.getId());
        return s != null ? s : 0.0;
    }

    @Test
    void flujoConProveedor_separaColectaRetira_creaCabecerasYStock() {
        double stock0 = stockOrigen();
        Devolucion d = crear(true);

        devolucionService.avanzarEstado(d.getId(), DevolucionEstado.SEPARADO, usuario);
        assertEquals(DevolucionEstado.SEPARADO, reload(d.getId()).getEstado());
        double stockSep = stockOrigen();
        assertTrue(stockSep < stock0, "el stock debe bajar al separar");

        devolucionService.colectar(d.getId(), deposito.getId(), usuario);
        Devolucion col = reload(d.getId());
        assertEquals(DevolucionEstado.COLECTADO, col.getEstado());
        assertNotNull(col.getColecta(), "colecta individual debe crear cabecera");
        assertEquals(deposito.getId(), col.getSucursalUbicacion().getId());
        assertEquals(stockSep, stockOrigen(), 0.001); // colecta no mueve stock

        devolucionService.avanzarEstado(d.getId(), DevolucionEstado.RETIRADO, usuario);
        Devolucion ret = reload(d.getId());
        assertEquals(DevolucionEstado.RETIRADO, ret.getEstado());
        assertNotNull(ret.getRetiro(), "retiro individual debe crear cabecera");
        assertEquals(stockSep, stockOrigen(), 0.001); // retiro no mueve stock
    }

    @Test
    void revertirRetiro_vuelveAColectado_yCabeceraRevertida() {
        Devolucion d = crear(true);
        devolucionService.avanzarEstado(d.getId(), DevolucionEstado.SEPARADO, usuario);
        devolucionService.colectar(d.getId(), deposito.getId(), usuario);
        devolucionService.avanzarEstado(d.getId(), DevolucionEstado.RETIRADO, usuario);
        Long retiroId = reload(d.getId()).getRetiro().getId();

        devolucionService.revertirRetiro(retiroId, usuario);

        Devolucion rev = reload(d.getId());
        assertEquals(DevolucionEstado.COLECTADO, rev.getEstado(), "revertir retiro -> COLECTADO (fue colectada)");
        assertNull(rev.getRetiro());
        RetiroDevolucion header = retiroDevolucionService.findById(retiroId).orElseThrow();
        assertEquals(RetiroDevolucion.ESTADO_REVERTIDO, header.getEstado());
    }

    @Test
    void revertirColecta_conLineaRetirada_bloquea() {
        Devolucion d = crear(true);
        devolucionService.avanzarEstado(d.getId(), DevolucionEstado.SEPARADO, usuario);
        devolucionService.colectar(d.getId(), deposito.getId(), usuario);
        Long colectaId = reload(d.getId()).getColecta().getId();
        devolucionService.avanzarEstado(d.getId(), DevolucionEstado.RETIRADO, usuario);

        assertThrows(GraphQLException.class,
                () -> devolucionService.revertirColecta(colectaId, usuario));
    }

    @Test
    void revertirSeparado_vuelveAPendiente_yReingresaStock() {
        double stock0 = stockOrigen();
        Devolucion d = crear(true);
        devolucionService.avanzarEstado(d.getId(), DevolucionEstado.SEPARADO, usuario);
        assertTrue(stockOrigen() < stock0);

        devolucionService.revertirEstado(d.getId(), usuario);

        assertEquals(DevolucionEstado.PENDIENTE, reload(d.getId()).getEstado());
        assertEquals(stock0, stockOrigen(), 0.001, "revertir separado reingresa el stock");
    }

    @Test
    void flujoSinProveedor_separaDescarta_finaliza() {
        Devolucion d = crear(false);
        devolucionService.avanzarEstado(d.getId(), DevolucionEstado.SEPARADO, usuario);
        devolucionService.avanzarEstado(d.getId(), DevolucionEstado.DESCARTADO, usuario);

        Devolucion desc = reload(d.getId());
        assertEquals(DevolucionEstado.DESCARTADO, desc.getEstado());
        assertTrue(Boolean.TRUE.equals(desc.getFinalizado()));
    }

    @Test
    void colectar_aMismaSucursal_esRechazado() {
        Devolucion d = crear(true);
        devolucionService.avanzarEstado(d.getId(), DevolucionEstado.SEPARADO, usuario);
        assertThrows(GraphQLException.class,
                () -> devolucionService.colectar(d.getId(), origen.getId(), usuario));
    }
}
