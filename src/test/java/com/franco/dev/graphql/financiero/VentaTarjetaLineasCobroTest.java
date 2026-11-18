package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.domain.operaciones.Venta;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Lineas de cobro de venta_tarjeta.completada desde la app (srv #587, previo al piloto). */
class VentaTarjetaLineasCobroTest {

    private static VentaTarjeta tarjeta() {
        Cobro c = new Cobro();
        c.setId(80098L);
        Venta v = new Venta();
        v.setId(80098L);
        v.setSucursalId(2L);
        v.setCobro(c);
        VentaTarjeta vt = new VentaTarjeta();
        vt.setId(22L);
        vt.setSucursalId(2L);
        vt.setVenta(v);
        return vt;
    }

    private static VentaTarjetaGraphQL resolverQueFalla(RuntimeException e) {
        VentaTarjetaGraphQL r = new VentaTarjetaGraphQL();
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.execute(any(ConnectionCallback.class))).thenThrow(e);
        ReflectionTestUtils.setField(r, "jdbcTemplate", jdbc);
        return r;
    }

    @Test
    void siFallaLaConsultaDevuelveNullYNoPropaga() {
        assertNull(resolverQueFalla(new BadSqlGrammarException("lineas", "SELECT", new SQLException("no existe")))
                .lineasCobro(tarjeta()));
    }

    @Test
    void siVenceElTimeoutDevuelveNullYNoPropaga() {
        assertNull(resolverQueFalla(new QueryTimeoutException("2 s")).lineasCobro(tarjeta()));
    }

    @Test
    void siFallaLaConversionDevuelveNullYNoPropaga() {
        assertNull(resolverQueFalla(new IllegalStateException("conversion")).lineasCobro(tarjeta()));
    }

    /** La linea leida por JDBC (numeric como BigDecimal) tiene la misma forma que la de EventosVenta.lineaCobro. */
    @Test
    @SuppressWarnings("unchecked")
    void laLineaPorJdbcTieneLaMismaFormaQueLaDeLaEntidad() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(115116L);
        when(rs.getBigDecimal("valor")).thenReturn(new BigDecimal("6000.00"));
        when(rs.getBigDecimal("cambio")).thenReturn(new BigDecimal("1.0000"));
        when(rs.getObject("pago", Boolean.class)).thenReturn(true);
        when(rs.getObject("vuelto", Boolean.class)).thenReturn(false);
        when(rs.getObject("descuento", Boolean.class)).thenReturn(false);
        when(rs.getObject("aumento", Boolean.class)).thenReturn(true);
        when(rs.getString("identificador_transaccion")).thenReturn("E2E-END2END");
        when(rs.getObject("moneda_id", Long.class)).thenReturn(1L);
        when(rs.getString("denominacion")).thenReturn("GUARANI");
        when(rs.getObject("forma_pago_id", Long.class)).thenReturn(2L);
        when(rs.getString("descripcion")).thenReturn("TARJETA");

        Moneda m = new Moneda();
        m.setId(1L);
        m.setDenominacion("GUARANI");
        FormaPago fp = new FormaPago();
        fp.setId(2L);
        fp.setDescripcion("TARJETA");
        CobroDetalle cd = new CobroDetalle();
        cd.setId(115116L);
        cd.setValor(6000.0);
        cd.setCambio(1.0);
        cd.setPago(true);
        cd.setVuelto(false);
        cd.setDescuento(false);
        cd.setAumento(true);
        cd.setIdentificadorTransaccion("E2E-END2END");
        cd.setMoneda(m);
        cd.setFormaPago(fp);
        Method entidad = com.franco.dev.eventos.EventosVenta.class.getDeclaredMethod("lineaCobro", CobroDetalle.class);
        entidad.setAccessible(true);

        Map<String, Object> porJdbc = VentaTarjetaGraphQL.lineaCobro(rs);
        assertEquals(entidad.invoke(null, cd), porJdbc);
        assertEquals(Double.class, porJdbc.get("valor").getClass());
    }

    @Test
    void montosNulosQuedanNulos() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getBigDecimal("valor")).thenReturn(null);
        when(rs.getBigDecimal("cambio")).thenReturn(null);
        Map<String, Object> l = VentaTarjetaGraphQL.lineaCobro(rs);
        assertNull(l.get("valor"));
        assertNull(l.get("cambio"));
    }
}
