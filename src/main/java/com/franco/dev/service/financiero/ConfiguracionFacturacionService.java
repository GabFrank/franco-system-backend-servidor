package com.franco.dev.service.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.ConfiguracionFacturacion;
import com.franco.dev.domain.financiero.ConfiguracionFacturacionHistorial;
import com.franco.dev.domain.financiero.enums.AccionConfiguracionFacturacion;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.ConfiguracionFacturacionInput;
import com.franco.dev.repository.financiero.ConfiguracionFacturacionHistorialRepository;
import com.franco.dev.repository.financiero.ConfiguracionFacturacionRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Politica de facturacion automatica del filial (issue filial #127). Una fila global
 * ({@code sucursal} NULL) y a lo sumo una por sucursal; los indices unicos de V231.1 lo garantizan en
 * la base, y este servicio lo respeta haciendo upsert por esa clave en vez de dejar que explote.
 * <p>
 * Cada cambio escribe su fila en el historial <b>en la misma transaccion</b>: o quedan los dos, o
 * ninguno. Y el masivo sale como una sola transaccion, que es tambien una sola en la replicacion.
 */
@Service
@AllArgsConstructor
public class ConfiguracionFacturacionService extends CrudService<ConfiguracionFacturacion, ConfiguracionFacturacionRepository, Long> {

    private static final int HISTORIAL_LIMITE_DEFAULT = 200;
    private static final int HISTORIAL_LIMITE_MAXIMO = 1000;

    private final ConfiguracionFacturacionRepository repository;
    private final ConfiguracionFacturacionHistorialRepository historialRepository;
    private final SucursalService sucursalService;

    @Override
    public ConfiguracionFacturacionRepository getRepository() {
        return repository;
    }

    public List<ConfiguracionFacturacion> listar() {
        return repository.findAllByOrderByIdAsc();
    }

    /** @param autor el usuario de la sesion; nunca el que mande el cliente. */
    @Transactional
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
            config = mismaClave.orElse(null);
        }

        AccionConfiguracionFacturacion accion;
        if (config == null) {
            config = new ConfiguracionFacturacion();
            config.setCreadoEn(LocalDateTime.now());
            config.setActivo(!Boolean.FALSE.equals(input.getActivo()));
            accion = AccionConfiguracionFacturacion.CREAR;
        } else {
            Long sucursalAnterior = config.getSucursal() != null ? config.getSucursal().getId() : null;
            boolean mismosValores = Objects.equals(sucursalAnterior, input.getSucursalId())
                    && config.getModo() == input.getModo()
                    && Objects.equals(config.getVentasSinFactura(), ventasSinFactura)
                    && Objects.equals(config.getVentaTicketRespetaPolitica(), Boolean.TRUE.equals(input.getVentaTicketRespetaPolitica()));
            // null conserva: un desktop viejo no manda activo y no puede reactivar nada.
            Boolean activoNuevo = input.getActivo() != null ? input.getActivo() : config.getActivo();
            boolean cambiaActivo = !Objects.equals(config.getActivo(), activoNuevo);
            config.setActivo(activoNuevo);
            if (mismosValores && cambiaActivo) {
                accion = Boolean.TRUE.equals(activoNuevo)
                        ? AccionConfiguracionFacturacion.ACTIVAR
                        : AccionConfiguracionFacturacion.DESACTIVAR;
            } else {
                accion = AccionConfiguracionFacturacion.MODIFICAR;
            }
        }

        config.setSucursal(sucursal);
        config.setModo(input.getModo());
        config.setVentasSinFactura(ventasSinFactura);
        config.setVentaTicketRespetaPolitica(Boolean.TRUE.equals(input.getVentaTicketRespetaPolitica()));
        config.setUsuario(autor);
        config.setModificadoEn(LocalDateTime.now());
        ConfiguracionFacturacion guardada = repository.save(config);
        registrar(guardada, accion, autor);
        return guardada;
    }

    /**
     * Borrar el override de una sucursal la devuelve a la global; borrar la global devuelve a las
     * sucursales sin override a su property facturaCountDown. El DELETE se replica. El historial
     * guarda los valores que tenia la fila, antes de borrarla.
     */
    @Transactional
    public Boolean eliminar(Long id, Usuario autor) {
        ConfiguracionFacturacion config = repository.findById(id)
                .orElseThrow(() -> new GraphQLException("La configuracion " + id + " ya no existe."));
        registrar(config, AccionConfiguracionFacturacion.ELIMINAR, autor);
        repository.delete(config);
        return true;
    }

    /**
     * Activa o desactiva todas las configuraciones de sucursal (nunca la global). Solo toca las que
     * no estan ya en ese estado, y devuelve cuantas cambio.
     */
    @Transactional
    public Integer setActivoSucursales(Boolean activo, Usuario autor) {
        if (activo == null) {
            throw new GraphQLException("Indica si se activan o se desactivan.");
        }
        List<ConfiguracionFacturacion> aCambiar = repository.findBySucursalIsNotNullAndActivoNotOrderByIdAsc(activo);
        LocalDateTime ahora = LocalDateTime.now();
        AccionConfiguracionFacturacion accion = activo
                ? AccionConfiguracionFacturacion.ACTIVAR
                : AccionConfiguracionFacturacion.DESACTIVAR;
        for (ConfiguracionFacturacion config : aCambiar) {
            config.setActivo(activo);
            config.setUsuario(autor);
            config.setModificadoEn(ahora);
            registrar(repository.save(config), accion, autor);
        }
        return aCambiar.size();
    }

    /** Mas reciente primero. {@code sucursalId} null trae todo; -1 trae solo la global. */
    public List<ConfiguracionFacturacionHistorial> historial(Long sucursalId, Integer limite) {
        int n = limite != null && limite > 0 ? Math.min(limite, HISTORIAL_LIMITE_MAXIMO) : HISTORIAL_LIMITE_DEFAULT;
        PageRequest pagina = PageRequest.of(0, n);
        if (sucursalId == null) {
            return historialRepository.findAllByOrderByIdDesc(pagina);
        }
        if (sucursalId < 0) {
            return historialRepository.findBySucursalIsNullOrderByIdDesc(pagina);
        }
        return historialRepository.findBySucursalIdOrderByIdDesc(sucursalId, pagina);
    }

    private void registrar(ConfiguracionFacturacion config, AccionConfiguracionFacturacion accion, Usuario autor) {
        ConfiguracionFacturacionHistorial h = new ConfiguracionFacturacionHistorial();
        h.setConfiguracionId(config.getId());
        h.setSucursal(config.getSucursal());
        h.setAccion(accion);
        h.setModo(config.getModo());
        h.setVentasSinFactura(config.getVentasSinFactura());
        h.setVentaTicketRespetaPolitica(config.getVentaTicketRespetaPolitica());
        h.setActivo(config.getActivo());
        h.setUsuario(autor);
        h.setCreadoEn(LocalDateTime.now());
        historialRepository.save(h);
    }
}
