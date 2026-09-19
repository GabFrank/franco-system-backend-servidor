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
 * `findMaxNumeroByTimbradoDetalleId`, que cuenta por fila. Si otra fila activa del mismo timbrado
 * resuelve al mismo establecimiento y punto con otro id, cada una lleva su propio contador y las
 * dos emiten la nota 1, la 2, la 3: numeros DUPLICADOS ante la SET, que no se pueden deshacer.
 *
 * Pasa en cuanto se le da timbrado a una sucursal sin `codigo_establecimiento_factura` propio
 * (cae al 001 por defecto) reusando el punto de otra. Hoy nada lo impedia.
 *
 * La serie se calcula igual que la arman los builders del XML: el establecimiento de la sucursal
 * que EMITE (no la de la fila, que con ids compartidos puede ser otra) y el punto con tres digitos.
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
     * @param timbrado        la fila con la que se va a emitir. Cuenta aunque este inactiva: la
     *                        nota de credito emite con el timbrado de la factura, que puede estarlo.
     * @param sucursalEmisora la sucursal de la nota, de donde sale el establecimiento del CDC.
     * @throws GraphQLException si otra fila activa del mismo timbrado declara la misma serie con
     *                          un id distinto.
     */
    public void exigirSerieSinColision(TimbradoDetalle timbrado, Long sucursalEmisora) {
        if (timbrado == null || timbrado.getTimbrado() == null) return;

        List<Object[]> filas =
                timbradoDetalleRepository.findFilasDeSerieByTimbradoId(timbrado.getTimbrado().getId());
        if (filas == null || filas.size() < 2) return;

        Map<Long, String> establecimientos = new HashMap<>();
        String serie = serieDe(sucursalEmisora, timbrado.getPuntoExpedicion(), establecimientos);
        for (Object[] fila : filas) {
            Long id = aLong(fila[0]);
            // Mismo id: mismo contador, no hay duplicado de numeracion. Pero NO es una forma valida de
            // compartir serie entre sucursales: en las filiales la PK de timbrado_detalle es solo
            // (id), y repetir un id traba la replicacion central→filial entera (incidente 2026-09-19).
            if (id == null || id.equals(timbrado.getId())) continue;
            if (!Boolean.TRUE.equals(fila[3])) continue;
            String suSerie = serieDe(aLong(fila[1]), (String) fila[2], establecimientos);
            if (suSerie.equals(serie)) {
                throw new GraphQLException(
                        "Hay dos timbrados activos que declaran el establecimiento " + serie
                        + " ante la SET con numeraciones separadas (timbrado_detalle " + timbrado.getId()
                        + " y " + id + "). Emitir asi produce numeros duplicados: hay que"
                        + " darle a cada uno su propio punto de expedicion, o que compartan el id.");
            }
        }
    }

    /** «001-002»: lo que identifica la serie ante la SET. */
    private String serieDe(Long sucursalId, String punto, Map<Long, String> establecimientos) {
        String establecimiento = establecimientos.computeIfAbsent(sucursalId == null ? -1L : sucursalId, id -> {
            Sucursal sucursal = id < 0 ? null : sucursalService.findById(id).orElse(null);
            return SifenTimbradoHelper.codigoEstablecimiento(sucursal);
        });
        return establecimiento + "-" + normalizarPunto(punto);
    }

    /** Igual que los builders: `String.format("%03d", parseInt(punto))`, así "2" y "002" son lo mismo. */
    static String normalizarPunto(String punto) {
        if (punto == null) return "";
        String limpio = punto.trim();
        try {
            return String.format("%03d", Integer.parseInt(limpio));
        } catch (NumberFormatException e) {
            return limpio;
        }
    }

    private static Long aLong(Object valor) {
        return valor instanceof Number ? ((Number) valor).longValue() : null;
    }
}
