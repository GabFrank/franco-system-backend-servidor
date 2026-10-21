package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Inventario;
import com.franco.dev.domain.operaciones.enums.InventarioEstado;
import com.franco.dev.print.operaciones.MovimientoPrintService;
import com.franco.dev.repository.operaciones.InventarioRepository;
import com.franco.dev.service.configuraciones.ModificacionService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La auditoria de una toma compara contra lo commiteado (#465). Leido en la transaccion de quien
 * guarda, findById devuelve la misma instancia ya modificada y el registro pierde el cambio de estado.
 */
class InventarioServiceAuditoriaTest {

    @Test
    void elEstadoAnteriorSeLeeEnUnaTransaccionAparte() {
        InventarioRepository repository = mock(InventarioRepository.class);
        ModificacionService modificacionService = mock(ModificacionService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        InventarioService service = new InventarioService(repository, mock(MovimientoStockService.class),
                mock(MovimientoPrintService.class), modificacionService, transactionManager);

        Inventario anterior = new Inventario();
        anterior.setId(7638L);
        anterior.setEstado(InventarioEstado.ABIERTO);
        Inventario nueva = new Inventario();
        nueva.setId(7638L);
        nueva.setEstado(InventarioEstado.CONCLUIDO);
        when(repository.findById(7638L)).thenReturn(Optional.of(anterior));
        when(repository.save(nueva)).thenReturn(nueva);

        service.save(nueva);

        verify(transactionManager).getTransaction(argThat(definicion ->
                definicion.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW
                        && definicion.isReadOnly()));
        verify(modificacionService).registrarActualizacion(same(anterior), same(nueva), eq("INVENTARIO"),
                any(), any());
    }
}
