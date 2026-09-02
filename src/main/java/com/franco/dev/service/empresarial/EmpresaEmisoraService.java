package com.franco.dev.service.empresarial;

import com.franco.dev.domain.empresarial.ConfiguracionGeneral;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.repository.financiero.TimbradoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Identidad de la empresa emisora (razon social y RUC) para encabezados y
 * clausulas de reportes.
 *
 * <p>Existe porque cada instalacion es una empresa distinta: la bodega emite como
 * "FRANCO AREVALOS SOCIEDAD ANONIMA" y la farmacia como "FARMACIA FRANCO AREVALOS
 * SOCIEDAD ANONIMA". No hay -- ni hace falta -- logica que distinga el tipo de
 * local: alcanza con leer el dato propio de cada base.</p>
 *
 * <p><b>Fuente: el timbrado activo</b> ({@code financiero.timbrado}), que es de
 * donde ya salen el ticket de factura legal ({@code FacturaLegalGraphQL}) y el
 * documento electronico ({@code SifenService}). Se prefiere sobre
 * {@link ConfiguracionGeneral} porque es el dato fiscal vigente y el que esta bien
 * escrito: en alpha el timbrado activo dice "FARMACIA FRANCO AREVALOS SOCIEDAD
 * ANONIMA" con RUC "80100270-2", mientras que configuracion_general guarda la
 * variante abreviada "FARMACIA FRANCO AREVALOS S.A" y un RUC sin digito
 * verificador ("80100270"). Un recibo tiene que nombrar la empresa igual que la
 * factura.</p>
 *
 * <p>{@link ConfiguracionGeneral} queda como respaldo, para instalaciones que
 * todavia no tengan timbrado cargado.</p>
 */
@Service
public class EmpresaEmisoraService {

    private final ConfiguracionGeneralService configuracionGeneralService;
    private final TimbradoRepository timbradoRepository;

    public EmpresaEmisoraService(ConfiguracionGeneralService configuracionGeneralService,
                                 TimbradoRepository timbradoRepository) {
        this.configuracionGeneralService = configuracionGeneralService;
        this.timbradoRepository = timbradoRepository;
    }

    /** Razon social de la empresa emisora; "" si no hay ninguna fuente cargada. */
    @Transactional(readOnly = true)
    public String razonSocial() {
        Timbrado t = timbradoActivo();
        if (t != null && tiene(t.getRazonSocial())) return t.getRazonSocial().trim();
        ConfiguracionGeneral cg = configuracionGeneral();
        if (cg != null) {
            if (tiene(cg.getRazonSocial())) return cg.getRazonSocial().trim();
            if (tiene(cg.getNombreEmpresa())) return cg.getNombreEmpresa().trim();
        }
        return "";
    }

    /** RUC de la empresa emisora, con digito verificador; "" si no hay fuente. */
    @Transactional(readOnly = true)
    public String ruc() {
        Timbrado t = timbradoActivo();
        if (t != null && tiene(t.getRuc())) return t.getRuc().trim();
        ConfiguracionGeneral cg = configuracionGeneral();
        if (cg != null && tiene(cg.getRuc())) return cg.getRuc().trim();
        return "";
    }

    private ConfiguracionGeneral configuracionGeneral() {
        List<ConfiguracionGeneral> all = configuracionGeneralService.findAll2();
        return all != null && !all.isEmpty() ? all.get(0) : null;
    }

    /**
     * Timbrado activo. Solo puede haber uno a la vez (lo garantiza
     * {@code TimbradoService.save} via {@code existeTimbradoActivo}); si igual
     * hubiera varios se toma el mas reciente, que es el que factura.
     */
    private Timbrado timbradoActivo() {
        List<Timbrado> activos = timbradoRepository.findActivos();
        return activos != null && !activos.isEmpty() ? activos.get(0) : null;
    }

    private boolean tiene(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
