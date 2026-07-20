package com.franco.dev.service.financiero;

import com.franco.dev.domain.activos.Ente;
import com.franco.dev.domain.activos.enums.TipoEnte;
import com.franco.dev.domain.financiero.TipoGasto;
import com.franco.dev.domain.financiero.enums.TipoPadreGastoModulo;
import com.franco.dev.service.activos.EnteService;
import graphql.GraphQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PreGastoEnteValidationService {

    private final EnteService enteService;
    private final TipoGastoModuloReglasService moduloReglasService;

    /**
     * Valida y resuelve el ente según el módulo padre del tipo de gasto.
     * Para activos (vehículo, mueble, inmueble, equipo) y servicios continuos
     * vinculados a inmueble (ANDE, agua, etc.) el ente es obligatorio.
     */
    public Ente validarYResolverEnte(TipoGasto tipoGasto, Long enteId) {
        if (tipoGasto == null) {
            if (enteId != null) {
                throw new GraphQLException("No se puede vincular un activo sin un tipo de gasto válido.");
            }
            return null;
        }

        TipoPadreGastoModulo modulo = tipoGasto.getModuloPadre();
        TipoEnte tipoEnteRequerido = moduloReglasService.tipoEnteEsperado(modulo);

        if (tipoEnteRequerido == null) {
            if (enteId != null) {
                throw new GraphQLException(
                        "El tipo de gasto \"" + tipoGasto.getDescripcion()
                                + "\" no admite vinculación a un activo (inmueble, vehículo, mueble o equipo).");
            }
            return null;
        }

        if (enteId == null) {
            throw new GraphQLException(
                    "Debe seleccionar " + etiquetaActivo(tipoEnteRequerido, modulo)
                            + " para el tipo de gasto \"" + tipoGasto.getDescripcion() + "\".");
        }

        Ente ente = enteService.findById(enteId)
                .orElseThrow(() -> new GraphQLException("El activo seleccionado no existe."));

        if (Boolean.FALSE.equals(ente.getActivo())) {
            throw new GraphQLException("El activo seleccionado no está activo.");
        }

        if (ente.getTipoEnte() != tipoEnteRequerido) {
            throw new GraphQLException(
                    "El activo seleccionado no corresponde al módulo del tipo de gasto. Se esperaba "
                            + etiquetaActivo(tipoEnteRequerido, modulo) + ".");
        }

        return ente;
    }

    private String etiquetaActivo(TipoEnte tipo, TipoPadreGastoModulo modulo) {
        if (modulo == TipoPadreGastoModulo.ANDE) {
            return "un inmueble (factura ANDE)";
        }
        if (modulo == TipoPadreGastoModulo.JUNTA_SANEAMIENTO) {
            return "un inmueble (servicio de agua)";
        }
        switch (tipo) {
            case VEHICULO:
                return "un vehículo";
            case MUEBLE:
                return "un mueble";
            case INMUEBLE:
                return "un inmueble";
            case EQUIPO:
                return "un equipo";
            default:
                return "un activo";
        }
    }
}