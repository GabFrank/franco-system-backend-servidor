package com.franco.dev.graphql.financiero;

public class CajaFilialOperacionResult {

    private Boolean exito;
    private Long cajaId;
    /** Id del conteo creado en la filial; lo usa el cliente como conteoAnteriorId de la proxima edicion. */
    private Long conteoId;

    public CajaFilialOperacionResult() {
    }

    public CajaFilialOperacionResult(Boolean exito, Long cajaId) {
        this.exito = exito;
        this.cajaId = cajaId;
    }

    public static CajaFilialOperacionResult ok(Long cajaId) {
        return new CajaFilialOperacionResult(true, cajaId);
    }

    public static CajaFilialOperacionResult ok(Long cajaId, Long conteoId) {
        CajaFilialOperacionResult result = new CajaFilialOperacionResult(true, cajaId);
        result.setConteoId(conteoId);
        return result;
    }

    public static CajaFilialOperacionResult fail() {
        return new CajaFilialOperacionResult(false, null);
    }

    public Boolean getExito() {
        return exito;
    }

    public void setExito(Boolean exito) {
        this.exito = exito;
    }

    public Long getCajaId() {
        return cajaId;
    }

    public void setCajaId(Long cajaId) {
        this.cajaId = cajaId;
    }

    public Long getConteoId() {
        return conteoId;
    }

    public void setConteoId(Long conteoId) {
        this.conteoId = conteoId;
    }
}
