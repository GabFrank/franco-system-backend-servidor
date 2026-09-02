package com.franco.dev.service.empresarial;

import com.franco.dev.domain.empresarial.ConfiguracionGeneral;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.repository.financiero.TimbradoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Identidad de la empresa emisora (razon social y RUC) para encabezados y clausulas
 * de reportes.
 *
 * <p>Existe porque cada instalacion es una empresa distinta: la bodega emite como
 * "FRANCO AREVALOS S.A." y la farmacia como "FARMACIA FRANCO AREVALOS S.A.". El
 * ticket de venta y la factura legal ya resuelven eso leyendo el <b>timbrado
 * activo</b> ({@code FacturaLegalGraphQL}, param {@code razonSocial}), que es
 * propio de cada base. Los recibos de RRHH en cambio leian solo
 * {@link ConfiguracionGeneral}, que en produccion esta vacia: por eso el recibo de
 * sueldo imprimia "Recibi de , en la ciudad de ...".</p>
 *
 * <p>Orden de resolucion: {@code ConfiguracionGeneral.razonSocial} →
 * {@code nombreEmpresa} → razon social del timbrado activo → cadena vacia. Asi un
 * valor cargado a mano en ConfiguracionGeneral sigue mandando, y donde no hay nada
 * cargado los recibos dicen exactamente lo mismo que el ticket impreso.</p>
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
        ConfiguracionGeneral cg = configuracionGeneral();
        if (cg != null) {
            if (tiene(cg.getRazonSocial())) return cg.getRazonSocial().trim();
            if (tiene(cg.getNombreEmpresa())) return cg.getNombreEmpresa().trim();
        }
        Timbrado t = timbradoActivo();
        if (t != null && tiene(t.getRazonSocial())) return t.getRazonSocial().trim();
        return "";
    }

    /** RUC de la empresa emisora; "" si no hay ninguna fuente cargada. */
    @Transactional(readOnly = true)
    public String ruc() {
        ConfiguracionGeneral cg = configuracionGeneral();
        if (cg != null && tiene(cg.getRuc())) return cg.getRuc().trim();
        Timbrado t = timbradoActivo();
        if (t != null && tiene(t.getRuc())) return t.getRuc().trim();
        return "";
    }

    /**
     * Ciudad del domicilio fiscal de la empresa emisora; "" si no hay timbrado
     * cargado.
     *
     * <p>Es el respaldo de la clausula "en la ciudad de X" de los recibos, que se
     * arma con la ciudad de la sucursal del funcionario. Esa cadena tiene dos
     * eslabones opcionales -- {@code funcionario.sucursal_id} y
     * {@code sucursal.ciudad_id} son nullable --, asi que un funcionario sin
     * sucursal asignada, o una sucursal sin ciudad, dejaba la frase cortada igual
     * que pasaba con la razon social. La ciudad fiscal del timbrado es ademas la
     * nocion correcta para un recibo que emite la empresa.</p>
     */
    @Transactional(readOnly = true)
    public String ciudad() {
        Timbrado t = timbradoActivo();
        if (t != null && tiene(t.getDomicilioFiscalCiudad())) return t.getDomicilioFiscalCiudad().trim();
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
