package com.franco.dev.service.sifen.util;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.repository.financiero.TimbradoDetalleRepository;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.GraphQLException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Impide emitir cuando dos timbrados distintos comparten serie ante la SET.
 *
 * Lo que identifica una serie para SIFEN es **(timbrado, establecimiento, punto de expedicion)**,
 * no el `timbrado_detalle_id`. Pero la numeracion de las notas sale de
 * `findMaxNumeroByTimbradoDetalleId`, que cuenta por fila. Si dos filas activas del mismo timbrado
 * resuelven al mismo establecimiento y punto, cada una lleva su propio contador y las dos emiten
 * la nota 1, la 2, la 3: numeros DUPLICADOS ante la SET, que no se pueden deshacer.
 *
 * Pasa en cuanto se le da timbrado a una sucursal sin `codigo_establecimiento_factura` propio
 * (cae al 001 por defecto) reusando el punto de otra. Hoy nada lo impedia.
 */
@Slf4j
@Component
public class SerieDeNumeracionValidator {

    private final TimbradoDetalleRepository timbradoDetalleRepository;
    private final SucursalService sucursalService;

    public SerieDeNumeracionValidator(TimbradoDetalleRepository timbradoDetalleRepository,
                                      SucursalService sucursalService) {
        this.timbradoDetalleRepository = timbradoDetalleRepository;
        this.sucursalService = sucursalService;
    }

    /**
     * @param timbrado el timbrado con el que se va a emitir.
     * @throws GraphQLException si otra fila activa del mismo timbrado declara el mismo
     *         establecimiento y punto de expedicion con un id distinto.
     */
    public void exigirSerieSinColision(TimbradoDetalle timbrado) {
        if (timbrado == null || timbrado.getTimbrado() == null) return;

        List<TimbradoDetalle> hermanos =
                timbradoDetalleRepository.findByTimbradoId(timbrado.getTimbrado().getId());
        if (hermanos == null || hermanos.size() < 2) return;

        String serie = serieDe(timbrado);
        Map<String, Long> primeroPorSerie = new HashMap<>();
        for (TimbradoDetalle otro : hermanos) {
            if (!Boolean.TRUE.equals(otro.getActivo())) continue;
            String suSerie = serieDe(otro);
            if (!suSerie.equals(serie)) continue;
            // Misma serie: solo es colision si es OTRA fila. El mismo id en dos sucursales
            // comparte contador a proposito y es seguro.
            Long yaVisto = primeroPorSerie.putIfAbsent(suSerie, otro.getId());
            if (yaVisto != null && !yaVisto.equals(otro.getId())) {
                throw new GraphQLException(
                        "Hay dos timbrados activos que declaran el establecimiento " + serie
                        + " ante la SET con numeraciones separadas (timbrado_detalle " + yaVisto
                        + " y " + otro.getId() + "). Emitir asi produce numeros duplicados: hay que"
                        + " darle a cada uno su propio punto de expedicion, o que compartan el id.");
            }
        }
    }

    /** «001-002»: lo que identifica la serie ante la SET. */
    private String serieDe(TimbradoDetalle detalle) {
        Sucursal sucursal = detalle.getSucursalId() != null
                ? sucursalService.findById(detalle.getSucursalId()).orElse(null) : null;
        String establecimiento = SifenTimbradoHelper.codigoEstablecimiento(sucursal);
        String punto = detalle.getPuntoExpedicion() != null
                ? detalle.getPuntoExpedicion().trim() : "";
        return establecimiento + "-" + punto;
    }
}
