package com.franco.dev.utilitarios.print;

import com.franco.dev.domain.empresarial.enums.PerfilPapel;

import javax.print.attribute.standard.MediaSizeName;

/**
 * Mapea el perfil de papel de una impresora a: cantidad de columnas de caracteres
 * (para termicas ESC/POS) y tamanho de media (para impresoras normales via Jasper).
 *
 * Las columnas son los defaults tipicos en fuente A; se pueden sobreescribir por
 * impresora con el campo {@code columnas}.
 */
public final class PerfilPapelHelper {

    private PerfilPapelHelper() {
    }

    /** Columnas de caracteres por defecto segun el perfil (fuente A). */
    public static int columnas(PerfilPapel perfil) {
        if (perfil == null) {
            return TicketFormato.COLUMNAS_POR_DEFECTO;
        }
        switch (perfil) {
            case MM_48:
                return 32;
            case MM_58:
                return 32;
            case MM_72:
                return 42;
            case MM_80:
                return 48;
            default:
                return TicketFormato.COLUMNAS_POR_DEFECTO;
        }
    }

    /** Tamanho de media (hoja) para impresoras normales. Default A4. */
    public static MediaSizeName mediaSize(PerfilPapel perfil) {
        if (perfil == null) {
            return MediaSizeName.ISO_A4;
        }
        switch (perfil) {
            case CARTA:
                return MediaSizeName.NA_LETTER;
            case A4:
            default:
                return MediaSizeName.ISO_A4;
        }
    }
}
