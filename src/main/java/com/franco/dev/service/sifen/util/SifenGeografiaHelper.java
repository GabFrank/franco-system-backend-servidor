package com.franco.dev.service.sifen.util;

import com.roshka.sifen.core.types.TDepartamento;

/**
 * Mapeo de nombres de departamento a los códigos de SIFEN. Es el mismo switch que
 * {@code SifenService.mapearDepartamento}, extraído para que lo puedan usar las notas sin tocar la
 * ruta de la factura.
 *
 * ⚠️ A diferencia de la referencia (frc-efact), acá **no hay fallback silencioso a Asunción** en los
 * campos de ciudad de la nota de remisión: una ciudad que falta se rechaza con un mensaje en el
 * validador. Un traslado declarado desde una ciudad equivocada es un dato fiscal falso.
 */
public final class SifenGeografiaHelper {

    private SifenGeografiaHelper() {
    }

    /**
     * Departamento por nombre, EXIGIENDO que exista. Para la nota de remisión, el par
     * (departamento, ciudad) tiene que ser coherente o SIFEN rechaza el lote entero con
     * «2203 El Departamento, el Distrito y la Ciudad ... no están relacionados» (producción,
     * 2026-09-18). El fallback silencioso a CAPITAL declaraba en Asunción un traslado de
     * Canindeyú: un dato fiscal falso, y además nunca aprobable.
     *
     * @param donde para el mensaje de error: "salida", "entrega", "receptor".
     */
    public static TDepartamento departamentoExigido(String nombre, String donde) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new graphql.GraphQLException("Falta el departamento de " + donde
                    + ": SIFEN exige que coincida con la ciudad");
        }
        TDepartamento d = departamentoONulo(nombre);
        if (d == null) {
            throw new graphql.GraphQLException("El departamento de " + donde + " («" + nombre.trim()
                    + "») no es uno de los 18 que reconoce SIFEN");
        }
        return d;
    }

    /** Departamento por nombre, o {@code null} si no se reconoce. */
    public static TDepartamento departamentoONulo(String nombre) {
        if (nombre == null) return null;
        switch (nombre.trim().toUpperCase()) {
            case "CAPITAL": case "ASUNCION": case "ASUNCIÓN": return TDepartamento.CAPITAL;
            case "CONCEPCION": case "CONCEPCIÓN": return TDepartamento.CONCEPCION;
            case "SAN PEDRO": return TDepartamento.SAN_PEDRO;
            case "CORDILLERA": return TDepartamento.CORDILLERA;
            case "GUAIRA": case "GUAIRÁ": return TDepartamento.GUAIRA;
            case "CAAGUAZU": case "CAAGUAZÚ": return TDepartamento.CAAGUAZU;
            case "CAAZAPA": case "CAAZAPÁ": return TDepartamento.CAAZAPA;
            case "ITAPUA": case "ITAPÚA": return TDepartamento.ITAPUA;
            case "MISIONES": return TDepartamento.MISIONES;
            case "PARAGUARI": case "PARAGUARÍ": return TDepartamento.PARAGUARI;
            case "ALTO PARANA": case "ALTO PARANÁ": return TDepartamento.ALTO_PARANA;
            case "CENTRAL": return TDepartamento.CENTRAL;
            case "ÑEEMBUCU": case "ÑEEMBUCÚ": return TDepartamento.NEEMBUCU;
            case "AMAMBAY": return TDepartamento.AMAMBAY;
            case "CANINDEYU": case "CANINDEYÚ": return TDepartamento.CANINDEYU;
            case "PRESIDENTE HAYES": case "HAYES": return TDepartamento.PTE_HAYES;
            case "BOQUERON": case "BOQUERÓN": return TDepartamento.BOQUERON;
            case "ALTO PARAGUAY": return TDepartamento.ALTO_PARAGUAY;
            default: return null;
        }
    }

    /** Departamento por nombre; {@code null} o desconocido devuelve CAPITAL, como la ruta actual. */
    public static TDepartamento departamento(String nombre) {
        if (nombre == null) return TDepartamento.CAPITAL;

        switch (nombre.trim().toUpperCase()) {
            case "CAPITAL": return TDepartamento.CAPITAL;
            case "CONCEPCION": case "CONCEPCIÓN": return TDepartamento.CONCEPCION;
            case "SAN PEDRO": return TDepartamento.SAN_PEDRO;
            case "CORDILLERA": return TDepartamento.CORDILLERA;
            case "GUAIRA": case "GUAIRÁ": return TDepartamento.GUAIRA;
            case "CAAGUAZU": case "CAAGUAZÚ": return TDepartamento.CAAGUAZU;
            case "CAAZAPA": case "CAAZAPÁ": return TDepartamento.CAAZAPA;
            case "ITAPUA": case "ITAPÚA": return TDepartamento.ITAPUA;
            case "MISIONES": return TDepartamento.MISIONES;
            case "PARAGUARI": case "PARAGUARÍ": return TDepartamento.PARAGUARI;
            case "ALTO PARANA": case "ALTO PARANÁ": return TDepartamento.ALTO_PARANA;
            case "CENTRAL": return TDepartamento.CENTRAL;
            case "ÑEEMBUCU": case "ÑEEMBUCÚ": return TDepartamento.NEEMBUCU;
            case "AMAMBAY": return TDepartamento.AMAMBAY;
            case "CANINDEYU": case "CANINDEYÚ": return TDepartamento.CANINDEYU;
            case "PRESIDENTE HAYES": case "HAYES": return TDepartamento.PTE_HAYES;
            case "BOQUERON": case "BOQUERÓN": return TDepartamento.BOQUERON;
            case "ALTO PARAGUAY": return TDepartamento.ALTO_PARAGUAY;
            default: return TDepartamento.CAPITAL;
        }
    }

    /**
     * Marca del vehículo para {@code dMarVeh}, que SIFEN limita a 10 caracteres (error E962).
     * Abrevia las marcas largas conocidas y, si no hay abreviatura, recorta.
     */
    public static String marcaVehiculo(String marca) {
        if (marca == null) return null;
        String normalizada = marca.trim().toUpperCase();
        if (normalizada.isEmpty()) return null;
        if (normalizada.length() <= 10) return normalizada;

        switch (normalizada) {
            case "MERCEDES BENZ":
            case "MERCEDES-BENZ":
            case "MERCEDEZ BENZ": return "MERCEDES";
            case "VOLKSWAGEN": return "VW";
            case "CHEVROLET": return "CHEVY";
            case "MITSUBISHI": return "MITSUBISH";
            case "LAND ROVER": return "LANDROVER";
            default: return normalizada.substring(0, 10);
        }
    }
}
