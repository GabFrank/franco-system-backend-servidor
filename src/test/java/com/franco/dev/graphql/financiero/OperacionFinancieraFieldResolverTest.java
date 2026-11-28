package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.MovimientoBancario;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.OperacionFinanciera;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.MovimientoBancarioRepository;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code anulacion} de una operación financiera (desk #721): sale del contra-movimiento que dejó la anulación,
 * de caja si lo hay y si no de banco, y el motivo se limpia del envoltorio que arma {@code revertir}.
 */
class OperacionFinancieraFieldResolverTest {

    private MovimientoCajaVirtualRepository cajaRepository;
    private MovimientoBancarioRepository bancoRepository;
    private OperacionFinancieraFieldResolver resolver;

    @BeforeEach
    void setUp() {
        cajaRepository = mock(MovimientoCajaVirtualRepository.class);
        bancoRepository = mock(MovimientoBancarioRepository.class);
        resolver = new OperacionFinancieraFieldResolver(cajaRepository, bancoRepository);
    }

    private static OperacionFinanciera operacion(long id, boolean anulada) {
        OperacionFinanciera op = new OperacionFinanciera();
        op.setId(id);
        op.setAnulado(anulada);
        return op;
    }

    @Test
    void noAnuladaDevuelveNullSinConsultar() {
        assertNull(resolver.anulacion(operacion(5L, false)));
        verifyNoInteractions(cajaRepository, bancoRepository);
    }

    @Test
    void tomaElPrimerContraMovimientoDeCaja() {
        Usuario quien = new Usuario();
        LocalDateTime cuando = LocalDateTime.of(2026, 10, 13, 14, 22);
        MovimientoCajaVirtual primero = new MovimientoCajaVirtual();
        primero.setDescripcion("ANULACION: PRUEBA ENVIO (mov #1030)");
        primero.setUsuario(quien);
        primero.setCreadoEn(cuando);
        MovimientoCajaVirtual segundo = new MovimientoCajaVirtual();
        segundo.setDescripcion("ANULACION: OTRO (mov #1031)");
        when(cajaRepository.findAnulacionesDeOperacion(26L)).thenReturn(Arrays.asList(primero, segundo));

        OperacionFinancieraAnulacion a = resolver.anulacion(operacion(26L, true));

        assertEquals("PRUEBA ENVIO", a.getMotivo());
        assertSame(quien, a.getUsuario());
        assertEquals(cuando, a.getFecha());
        verifyNoInteractions(bancoRepository);
    }

    @Test
    void sinPatasDeCajaLeeElBanco() {
        Usuario quien = new Usuario();
        MovimientoBancario contra = new MovimientoBancario();
        contra.setDescripcion("ANULACION: TRANSFERENCIA MAL CARGADA (mov #138)");
        contra.setUsuario(quien);
        when(cajaRepository.findAnulacionesDeOperacion(30L)).thenReturn(Collections.emptyList());
        when(bancoRepository.findAnulacionesDeOperacion(30L)).thenReturn(Collections.singletonList(contra));

        OperacionFinancieraAnulacion a = resolver.anulacion(operacion(30L, true));

        assertEquals("TRANSFERENCIA MAL CARGADA", a.getMotivo());
        assertSame(quien, a.getUsuario());
    }

    @Test
    void anuladaSinContraMovimientoDevuelveNull() {
        when(cajaRepository.findAnulacionesDeOperacion(31L)).thenReturn(Collections.emptyList());
        when(bancoRepository.findAnulacionesDeOperacion(31L)).thenReturn(Collections.emptyList());

        assertNull(resolver.anulacion(operacion(31L, true)));
    }

    @Test
    void motivoConParentesisYSaltoDeLinea() {
        assertEquals("ERROR (VER TICKET (3))", OperacionFinancieraFieldResolver.motivo("ANULACION: ERROR (VER TICKET (3)) (mov #7)"));
        assertEquals("LINEA 1\nLINEA 2", OperacionFinancieraFieldResolver.motivo("ANULACION: LINEA 1\nLINEA 2 (mov #7)"));
    }

    @Test
    void otroFormatoOTruncadoSeDevuelveEntero() {
        String truncada = "ANULACION: MOTIVO LARGO QUE SE CORTO SIN EL SUFI";
        assertEquals(truncada, OperacionFinancieraFieldResolver.motivo(truncada));
        assertEquals("Anulación operación financiera #20",
                OperacionFinancieraFieldResolver.motivo("ANULACION: Anulación operación financiera #20 (mov #998)"));
    }

    @Test
    void motivoEnBlancoEsNull() {
        assertNull(OperacionFinancieraFieldResolver.motivo("ANULACION:  (mov #9)"));
        assertNull(OperacionFinancieraFieldResolver.motivo(null));
    }
}
