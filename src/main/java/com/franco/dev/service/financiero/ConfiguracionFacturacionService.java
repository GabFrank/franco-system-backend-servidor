package com.franco.dev.service.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.ConfiguracionFacturacion;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.ConfiguracionFacturacionInput;
import com.franco.dev.repository.financiero.ConfiguracionFacturacionRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Politica de facturacion automatica del filial (issue filial #127). Una fila global
 * ({@code sucursal} NULL) y a lo sumo una por sucursal; el indice unico de V230.1 lo garantiza en
 * la base, y este servicio lo respeta haciendo upsert por esa clave en vez de dejar que explote.
 */
@Service
@AllArgsConstructor
public class ConfiguracionFacturacionService extends CrudService<ConfiguracionFacturacion, ConfiguracionFacturacionRepository, Long> {

    private final ConfiguracionFacturacionRepository repository;
    private final SucursalService sucursalService;

    @Override
    public ConfiguracionFacturacionRepository getRepository() {
        return repository;
    }

    public List<ConfiguracionFacturacion> listar() {
        return repository.findAllByOrderByIdAsc();
    }

    /** @param autor el usuario de la sesion; nunca el que mande el cliente. */
    public ConfiguracionFacturacion guardar(ConfiguracionFacturacionInput input, Usuario autor) {
        if (input.getModo() == null) {
            throw new GraphQLException("Elegi el modo de facturacion.");
        }
        int ventasSinFactura = input.getVentasSinFactura() != null ? input.getVentasSinFactura() : 0;
        if (ventasSinFactura < 0) {
            throw new GraphQLException("Las ventas sin factura entre dos facturadas no pueden ser negativas.");
        }

        Sucursal sucursal = null;
        if (input.getSucursalId() != null) {
            sucursal = sucursalService.findById(input.getSucursalId())
                    .orElseThrow(() -> new GraphQLException("No existe la sucursal " + input.getSucursalId() + "."));
        }

        Optional<ConfiguracionFacturacion> mismaClave = input.getSucursalId() != null
                ? repository.findFirstBySucursalId(input.getSucursalId())
                : repository.findFirstBySucursalIsNull();

        ConfiguracionFacturacion config;
        if (input.getId() != null) {
            config = repository.findById(input.getId())
                    .orElseThrow(() -> new GraphQLException("La configuracion " + input.getId() + " ya no existe."));
            if (mismaClave.isPresent() && !mismaClave.get().getId().equals(config.getId())) {
                throw new GraphQLException(sucursal != null
                        ? "La sucursal " + sucursal.getNombre() + " ya tiene su propia configuracion: editala a ella."
                        : "Ya existe la configuracion global: editala a ella.");
            }
        } else {
            // Guardar sobre una clave que ya tiene fila la actualiza: el indice unico no deja dos.
            config = mismaClave.orElseGet(() -> {
                ConfiguracionFacturacion nueva = new ConfiguracionFacturacion();
                nueva.setCreadoEn(LocalDateTime.now());
                return nueva;
            });
        }

        config.setSucursal(sucursal);
        config.setModo(input.getModo());
        config.setVentasSinFactura(ventasSinFactura);
        config.setVentaTicketRespetaPolitica(Boolean.TRUE.equals(input.getVentaTicketRespetaPolitica()));
        config.setUsuario(autor);
        config.setModificadoEn(LocalDateTime.now());
        return repository.save(config);
    }

    /**
     * Borrar el override de una sucursal la devuelve a la global; borrar la global devuelve a las
     * sucursales sin override a su property facturaCountDown. El DELETE se replica.
     */
    public Boolean eliminar(Long id) {
        if (!repository.existsById(id)) {
            throw new GraphQLException("La configuracion " + id + " ya no existe.");
        }
        repository.deleteById(id);
        return true;
    }
}
