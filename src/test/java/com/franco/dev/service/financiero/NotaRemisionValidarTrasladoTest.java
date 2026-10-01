package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.NotaRemision;
import com.franco.dev.domain.financiero.NotaRemisionItem;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.financiero.enums.MotivoEmisionNotaRemision;
import com.franco.dev.domain.financiero.enums.OrigenNotaRemision;
import com.franco.dev.domain.financiero.enums.ResponsableEmisionNr;
import com.franco.dev.repository.financiero.NotaRemisionItemRepository;
import com.franco.dev.repository.financiero.NotaRemisionRepository;
import com.franco.dev.repository.financiero.TimbradoDetalleRepository;
import com.franco.dev.service.sifen.util.SerieDeNumeracionValidator;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Datos de traslado que SIFEN rechaza se cortan antes de numerar: un rechazo quema el número de la
 * serie. Sucursal 13, 2026-09-30: la NRE 31 salió con la entrega «KATUETE» y el código 5626 (2208) y
 * la 41 y la 42 con el fin del traslado un mes antes que el inicio (2108).
 */
class NotaRemisionValidarTrasladoTest {

    private static final Long TIMBRADO = 118L;

    private NotaRemisionRepository repository;
    private TimbradoDetalleRepository timbradoDetalleRepository;
    private NotaRemisionService service;
    private final List<Object[]> ciudades = new ArrayList<>();

    @BeforeEach
    void setUp() {
        repository = mock(NotaRemisionRepository.class);
        NotaRemisionItemRepository itemRepository = mock(NotaRemisionItemRepository.class);
        timbradoDetalleRepository = mock(TimbradoDetalleRepository.class);
        service = new NotaRemisionService(repository, itemRepository, timbradoDetalleRepository,
                mock(FacturacionSecurityService.class), mock(SerieDeNumeracionValidator.class));

        TimbradoDetalle timbrado = new TimbradoDetalle();
        timbrado.setId(TIMBRADO);
        timbrado.setActivo(true);
        when(timbradoDetalleRepository.lockById(TIMBRADO)).thenReturn(Optional.of(timbrado));
        when(timbradoDetalleRepository.findCiudadesConCodigo()).thenReturn(ciudades);
        when(repository.findActivasByTransferenciaId(any())).thenReturn(Collections.emptyList());
        when(repository.save(any(NotaRemision.class))).thenAnswer(i -> i.getArgument(0));
        when(itemRepository.save(any(NotaRemisionItem.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.siguienteId()).thenReturn(85L);
        when(itemRepository.siguienteId()).thenReturn(900L);

        ciudades.add(new Object[]{"4738", "SALTO DEL GUAIRA"});
        ciudades.add(new Object[]{"5626", "FRANCISCO CABALLERO ALVAREZ"});
        ciudades.add(new Object[]{"4881", "CURUGUATY (MUNICIPIO)"});
    }

    // ----- fechas -----

    @Test
    void finAnteriorAlInicioNoSeNumera() {
        NotaRemision nota = nota();
        nota.setFechaInicioTraslado(LocalDate.of(2026, 10, 1));
        nota.setFechaFinTraslado(LocalDate.of(2026, 9, 1));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.crear(nota, items()));

        assertTrue(e.getMessage().contains("01/09/2026") && e.getMessage().contains("01/10/2026"), e.getMessage());
        verify(timbradoDetalleRepository, never()).lockById(anyLong());
        verify(repository, never()).save(any());
    }

    @Test
    void sinInicioSeComparaContraLaFechaDeLaNota() {
        NotaRemision nota = nota();
        nota.setFecha(LocalDateTime.of(2026, 9, 30, 16, 55));
        nota.setFechaFinTraslado(LocalDate.of(2026, 9, 1));

        assertThrows(GraphQLException.class, () -> service.crear(nota, items()));
        verify(repository, never()).save(any());
    }

    @Test
    void finIgualAlInicioOSinFinPasa() {
        NotaRemision mismoDia = nota();
        mismoDia.setFechaInicioTraslado(LocalDate.of(2026, 10, 1));
        mismoDia.setFechaFinTraslado(LocalDate.of(2026, 10, 1));
        assertDoesNotThrow(() -> service.crear(mismoDia, items()));

        NotaRemision sinFin = nota();
        sinFin.setFechaInicioTraslado(LocalDate.of(2026, 10, 1));
        assertDoesNotThrow(() -> service.crear(sinFin, items()));
    }

    // ----- ciudad y código -----

    @Test
    void entregaConUnaCiudadQueNoEsLaDeSuCodigoNoSeNumera() {
        NotaRemision nota = nota();
        nota.setEntregaCiudad("KATUETE");
        nota.setEntregaCodigoCiudad(5626);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.crear(nota, items()));

        assertTrue(e.getMessage().contains("FRANCISCO CABALLERO ALVAREZ"), e.getMessage());
        assertTrue(e.getMessage().contains("entrega"), e.getMessage());
        verify(timbradoDetalleRepository, never()).lockById(anyLong());
    }

