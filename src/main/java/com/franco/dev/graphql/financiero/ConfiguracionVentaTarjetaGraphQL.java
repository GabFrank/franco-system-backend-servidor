package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.ConfiguracionVentaTarjeta;
import com.franco.dev.graphql.financiero.input.ConfiguracionVentaTarjetaInput;
import com.franco.dev.service.financiero.ConfiguracionVentaTarjetaService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import graphql.GraphQLException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@AllArgsConstructor
public class ConfiguracionVentaTarjetaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final ConfiguracionVentaTarjetaService service;
    private final UsuarioService usuarioService;
    private final TesoreriaSecurityService seg;

    public ConfiguracionVentaTarjeta configuracionVentaTarjeta() {
        return service.findOrCreate();
    }

    /**
     * Semantica PATCH: un campo que no viene NO pisa lo guardado.
     * <p>
     * Es la convencion del modulo (misma que {@code saveCajaVirtualConfiguracion}) y lo que permite
     * que una pantalla que solo edita el toggle no borre el resto. El precio es que
     * {@code diasRetencionImagenes} y {@code mbLibresMinimos} no se pueden volver a poner en NULL
     * desde aca una vez seteados; si algun dia hace falta, se agrega un campo explicito de "limpiar"
     * en vez de cambiarle la semantica a todo el input.
     */
    public ConfiguracionVentaTarjeta saveConfiguracionVentaTarjeta(ConfiguracionVentaTarjetaInput input) {
        seg.requireGestionar();
        ConfiguracionVentaTarjeta config = service.findOrCreate();
        if (input.getHabilitado() != null) {
            config.setHabilitado(input.getHabilitado());
        }
        if (input.getRegistroObligatorio() != null) {
            String v = input.getRegistroObligatorio().trim().toUpperCase();
            // Se valida aca y no se deja llegar a la base: la columna tiene CHECK, pero una
            // violacion sube como DataIntegrityViolationException y el usuario veria un error
            // opaco en vez de saber cuales son los valores validos.
            if (!ConfiguracionVentaTarjeta.REGISTROS.contains(v)) {
                throw new GraphQLException("Valor '" + input.getRegistroObligatorio().trim()
                        + "' desconocido para el registro obligatorio. Los validos son: "
                        + String.join(", ", ConfiguracionVentaTarjeta.REGISTROS) + ".");
            }
            config.setRegistroObligatorio(v);
        }
        if (input.getToleranciaDiferenciaMontoPct() != null) {
            BigDecimal pct = input.getToleranciaDiferenciaMontoPct();
            if (pct.compareTo(BigDecimal.ZERO) < 0 || pct.compareTo(new BigDecimal("100")) > 0) {
                throw new GraphQLException("La tolerancia es un porcentaje: tiene que estar entre 0 y 100.");
            }
            config.setToleranciaDiferenciaMontoPct(pct);
        }
        // Los tres tiempos tienen que ser positivos. Un 0 no significa "sin limite" en ninguno de
        // los tres: apagaria el chequeo de duplicado, dejaria el QR muerto al nacer, o cerraria el
        // dialogo antes de que el cajero lo vea.
        config.setMinutosValidezCaptura(positivoODeja(input.getMinutosValidezCaptura(),
                config.getMinutosValidezCaptura(), "Los minutos de validez de la captura"));
        config.setSegundosDialogoRegistro(positivoODeja(input.getSegundosDialogoRegistro(),
                config.getSegundosDialogoRegistro(), "Los segundos del dialogo de registro"));
        config.setHorasVentanaDuplicado(positivoODeja(input.getHorasVentanaDuplicado(),
                config.getHorasVentanaDuplicado(), "Las horas de la ventana de duplicado"));

        if (input.getDiasRetencionImagenes() != null) {
            if (input.getDiasRetencionImagenes() <= 0) {
                throw new GraphQLException("Los dias de retencion tienen que ser mayores a 0."
                        + " Para no purgar nunca, dejalo vacio.");
            }
            config.setDiasRetencionImagenes(input.getDiasRetencionImagenes());
        }
        if (input.getMbLibresMinimos() != null) {
            if (input.getMbLibresMinimos() <= 0) {
                throw new GraphQLException("El umbral de espacio libre tiene que ser mayor a 0."
                        + " Para no alertar, dejalo vacio.");
            }
            config.setMbLibresMinimos(input.getMbLibresMinimos());
        }
        if (input.getUsuarioId() != null) {
            config.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        config.setModificadoEn(LocalDateTime.now());
        return service.save(config);
    }

    private static Integer positivoODeja(Integer nuevo, Integer actual, String queEs) {
        if (nuevo == null) return actual;
        if (nuevo <= 0) throw new GraphQLException(queEs + " tienen que ser mayores a 0.");
        return nuevo;
    }
}
