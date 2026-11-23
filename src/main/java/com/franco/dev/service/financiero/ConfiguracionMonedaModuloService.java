package com.franco.dev.service.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.ConfiguracionMonedaModulo;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.enums.ModuloMoneda;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.ConfiguracionMonedaModuloInput;
import com.franco.dev.repository.financiero.ConfiguracionMonedaModuloRepository;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

/**
 * ABM de la moneda por modulo. Una configuracion no se edita: cada cambio es una fila nueva con su
 * vigencia, y la unica modificacion admitida es activarla o desactivarla (la vuelta atras).
 */
@Service
@AllArgsConstructor
public class ConfiguracionMonedaModuloService {

    private final ConfiguracionMonedaModuloRepository repository;
    private final MonedaService monedaService;
    private final SucursalService sucursalService;
    private final CotizacionService cotizacionService;
    private final ControlVersionMoneda controlVersionMoneda;

    /** Una cotizacion del comercio mas vieja que esto pide confirmacion al asignarla a un modulo. */
    static final int DIAS_COTIZACION_VIEJA = 30;
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public List<ConfiguracionMonedaModulo> listar(ModuloMoneda modulo) {
        return modulo != null
                ? repository.findByModuloOrderByVigenteDesdeDescIdDesc(modulo)
                : repository.findAllByOrderByVigenteDesdeDescIdDesc();
    }

    @Transactional
    public ConfiguracionMonedaModulo crear(ConfiguracionMonedaModuloInput input, Usuario autor) {
        if (input == null || input.getModulo() == null) throw new GraphQLException("Falta el modulo.");
        if (input.getMonedaId() == null) throw new GraphQLException("Falta la moneda.");
        Moneda moneda = monedaService.findById(input.getMonedaId())
                .orElseThrow(() -> new GraphQLException("La moneda " + input.getMonedaId() + " no existe."));

        Sucursal sucursal = null;
        if (input.getSucursalId() != null) {
            // Decision de negocio, tambien en el CHECK de V267.5: compras, costos y precios son de
            // toda la empresa; solo el PDV y la facturacion pueden variar por sucursal.
            if (!input.getModulo().porSucursal()) {
                throw new GraphQLException("Solo los modulos PDV y FACTURACION admiten una moneda por sucursal.");
            }
            sucursal = sucursalService.findById(input.getSucursalId())
                    .orElseThrow(() -> new GraphQLException("La sucursal " + input.getSucursalId() + " no existe."));
        }

        LocalDateTime vigenteDesde;
        try {
            vigenteDesde = input.getVigenteDesde() != null ? stringToDate(input.getVigenteDesde()) : null;
        } catch (Exception e) {
            throw new GraphQLException("Fecha de vigencia invalida: " + input.getVigenteDesde());
        }
        LocalDateTime ahora = LocalDateTime.now();
        if (vigenteDesde == null) vigenteDesde = ahora;

        // Una fila con la moneda que ya rige en su clave no cambia nada: solo repite el historial.
        // COSTOS no: una fila nueva re-fija la cotizacion de partida, que es lo que lee CosteoMoneda.
        if (input.getModulo() != ModuloMoneda.COSTOS) {
            Long sucursalId = sucursal != null ? sucursal.getId() : null;
            Optional<ConfiguracionMonedaModulo> rige = MonedaModuloLector.fila(
                    repository.findByModuloOrderByVigenteDesdeDescIdDesc(input.getModulo()), sucursalId, vigenteDesde);
            if (rige.isPresent() && MonedaService.misma(rige.get().getMoneda(), moneda)) {
                String clave = input.getModulo() + (sucursal != null ? " de la sucursal "
                        + (sucursal.getNombre() != null ? sucursal.getNombre() : sucursal.getId()) : "");
                throw new GraphQLException(clave + " ya usa " + moneda.getDenominacion() + " desde el "
                        + rige.get().getVigenteDesde().format(FORMATO_FECHA) + ": no hace falta otra configuracion igual.");
            }
        }

        // Un modulo en una moneda sin cotizacion no puede convertir nada a la de la empresa.
        LocalDateTime controlCotizacion = vigenteDesde.isAfter(ahora) ? ahora : vigenteDesde;
        if (!cotizacionService.delComercio(moneda, controlCotizacion).isPresent()) {
            throw new GraphQLException("SIN_COTIZACION: " + moneda.getDenominacion()
                    + " no tiene cotizacion cargada. Cargala antes de asignarla a un modulo.");
        }
        // Una cotizacion del comercio de hace meses no bloquea, pero el usuario la tiene que ver y
        // confirmar: el PDV y la factura convierten con ella. La de la empresa no se convierte.
        if (!Boolean.TRUE.equals(input.getConfirmarCotizacionVieja())
                && !MonedaService.misma(moneda, monedaService.empresa())) {
            cotizacionService.fechaDelComercio(moneda, controlCotizacion)
                    .filter(f -> f.isBefore(controlCotizacion.minusDays(DIAS_COTIZACION_VIEJA)))
                    .ifPresent(f -> {
                        throw new GraphQLException("COTIZACION_VIEJA: La ultima cotizacion de "
                                + moneda.getDenominacion() + " es del " + f.format(FORMATO_FECHA)
                                + ". Confirma para asignarla igual o carga una nueva.");
                    });
        }

        // Un escritorio viejo cobra y factura todo en la moneda de la empresa: el PDV o la factura en
        // otra moneda exigen la version minima (como un precio en otra moneda, fase 3).
        if ((input.getModulo() == ModuloMoneda.PDV || input.getModulo() == ModuloMoneda.FACTURACION)
                && !MonedaService.misma(moneda, monedaService.empresa())) {
            controlVersionMoneda.exigirParaOtraMoneda("operar el " + input.getModulo() + " en " + moneda.getDenominacion());
        }
        // COSTOS en otra moneda: la cotizacion de compras de hoy queda guardada como punto de partida
        // del costeo (CosteoMoneda). Sin ella las filas viejas no se pueden llevar a esa moneda.
        BigDecimal cotizacionPartida = null;
        if (input.getModulo() == ModuloMoneda.COSTOS && !MonedaService.misma(moneda, monedaService.empresa())
                && !MonedaService.esGuarani(moneda)) {
            cotizacionPartida = cotizacionService.paraCompra(moneda, null)
                    .filter(c -> c.compareTo(BigDecimal.ONE) > 0)
                    .orElseThrow(() -> new GraphQLException("SIN_COTIZACION: " + moneda.getDenominacion()
                            + " no tiene cotizacion de compras. Cargala antes de llevar los costos a esa moneda."));
        }

        ConfiguracionMonedaModulo e = new ConfiguracionMonedaModulo();
        e.setCotizacionPartida(cotizacionPartida);
        e.setModulo(input.getModulo());
        e.setSucursal(sucursal);
        e.setMoneda(moneda);
        e.setVigenteDesde(vigenteDesde);
        e.setActivo(true);
        e.setUsuario(autor);
        e.setCreadoEn(ahora);
        return repository.save(e);
    }

    @Transactional
    public ConfiguracionMonedaModulo setActivo(Long id, Boolean activo) {
        if (activo == null) throw new GraphQLException("Falta activo.");
        ConfiguracionMonedaModulo e = repository.findById(id)
                .orElseThrow(() -> new GraphQLException("La configuracion " + id + " no existe."));
        e.setActivo(activo);
        return repository.save(e);
    }
}
