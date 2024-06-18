package com.franco.dev.domain.financiero.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExcelFacturasDto {

    private String venTipimp;
    private Double venGra05;
    private Double venIva05;
    private String venDisg05 = "A";
    private String ctaIva05 = "";
    private String venRubgra = "1";
    private String venRubg05 = "1";
    private String venDisexe = "";
    private String venNumero;
    private String venImputa = "";
    private Long venSucurs;
    private String generar = "";
    private String formPag;
    private String venCentro = "";
    private String venProvee;
    private String venCuenta = "";
    private String venPrvnom;
    private String venTipofa;
    private String venFecha;
    private Double venTotfac;
    private Double venExenta;
    private Double venGravad;
    private Double venIva;
    private String venRetenc = "";
    private String venAux = "";
    private String venCtrl = "";
    private String venCon = "";
    private Long venCuota = Long.valueOf(0);
    private String venFecven;
    private String cantDias = "";
    private String origen = "";
    private String cambio = "";
    private String valor = "";
    private String moneda = "";
    private String exenDolar = "";
    private String concepto = "";
    private String ctaIva = "";
    private String ctaCaja = "";
    private String tkdesde = "";
    private String tkhasta = "";
    private String caja = "";
    private String venDisgra = "A";
    private String formaDevo = "";
    private String venCuense = "";
    private String anular = "";
    private String reproceso = "";
    private String cuentaExe = "";
    private String usuIde = "";
    private Long rucvennrotim;
    private String clieasi = "";
    private String ventirptip = "";
    private String ventirpgra = "";
    private String ventirpexe = "";
    private String irpc = "1";
    private String ivasimplificado = "";
    private String venirprygc = "";
    private String venbconom = "";
    private String venbcoctacte = "";
    private String nofacnotcre = "";
    private String notimbfacnotcre = "";
    private String ventipodoc = "";
    private String ventanoiva = "";
    private String identifclie = "";
    private String gdcbienid = "";
    private String gdctipobien = "";
    private String gdcimpcosto = "";
    private String gdcimpventagrav = "";
}

