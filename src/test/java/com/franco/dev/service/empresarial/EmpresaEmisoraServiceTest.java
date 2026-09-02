package com.franco.dev.service.empresarial;

import com.franco.dev.domain.empresarial.ConfiguracionGeneral;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.repository.financiero.TimbradoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Resolucion de la empresa emisora que encabeza los recibos de RRHH.
 *
 * <p>El caso que motivo el servicio: en produccion configuracion_general esta
 * vacia y el recibo de sueldo imprimia "Recibi de , en la ciudad de ...". La
 * razon social tiene que salir del timbrado activo, que es de donde ya la saca
 * el ticket impreso, y que es distinto en cada instalacion (bodega vs farmacia).</p>
 */
class EmpresaEmisoraServiceTest {

    private ConfiguracionGeneralService configuracionGeneralService;
    private TimbradoRepository timbradoRepository;
    private EmpresaEmisoraService service;

    @BeforeEach
    void setUp() {
        configuracionGeneralService = mock(ConfiguracionGeneralService.class);
        timbradoRepository = mock(TimbradoRepository.class);
        service = new EmpresaEmisoraService(configuracionGeneralService, timbradoRepository);
    }

    private ConfiguracionGeneral cg(String razonSocial, String nombreEmpresa, String ruc) {
        ConfiguracionGeneral c = new ConfiguracionGeneral();
        c.setRazonSocial(razonSocial);
        c.setNombreEmpresa(nombreEmpresa);
        c.setRuc(ruc);
        return c;
    }

    private Timbrado timbrado(String razonSocial, String ruc) {
        Timbrado t = new Timbrado();
        t.setRazonSocial(razonSocial);
        t.setRuc(ruc);
        t.setActivo(true);
        return t;
    }

    @Test
    void sinConfiguracionGeneral_usaLaRazonSocialDelTimbradoActivo() {
        when(configuracionGeneralService.findAll2()).thenReturn(Collections.emptyList());
        when(timbradoRepository.findActivos())
                .thenReturn(Collections.singletonList(timbrado("FARMACIA FRANCO AREVALOS S.A.", "80012345-6")));

        assertEquals("FARMACIA FRANCO AREVALOS S.A.", service.razonSocial());
        assertEquals("80012345-6", service.ruc());
    }

    @Test
    void configuracionGeneralVacia_noTapaAlTimbrado() {
        // El bug real: la fila existe pero con los campos en blanco/null.
        when(configuracionGeneralService.findAll2())
                .thenReturn(Collections.singletonList(cg("   ", null, "")));
        when(timbradoRepository.findActivos())
                .thenReturn(Collections.singletonList(timbrado("FRANCO AREVALOS S.A.", "80011111-1")));

        assertEquals("FRANCO AREVALOS S.A.", service.razonSocial());
        assertEquals("80011111-1", service.ruc());
    }

    @Test
    void configuracionGeneralCargada_tienePrioridadSobreElTimbrado() {
        when(configuracionGeneralService.findAll2())
                .thenReturn(Collections.singletonList(cg("FRANCO AREVALOS SOCIEDAD ANONIMA", null, "80022222-2")));

        assertEquals("FRANCO AREVALOS SOCIEDAD ANONIMA", service.razonSocial());
        assertEquals("80022222-2", service.ruc());
    }

    @Test
    void sinRazonSocial_caeANombreEmpresa() {
        when(configuracionGeneralService.findAll2())
                .thenReturn(Collections.singletonList(cg(null, "Farmacia Franco", null)));
        when(timbradoRepository.findActivos()).thenReturn(Collections.emptyList());

        assertEquals("Farmacia Franco", service.razonSocial());
    }

    @Test
    void variosTimbradosActivos_tomaElPrimero() {
        // findActivos() ordena por id desc: el mas reciente es el que factura.
        List<Timbrado> activos = Arrays.asList(
                timbrado("FARMACIA FRANCO AREVALOS S.A.", "80012345-6"),
                timbrado("VIEJO S.A.", "80099999-9"));
        when(configuracionGeneralService.findAll2()).thenReturn(Collections.emptyList());
        when(timbradoRepository.findActivos()).thenReturn(activos);

        assertEquals("FARMACIA FRANCO AREVALOS S.A.", service.razonSocial());
    }

    @Test
    void sinNingunaFuente_devuelveCadenaVacia() {
        when(configuracionGeneralService.findAll2()).thenReturn(null);
        when(timbradoRepository.findActivos()).thenReturn(null);

        assertEquals("", service.razonSocial());
        assertEquals("", service.ruc());
    }
}
