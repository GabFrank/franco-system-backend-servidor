package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.configuracion.Local;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.dto.ExcelFacturasDto;
import com.franco.dev.domain.financiero.dto.ExcelFacturasDtoProjection;
import com.franco.dev.domain.financiero.dto.ResumenFacturasDto;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface FacturaLegalRepository extends HelperRepository<FacturaLegal, EmbebedPrimaryKey> {

    default Class<FacturaLegal> getEntityClass() {
        return FacturaLegal.class;
    }

//    @Query("select m from Moneda m " +
//            "where UPPER(CAST(id as text)) like %?1% or UPPER(denominacion) like %?1%")
//    public List<Moneda> findByAll(String texto);

//    Moneda findByPaisId(Long id);

    public List<FacturaLegal> findByCajaId(Long id);

    FacturaLegal findByIdAndSucursalId(Long id, Long sucId);

    Boolean deleteByIdAndSucursalId(Long id, Long sucId);

    public FacturaLegal findByVentaIdAndSucursalId(Long id, Long sucId);

    @Query(value =  "select fl from FacturaLegal fl where " +
            "(fl.creadoEn between :inicio and :fin) " +
            "and (fl.sucursalId in :sucId) " +
            "and (:nombre is null or fl.nombre like :nombre) " +
            "and (:ruc is null or fl.ruc like :ruc) " +
            "order by fl.creadoEn")
    public Page<FacturaLegal> findByCreadoEnBetweenAndSucursalId(LocalDateTime inicio, LocalDateTime fin, List<Long> sucId, String nombre, String ruc, Pageable pageable);

    @Query(value =  "select fl from FacturaLegal fl where " +
            "(fl.creadoEn between :inicio and :fin) " +
            "and (fl.sucursalId in :sucId) " +
            "order by fl.creadoEn")
    public List<FacturaLegal> findByCreadoEnBetweenAndSucursalId(LocalDateTime inicio, LocalDateTime fin, Long sucId);

    @Query(value = "select new com.franco.dev.domain.financiero.dto.ResumenFacturasDto(count(fl.id), max(fl.numeroFactura), min(fl.numeroFactura), " +
            "sum(fl.totalFinal), sum(fl.totalParcial5), sum(fl.totalParcial10), sum(totalParcial0)) " +
            "from FacturaLegal fl where " +
            "(fl.creadoEn between :inicio and :fin) " +
            "and (fl.sucursalId in :sucId) " +
            "and (:nombre is null or fl.nombre like :nombre) " +
            "and (:ruc is null or fl.ruc like :ruc)")
    public ResumenFacturasDto findResumenFacturas(LocalDateTime inicio, LocalDateTime fin, List<Long> sucId, String nombre, String ruc);

//    @Query(value = "select " +
//            "CAST('I' AS TEXT) as ven_tipimp, " +
//            "CAST(sum(CASE WHEN f.total_parcial_5 IS NOT NULL THEN f.total_parcial_5 - f.total_parcial_5 / 21 ELSE 0 END) AS TEXT) as ven_gra05, " +
//            "CAST(sum(CASE WHEN f.total_parcial_5 IS NOT NULL THEN f.total_parcial_5 / 21 ELSE 0 END) AS TEXT) as ven_iva05, " +
//            "CAST('' AS TEXT) as ven_disg05, " +
//            "CAST('' AS TEXT) as cta_iva05, " +
//            "CAST('' AS TEXT) as ven_rubgra, " +
//            "CAST('' AS TEXT) as ven_rubg05, " +
//            "CAST('' AS TEXT) as ven_disexe, " +
//            "CAST(concat(s.codigo_establecimiento_factura, '-', td.punto_expedicion, '-', " +
//            "CASE " +
//            "   WHEN length(CAST(f.numero_factura AS TEXT)) = 1 THEN '000000' " +
//            "   WHEN length(CAST(f.numero_factura AS TEXT)) = 2 THEN '00000' " +
//            "   WHEN length(CAST(f.numero_factura AS TEXT)) = 3 THEN '0000' " +
//            "   WHEN length(CAST(f.numero_factura AS TEXT)) = 4 THEN '000' " +
//            "   WHEN length(CAST(f.numero_factura AS TEXT)) = 5 THEN '00' " +
//            "   WHEN length(CAST(f.numero_factura AS TEXT)) = 6 THEN '0' " +
//            "END, f.numero_factura) AS TEXT) as ven_numero, " +
//            "CAST('' AS TEXT) as ven_imputa, " +
//            "CAST(s.codigo_establecimiento_factura AS TEXT) as ven_sucurs, " +
//            "CAST('' AS TEXT) as generar, " +
//            "CAST(CASE WHEN f.credito = true THEN 'CREDITO' ELSE 'CONTADO' END AS TEXT) as form_pag, " +
//            "CAST('' AS TEXT) as ven_centro, " +
//            "CAST(f.ruc AS TEXT) as ven_provee, " +
//            "CAST('' AS TEXT) as ven_cuenta, " +
//            "CAST(f.nombre AS TEXT) as ven_prvnom, " +
//            "CAST('FACTURA' AS TEXT) as ven_tipofa, " +
//                    "CAST(substring(CAST(to_char(f.creado_en, 'DD-MM-YYYY') AS TEXT) from 0 for 11) AS TEXT) as ven_fecha, " +
//                    "CAST(f.total_final AS TEXT) as ven_totfac, " +
//                    "CAST('0' AS TEXT) as ven_exenta, " +
//                    "CAST(sum(CASE WHEN f.total_parcial_10 IS NOT NULL THEN f.total_parcial_10 - f.total_parcial_10 / 11 ELSE 0 END) AS TEXT) as ven_gravad, " +
//                    "CAST(sum(CASE WHEN f.total_parcial_10 IS NOT NULL THEN f.total_parcial_10 / 11 ELSE 0 END) AS TEXT) as ven_iva, " +
//                    "CAST('' AS TEXT) as ven_retenc, " +
//                    "CAST('' AS TEXT) as ven_aux, " +
//                    "CAST('' AS TEXT) as ven_ctrl, " +
//                    "CAST('' AS TEXT) as ven_con, " +
//                    "CAST('0' AS TEXT) as ven_cuota, " +
//                    "CAST(substring(CAST(to_char(f.creado_en, 'DD-MM-YYYY') AS TEXT) from 0 for 11) AS TEXT) as ven_fecven, " +
//                    "CAST('' AS TEXT) as cant_dias, " +
//                    "CAST('' AS TEXT) as origen, " +
//                    "CAST('' AS TEXT) as cambio, " +
//                    "CAST('' AS TEXT) as valor, " +
//                    "CAST('' AS TEXT) as moneda, " +
//                    "CAST('' AS TEXT) as exen_dolar, " +
//                    "CAST('' AS TEXT) as concepto, " +
//                    "CAST('' AS TEXT) as cta_iva, " +
//                    "CAST('' AS TEXT) as cta_caja, " +
//                    "CAST('' AS TEXT) as tkdesde, " +
//                    "CAST('' AS TEXT) as tkhasta, " +
//                    "CAST('' AS TEXT) as caja, " +
//                    "CAST('' AS TEXT) as ven_disgra, " +
//                    "CAST('' AS TEXT) as forma_devo, " +
//                    "CAST('' AS TEXT) as ven_cuense, " +
//                    "CAST('' AS TEXT) as anular, " +
//                    "CAST('' AS TEXT) as reproceso, " +
//                    "CAST('' AS TEXT) as cuenta_exe, " +
//                    "CAST('' AS TEXT) as usu_ide, " +
//                    "CAST(t.numero AS TEXT) as rucvennrotim, " +
//                    "CAST('' AS TEXT) as clieasi, " +
//                    "CAST('' AS TEXT) as ventirptip, " +
//                    "CAST('' AS TEXT) as ventirpgra, " +
//                    "CAST('' AS TEXT) as ventirpexe, " +
//                    "CAST('' AS TEXT) as irpc, " +
//                    "CAST('' AS TEXT) as ivasimplificado, " +
//                    "CAST('' AS TEXT) as venIRPrygc, " +
//            "CAST('' AS TEXT) as VenBcoNom, " +
//            "CAST('' AS TEXT) as VenBcoCtaCte, " +
//            "CAST('' AS TEXT) as nofacnotcre, " +
//            "CAST('' AS TEXT) as notimbfacnotcre, " +
//            "CAST('' AS TEXT) as ventipodoc, " +
//            "CAST('' AS TEXT) as VentaNoIva, " +
//            "CAST('' AS TEXT) as IdentifClie, " +
//            "CAST('' AS TEXT) as GDCBIENID, " +
//            "CAST('' AS TEXT) as GDCTIPOBIEN, " +
//            "CAST('' AS TEXT) as GDCIMPCOSTO, " +
//            "CAST('' AS TEXT) as GDCIMPVENTAGRAV " +
//            "from financiero.factura_legal f " +
//            "left join financiero.timbrado_detalle td on f.timbrado_detalle_id = td.id " +
//            "left join financiero.timbrado t on td.timbrado_id = t.id " +
//            "left join empresarial.punto_de_venta pdv on td.punto_de_venta_id = pdv.id " +
//            "left join empresarial.sucursal s on pdv.sucursal_id = s.id " +
//            "where cast(f.creado_en as text) between cast(:inicio as text) and cast(:fin as text) and s.id = :sucursalId " +
//            "group by td.punto_expedicion, s.codigo_establecimiento_factura, f.numero_factura, f.credito, f.ruc, f.nombre, f.creado_en, f.total_final, t.numero " +
//            "order by f.numero_factura", nativeQuery = true)
//    List<FacturaLegal> findExcelFacturas(@Param("inicio") LocalDateTime inicio,
//                                                       @Param("fin") LocalDateTime fin,
//                                                       @Param("sucursalId") Long sucursalId);

}