    @Test
    void salidaConUnaCiudadQueNoEsLaDeSuCodigoNoSeNumera() {
        NotaRemision nota = nota();
        nota.setSalidaCiudad("KATUETE");
        nota.setSalidaCodigoCiudad(4738);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.crear(nota, items()));

        assertTrue(e.getMessage().contains("salida"), e.getMessage());
    }

    @Test
    void acentosMinusculasYEspaciosNoCuentan() {
        NotaRemision nota = nota();
        nota.setEntregaCiudad("  Francisco  Caballero Álvarez ");
        nota.setEntregaCodigoCiudad(5626);

        assertDoesNotThrow(() -> service.crear(nota, items()));
    }

    @Test
    void codigoQueNingunTimbradoTieneOCodigoNuloNoSeValida() {
        NotaRemision desconocido = nota();
        desconocido.setEntregaCiudad("ASUNCION");
        desconocido.setEntregaCodigoCiudad(1);
        assertDoesNotThrow(() -> service.crear(desconocido, items()));

        NotaRemision sinCodigo = nota();
        sinCodigo.setEntregaCiudad("CUALQUIERA");
        assertDoesNotThrow(() -> service.crear(sinCodigo, items()));
    }

    /** El código del timbrado es varchar: con espacios no tiene que volverse «desconocido». */
    @Test
    void elCodigoDelTimbradoConEspaciosIgualValida() {
        ciudades.clear();
        ciudades.add(new Object[]{" 5626 ", "FRANCISCO CABALLERO ALVAREZ"});
        NotaRemision nota = nota();
        nota.setEntregaCiudad("KATUETE");
        nota.setEntregaCodigoCiudad(5626);

        assertThrows(GraphQLException.class, () -> service.crear(nota, items()));
    }

    @Test
    void conDosNombresParaElMismoCodigoValeCualquiera() {
        ciudades.add(new Object[]{"4881", "CURUGUATY"});
        NotaRemision nota = nota();
        nota.setEntregaCiudad("CURUGUATY");
        nota.setEntregaCodigoCiudad(4881);

        assertDoesNotThrow(() -> service.crear(nota, items()));
    }

    private static NotaRemision nota() {
        NotaRemision nota = new NotaRemision();
        nota.setSucursalId(13L);
        nota.setTimbradoDetalleId(TIMBRADO);
        nota.setOrigen(OrigenNotaRemision.MANUAL);
        nota.setMotivoEmision(MotivoEmisionNotaRemision.TRASLADO_POR_CONSIGNACION);
        nota.setResponsableEmision(ResponsableEmisionNr.EMISOR_FACTURA);
        nota.setReceptorNombre("FRANCO AREVALOS S.A.");
        nota.setReceptorRuc("80099482-5");
        return nota;
    }

    private static List<NotaRemisionItem> items() {
        NotaRemisionItem item = new NotaRemisionItem();
        item.setDescripcion("COCA COLA 2L");
        item.setCantidad(new BigDecimal("12"));
        return new ArrayList<>(Arrays.asList(item));
    }
}
