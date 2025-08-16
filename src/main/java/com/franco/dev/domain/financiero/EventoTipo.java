package com.franco.dev.domain.financiero;

public enum EventoTipo {
    CANCELACION(1),
    CONFORMIDAD(2),
    DISCONFORMIDAD(3),
    INUTILIZACION(4);

    private final int code;

    EventoTipo(int code) { this.code = code; }

    public int getCode() { return code; }

    public static EventoTipo fromCode(Integer code) {
        if (code == null) return null;
        for (EventoTipo t : values()) if (t.code == code) return t;
        return null;
    }
}


