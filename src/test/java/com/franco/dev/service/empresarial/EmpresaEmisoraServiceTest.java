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
 * <p>La razon social sale del timbrado activo, que es de donde ya la saca el ticket
 * de factura legal y el documento electronico, y que es propio de cada instalacion
 * (bodega vs farmacia son empresas distintas, con base y timbrado separados).</p>
 *
 * <p>Se prefiere al de configuracion_general porque es el dato fiscal vigente y el
 * que esta bien escrito: en alpha el timbrado activo dice "FARMACIA FRANCO AREVALOS
 * SOCIEDAD ANONIMA" con RUC "80100270-2", mientras que configuracion_general guarda
 * "FARMACIA FRANCO AREVALOS S.A" y un RUC sin digito verificador.</p>
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
    void razonSocial_saleDelTimbradoActivo() {
        // Valores reales de alpha: el timbrado activo tiene la redaccion completa
        // y el RUC con digito verificador.
        when(timbradoRepository.findActivos()).thenReturn(Collections.singletonList(
                timbrado("FARMACIA FRANCO AREVALOS SOCIEDAD ANONIMA", "80100270-2")));

        assertEquals("FARMACIA FRANCO AREVALOS SOCIEDAD ANONIMA", service.razonSocial());
        assertEquals("80100270-2", service.ruc());
    }

    @Test
    void elTimbradoLeGanaAConfiguracionGeneral() {
        // configuracion_general guarda la variante abreviada y un RUC sin DV; el
        // recibo tiene que nombrar la empresa igual que la factura.
        when(configuracionGeneralService.findAll2()).thenReturn(Collections.singletonList(
                cg("FARMACIA FRANCO AREVALOS S.A", null, "80100270")));
        when(timbradoRepository.findActivos()).thenReturn(Collections.singletonList(
                timbrado("FARMACIA FRANCO AREVALOS SOCIEDAD ANONIMA", "80100270-2")));

        assertEquals("FARMACIA FRANCO AREVALOS SOCIEDAD ANONIMA", service.razonSocial());
        assertEquals("80100270-2", service.ruc());
    }

    @Test
    void sinTimbrado_caeAConfiguracionGeneral() {
        when(timbradoRepository.findActivos()).thenReturn(Collections.emptyList());
        when(configuracionGeneralService.findAll2()).thenReturn(Collections.singletonList(
                cg("FRANCO AREVALOS SOCIEDAD ANONIMA", null, "80011111-1")));

        assertEquals("FRANCO AREVALOS SOCIEDAD ANONIMA", service.razonSocial());
        assertEquals("80011111-1", service.ruc());
    }

    @Test
    void timbradoConRazonSocialEnBlanco_noTapaAConfiguracionGeneral() {
        when(timbradoRepository.findActivos()).thenReturn(Collections.singletonList(timbrado("   ", null)));
        when(configuracionGeneralService.findAll2()).thenReturn(Collections.singletonList(
                cg(null, "Farmacia Franco", null)));

        assertEquals("Farmacia Franco", service.razonSocial());
    }

    @Test
    void variosTimbradosActivos_tomaElMasReciente() {
        // findActivos() ordena por id desc: el mas reciente es el que factura.
        List<Timbrado> activos = Arrays.asList(
                timbrado("FARMACIA FRANCO AREVALOS SOCIEDAD ANONIMA", "80100270-2"),
                timbrado("VIEJO S.A.", "80099999-9"));
        when(timbradoRepository.findActivos()).thenReturn(activos);

        assertEquals("FARMACIA FRANCO AREVALOS SOCIEDAD ANONIMA", service.razonSocial());
    }

    @Test
    void sinNingunaFuente_devuelveCadenaVacia() {
        when(configuracionGeneralService.findAll2()).thenReturn(null);
        when(timbradoRepository.findActivos()).thenReturn(null);

        assertEquals("", service.razonSocial());
        assertEquals("", service.ruc());
    }
}
