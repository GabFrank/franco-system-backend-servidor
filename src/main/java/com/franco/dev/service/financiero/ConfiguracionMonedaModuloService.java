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

import java.time.LocalDateTime;
import java.util.List;

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
            // Decision de negocio, tambien en el CHECK de V258.5: compras, costos y precios son de
            // toda la empresa; solo el PDV puede tener una moneda distinta por sucursal.
            if (input.getModulo() != ModuloMoneda.PDV) {
                throw new GraphQLException("Solo el modulo PDV admite una moneda por sucursal.");
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

        // Un modulo en una moneda sin cotizacion no puede convertir nada a la de la empresa.
        LocalDateTime controlCotizacion = vigenteDesde.isAfter(ahora) ? ahora : vigenteDesde;
        if (!cotizacionService.delComercio(moneda, controlCotizacion).isPresent()) {
            throw new GraphQLException("SIN_COTIZACION: " + moneda.getDenominacion()
                    + " no tiene cotizacion cargada. Cargala antes de asignarla a un modulo.");
        }

        ConfiguracionMonedaModulo e = new ConfiguracionMonedaModulo();
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
