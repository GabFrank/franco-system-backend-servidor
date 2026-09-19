package com.franco.dev.service.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.NotaRemision;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.financiero.enums.OrigenNotaRemision;
import com.franco.dev.domain.general.Ciudad;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.TransferenciaItemService;
import com.franco.dev.service.operaciones.TransferenciaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * La entrega de una transferencia se completa desde el timbrado de la sucursal destino, igual que
 * la salida desde el de origen. Antes el codigo salia de `general.ciudad.codigo` ("SDG"), que no
 * parsea como numero: la entrega quedaba siempre sin codigo de ciudad.
 */
class NotaRemisionPrellenadoServiceTest {

    private static final Long ORIGEN = 1L;
    private static final Long DESTINO = 7L;
    private static final Long TRANSFERENCIA = 51339L;

    private TransferenciaService transferenciaService;
    private TimbradoDetalleService timbradoDetalleService;
    private SucursalService sucursalService;
    private NotaRemisionPrellenadoService service;
    private Sucursal destino;

    @BeforeEach
    void setUp() {
        transferenciaService = mock(TransferenciaService.class);
        TransferenciaItemService transferenciaItemService = mock(TransferenciaItemService.class);
        timbradoDetalleService = mock(TimbradoDetalleService.class);
        sucursalService = mock(SucursalService.class);
        service = new NotaRemisionPrellenadoService(transferenciaService, transferenciaItemService,
                mock(FacturaLegalService.class), mock(FacturaLegalItemService.class),
                timbradoDetalleService, sucursalService, mock(FacturacionSecurityService.class));

        when(timbradoDetalleService.findBySucursalId(ORIGEN)).thenReturn(Collections.singletonList(
                detalle(ORIGEN, "30 DE JULIO", "SALTO DEL GUAIRA", "4738", "CANINDEYU")));
        when(sucursalService.findById(ORIGEN)).thenReturn(Optional.empty());
        when(transferenciaItemService.findByTransferenciaId(TRANSFERENCIA)).thenReturn(Collections.emptyList());

        Sucursal origen = new Sucursal();
        origen.setId(ORIGEN);
        destino = new Sucursal();
        destino.setId(DESTINO);
        Ciudad ciudad = new Ciudad();
        ciudad.setDescripcion("KATUETE");
        ciudad.setCodigo("KTT");
        destino.setCiudad(ciudad);

        Transferencia transferencia = new Transferencia();
        transferencia.setId(TRANSFERENCIA);
        transferencia.setSucursalOrigen(origen);
        transferencia.setSucursalDestino(destino);
        when(transferenciaService.findById(TRANSFERENCIA)).thenReturn(Optional.of(transferencia));
    }

    @Test
    void entregaSaleDelTimbradoDeLaSucursalDestino() {
        when(timbradoDetalleService.findBySucursalId(DESTINO)).thenReturn(Collections.singletonList(
                detalle(DESTINO, "RUTA 10 KM 60", "KATUETE", "4599", "CANINDEYU")));

        NotaRemision nota = prellenar();

        assertEquals("RUTA 10 KM 60", nota.getEntregaDireccion());
        assertEquals("KATUETE", nota.getEntregaCiudad());
        assertEquals(4599, nota.getEntregaCodigoCiudad());
        assertEquals("CANINDEYU", nota.getEntregaDepartamento());
    }

    @Test
    void laDireccionCargadaEnLaSucursalDestinoGanaSobreLaDelTimbrado() {
        destino.setDireccion("AV. PRINCIPAL");
        when(timbradoDetalleService.findBySucursalId(DESTINO)).thenReturn(Collections.singletonList(
                detalle(DESTINO, "RUTA 10 KM 60", "KATUETE", "4599", "CANINDEYU")));

        NotaRemision nota = prellenar();

        assertEquals("AV. PRINCIPAL", nota.getEntregaDireccion());
        assertEquals(4599, nota.getEntregaCodigoCiudad());
    }

    @Test
    void destinoSinTimbradoNoInventaCodigoYProponeElDepartamentoDeSalida() {
        when(timbradoDetalleService.findBySucursalId(DESTINO)).thenReturn(Collections.emptyList());

        NotaRemision nota = prellenar();

        assertEquals("KATUETE", nota.getEntregaCiudad());
        assertNull(nota.getEntregaCodigoCiudad(), "la abreviatura KTT no es un codigo de SIFEN");
        assertEquals("CANINDEYU", nota.getEntregaDepartamento());
    }

    private NotaRemision prellenar() {
        return service.prellenar(OrigenNotaRemision.TRANSFERENCIA, TRANSFERENCIA, ORIGEN).getNotaRemision();
    }

    private static TimbradoDetalle detalle(Long sucursalId, String direccion, String ciudad,
                                           String codigoCiudad, String departamento) {
        Timbrado timbrado = new Timbrado();
        timbrado.setIsElectronico(true);
        timbrado.setRazonSocial("FRANCO");
        timbrado.setRuc("80099482-5");
        TimbradoDetalle detalle = new TimbradoDetalle();
        detalle.setId(105L);
        detalle.setSucursalId(sucursalId);
        detalle.setActivo(true);
        detalle.setTimbrado(timbrado);
        detalle.setDireccion(direccion);
        detalle.setCiudad(ciudad);
        detalle.setCodigoCiudad(codigoCiudad);
        detalle.setDepartamento(departamento);
        return detalle;
    }
}
