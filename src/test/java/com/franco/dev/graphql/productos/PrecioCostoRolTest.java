package com.franco.dev.graphql.productos;

import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.graphql.productos.input.CostoPorProductoInput;
import com.franco.dev.graphql.productos.input.PrecioPorSucursalInput;
import com.franco.dev.service.productos.CostosPorProductoService;
import com.franco.dev.service.productos.PrecioPorSucursalService;
import com.franco.dev.service.productos.PrecioSecurityFalso;
import graphql.GraphQLException;
import org.junit.jupiter.api.Test;
import org.springframework.objenesis.ObjenesisStd;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Precios y costos se replican a las filiales y notifican: el rechazo tiene que pasar antes de leer,
 * guardar o avisar. EDITAR PRODUCTOS no alcanza para precios; para costos sí.
 */
class PrecioCostoRolTest {

    @Test
    void precioSinRolDePreciosSeRechazaSinGuardarNiNotificar() {
        PrecioPorSucursalGraphQL r = new ObjenesisStd().newInstance(PrecioPorSucursalGraphQL.class);
        PrecioPorSucursalService service = mock(PrecioPorSucursalService.class);
        PushNotificationService push = mock(PushNotificationService.class);
        ReflectionTestUtils.setField(r, "seg", new PrecioSecurityFalso("EDITAR PRODUCTOS"));
        ReflectionTestUtils.setField(r, "service", service);
        ReflectionTestUtils.setField(r, "pushNotificationService", push);

        PrecioPorSucursalInput in = new PrecioPorSucursalInput();
        in.setId(5L);
        assertThrows(GraphQLException.class, () -> r.savePrecioPorSucursal(in));
        assertThrows(GraphQLException.class, () -> r.deletePrecioPorSucursal(5L));
        verifyNoInteractions(service, push);
    }

    @Test
    void costoSinEditarProductosSeRechazaSinGuardarNiNotificar() {
        CostoPorProductoGraphQL r = new ObjenesisStd().newInstance(CostoPorProductoGraphQL.class);
        CostosPorProductoService service = mock(CostosPorProductoService.class);
        PushNotificationService push = mock(PushNotificationService.class);
        ReflectionTestUtils.setField(r, "seg", new PrecioSecurityFalso("EDITAR PRECIOS"));
        ReflectionTestUtils.setField(r, "service", service);
        ReflectionTestUtils.setField(r, "pushNotificationService", push);

        CostoPorProductoInput in = new CostoPorProductoInput();
        in.setId(5L);
        assertThrows(GraphQLException.class, () -> r.saveCostoPorProducto(in));
        verifyNoInteractions(service, push);
    }
}
