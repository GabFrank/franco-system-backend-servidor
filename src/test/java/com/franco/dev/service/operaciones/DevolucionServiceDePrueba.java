package com.franco.dev.service.operaciones;

import com.franco.dev.repository.operaciones.DevolucionRepository;
import com.franco.dev.service.empresarial.SucursalService;
import org.springframework.transaction.PlatformTransactionManager;

import static org.mockito.Mockito.mock;

/**
 * Arma un {@link DevolucionService} para tests unitarios con solo las dependencias que el test usa;
 * el resto queda en null. Reemplaza al ApplicationContext simulado que se usaba cuando el service
 * pedía sus dependencias con getBean (issue #651).
 *
 * El PlatformTransactionManager es un mock: el TransactionTemplate de las operaciones en bloque
 * ejecuta cada línea directo, sin transacción real, y una excepción de la línea sale tal cual.
 */
final class DevolucionServiceDePrueba {

    private DevolucionRepository repository;
    private DevolucionItemService devolucionItemService;
    private MovimientoStockService movimientoStockService;
    private ProductoVencimientoService productoVencimientoService;
    private RetiroDevolucionService retiroDevolucionService;
    private ColectaDevolucionService colectaDevolucionService;
    private DevolucionLoteService devolucionLoteService;
    private DevolucionConfiguracionService devolucionConfiguracionService;
    private SucursalService sucursalService;

    DevolucionServiceDePrueba con(DevolucionRepository repository) {
        this.repository = repository;
        return this;
    }

    DevolucionServiceDePrueba con(DevolucionItemService devolucionItemService) {
        this.devolucionItemService = devolucionItemService;
        return this;
    }

    DevolucionServiceDePrueba con(MovimientoStockService movimientoStockService) {
        this.movimientoStockService = movimientoStockService;
        return this;
    }

    DevolucionServiceDePrueba con(ProductoVencimientoService productoVencimientoService) {
        this.productoVencimientoService = productoVencimientoService;
        return this;
    }

    DevolucionServiceDePrueba con(RetiroDevolucionService retiroDevolucionService) {
        this.retiroDevolucionService = retiroDevolucionService;
        return this;
    }

    DevolucionServiceDePrueba con(ColectaDevolucionService colectaDevolucionService) {
        this.colectaDevolucionService = colectaDevolucionService;
        return this;
    }

    DevolucionServiceDePrueba con(DevolucionLoteService devolucionLoteService) {
        this.devolucionLoteService = devolucionLoteService;
        return this;
    }

    DevolucionServiceDePrueba con(DevolucionConfiguracionService devolucionConfiguracionService) {
        this.devolucionConfiguracionService = devolucionConfiguracionService;
        return this;
    }

    DevolucionServiceDePrueba con(SucursalService sucursalService) {
        this.sucursalService = sucursalService;
        return this;
    }

    DevolucionService build() {
        return new DevolucionService(repository, devolucionItemService, null, movimientoStockService,
                productoVencimientoService, null, null, null, null,
                retiroDevolucionService, colectaDevolucionService, devolucionLoteService,
                devolucionConfiguracionService, sucursalService, null, null,
                mock(PlatformTransactionManager.class));
    }
}
