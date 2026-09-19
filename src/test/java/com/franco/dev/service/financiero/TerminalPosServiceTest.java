package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.FormatoTerminalPos;
import com.franco.dev.domain.financiero.TerminalPos;
import com.franco.dev.repository.financiero.TerminalPosRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Identidad y configuracion de una terminal.
 *
 * <p>Dos grupos de casos, y los dos vienen de un error que ya se cometio en el diseno:
 *
 * <ul>
 *   <li><b>La serie.</b> El diseno original tenia UN indice unico sobre (proveedor, serie) y no
 *       protegia nada: Postgres no compara NULLs como iguales, y las dos terminales que existen
 *       tienen el proveedor en NULL. El caso roto era el caso actual.</li>
 *   <li><b>El interruptor de carga manual.</b> Es la salida universal en la que se apoya el cierre
 *       del camino equivocado. Apagarlo cuando es el ultimo deja a una caja sin poder cobrar con
 *       tarjeta, sin que nadie avise.</li>
 * </ul>
 */
public class TerminalPosServiceTest {

    private static final String MAPEO =
            "{\"monto\":{\"de\":\"monto\",\"obligatorio\":true},"
            + "\"codigoAutorizacion\":{\"de\":\"auth\",\"obligatorio\":true},"
            + "\"terminal\":{\"de\":\"term\"}}";

    private TerminalPosRepository repository;
    private TerminalPosService service;

    @BeforeEach
    public void setUp() {
        repository = mock(TerminalPosRepository.class);
        service = new TerminalPosService(repository);
        when(repository.save(org.mockito.ArgumentMatchers.<TerminalPos>any()))
                .thenAnswer(i -> i.getArgument(0));
    }

    private TerminalPos terminal(String tipoFormato) {
        TerminalPos t = new TerminalPos();
        t.setId(3L);
        t.setDescripcion("Caja 1");
        if (tipoFormato != null) {
            FormatoTerminalPos f = new FormatoTerminalPos();
            f.setId(9L);
            f.setNombre("Bancard v5.2");
            f.setTipo(tipoFormato);
            f.setMapeo(MAPEO);
            t.setFormatoTerminalPos(f);
        }
        return t;
    }

    // ── Identidad ───────────────────────────────────────────────────────────────────────────

    @Test
    public void la_serie_se_guarda_en_mayusculas_y_sin_espacios() {
        // Se tipea mirando una etiqueta pegada al aparato, que es donde aparecen estas diferencias.
        // Sin normalizar, jf798sjj y JF798SJJ son dos maquinas para Postgres y una sola para
        // cualquier persona --y los indices unicos comparan la columna cruda--.
        TerminalPos t = terminal(null);
        t.setSerie("  jf798sjj ");

        service.save(t);

        assertEquals("JF798SJJ", t.getSerie());
    }

    @Test
    public void una_serie_vacia_se_guarda_como_null() {
        // Los indices son parciales sobre serie IS NOT NULL: dos terminales con '' chocarian entre
        // si sin ningun motivo.
        TerminalPos t = terminal(null);
        t.setSerie("   ");

        service.save(t);

        assertNull(t.getSerie());
    }

    @Test
    public void una_serie_repetida_sin_proveedor_se_rechaza() {
        // EL caso que el indice original no cubria, porque las dos terminales reales tienen el
        // proveedor en NULL.
        TerminalPos otra = terminal(null);
        otra.setId(99L);
        otra.setDescripcion("Caja 2");
        when(repository.findByProveedorServicioIsNullAndSerie("JF798SJJ")).thenReturn(otra);

        TerminalPos t = terminal(null);
        t.setSerie("JF798SJJ");

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.save(t));
        assertTrue(e.getMessage().contains("Caja 2"), e.getMessage());
    }

    @Test
    public void guardar_la_misma_terminal_no_choca_con_su_propia_serie() {
        TerminalPos t = terminal(null);
        t.setSerie("JF798SJJ");
        when(repository.findByProveedorServicioIsNullAndSerie("JF798SJJ")).thenReturn(t);

        assertDoesNotThrow(() -> service.save(t));
    }

    @Test
    public void un_codigo_repetido_se_rechaza() {
        // El codigo es lo que el cajero escanea, y el dialogo se queda con resultados[0]: repetido,
        // el cobro va contra la maquina equivocada sin ningun aviso.
        TerminalPos otra = terminal(null);
        otra.setId(99L);
        otra.setDescripcion("Caja 2");
        when(repository.findByCodigoIgnoreCase("A1")).thenReturn(otra);

        TerminalPos t = terminal(null);
        t.setCodigo("A1");

        assertThrows(GraphQLException.class, () -> service.save(t));
    }

    // ── El interruptor de carga manual ──────────────────────────────────────────────────────

    @Test
    public void no_se_puede_apagar_la_carga_manual_sin_formato() {
        // Sin formato la venta con tarjeta ya esta bloqueada: la carga a mano es lo unico que
        // queda. Apagarla deja la caja sin ninguna forma de cobrar.
        TerminalPos t = terminal(null);

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.configurar(t, Boolean.FALSE, null));

        assertTrue(e.getMessage().contains("no tiene formato"), e.getMessage());
    }

    @Test
    public void con_formato_MAQUINA_si_se_puede_apagar() {
        // La camara queda como camino abierto.
        TerminalPos t = terminal(FormatoTerminalPos.TIPO_MAQUINA);

        service.configurar(t, Boolean.FALSE, null);

        assertEquals(Boolean.FALSE, t.getCargaManualPermitida());
    }

    @Test
    public void con_formato_WEB_tambien() {
        TerminalPos t = terminal(FormatoTerminalPos.TIPO_WEB);

        assertDoesNotThrow(() -> service.configurar(t, Boolean.FALSE, null));
    }

    @Test
    public void con_formato_API_no_se_puede_apagar_todavia() {
        // API entra al modelo pero su driver no existe. Apagar la carga manual seria apoyarse en
        // una integracion que no esta.
        TerminalPos t = terminal(FormatoTerminalPos.TIPO_API);

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.configurar(t, Boolean.FALSE, null));

        assertTrue(e.getMessage().contains("API"), e.getMessage());
    }

    @Test
    public void permitirla_o_heredarla_nunca_se_valida() {
        // La restriccion es sobre APAGAR. Prenderla o volver a heredar no puede cerrar nada.
        TerminalPos t = terminal(null);

        assertDoesNotThrow(() -> service.configurar(t, Boolean.TRUE, null));
        assertDoesNotThrow(() -> service.configurar(t, null, null));
        assertNull(t.getCargaManualPermitida(), "null = hereda la configuracion general");
    }

    // ── Campos obligatorios por aparato ─────────────────────────────────────────────────────

    @Test
    public void solo_puede_apretar_no_aflojar() {
        // El mapeo declara monto y codigoAutorizacion obligatorios. Una lista que deje afuera uno
        // de esos usaria esta pantalla --que parece menor-- para saltear la validacion del formato,
        // que comparten todas las terminales del mismo modelo.
        TerminalPos t = terminal(FormatoTerminalPos.TIPO_MAQUINA);

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.configurar(t, null, Collections.singletonList("monto")));

        assertTrue(e.getMessage().contains("codigoAutorizacion"), e.getMessage());
    }

    @Test
    public void apretar_agregando_uno_mas_si_vale() {
        TerminalPos t = terminal(FormatoTerminalPos.TIPO_MAQUINA);

        service.configurar(t, null, Arrays.asList("monto", "codigoAutorizacion", "terminal"));

        List<String> guardados = service.camposObligatoriosDe(t);
        assertEquals(3, guardados.size());
        assertTrue(guardados.contains("terminal"));
    }

    @Test
    public void un_campo_que_el_formato_no_produce_bloquearia_todas_las_ventas() {
        TerminalPos t = terminal(FormatoTerminalPos.TIPO_MAQUINA);

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.configurar(t, null,
                        Arrays.asList("monto", "codigoAutorizacion", "numeroBoleta")));

        assertTrue(e.getMessage().contains("no produce"), e.getMessage());
    }

    @Test
    public void una_lista_vacia_vuelve_a_heredar() {
        TerminalPos t = terminal(FormatoTerminalPos.TIPO_MAQUINA);

        service.configurar(t, null, Collections.<String>emptyList());

        assertNull(t.getCamposObligatorios());
        assertNull(service.camposObligatoriosDe(t));
    }

    @Test
    public void sin_lista_propia_los_efectivos_salen_del_mapeo() {
        // Una sola fuente de verdad para las tres cosas que deciden los obligatorios: que tiene que
        // encontrar el OCR, cuando el resultado es utilizable, y que pide la carga a mano.
        TerminalPos t = terminal(FormatoTerminalPos.TIPO_MAQUINA);

        List<String> efectivos = service.camposObligatoriosEfectivos(t);

        assertEquals(Arrays.asList("monto", "codigoAutorizacion"), efectivos);
    }

    @Test
    public void sin_formato_ni_lista_los_efectivos_son_vacio() {
        assertTrue(service.camposObligatoriosEfectivos(terminal(null)).isEmpty());
    }

    @Test
    public void una_lista_ilegible_se_comporta_como_no_configurada() {
        // Estado seguro: se cae al mapeo del formato en vez de tumbar el listado de terminales.
        TerminalPos t = terminal(FormatoTerminalPos.TIPO_MAQUINA);
        t.setCamposObligatorios("{esto no es un array}");

        assertNull(service.camposObligatoriosDe(t));
        assertEquals(Arrays.asList("monto", "codigoAutorizacion"), service.camposObligatoriosEfectivos(t));
    }

    // ── Quitar el formato: el otro lado de la misma regla ───────────────────────────────────

    @Test
    public void no_se_puede_quitar_el_formato_si_la_carga_a_mano_esta_apagada() {
        // EL hallazgo de la auditoria, hecho test. La regla del ultimo camino estaba protegida en
        // una sola direccion: se impedia apagar la carga manual sin otro camino abierto, pero
        // quedaba la secuencia inversa --apagar la carga manual con el formato puesto, que esta
        // permitido, y despues quitarle el formato--. La terminal terminaba sin ninguna forma de
        // cobrar con tarjeta y nadie lo detectaba.
        TerminalPos t = terminal(FormatoTerminalPos.TIPO_MAQUINA);
        t.setCargaManualPermitida(Boolean.FALSE);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.desasignarFormato(t));

        assertTrue(e.getMessage().contains("carga a mano"), e.getMessage());
        assertNotNull(t.getFormatoTerminalPos(), "el formato no se tiene que haber tocado");
    }

    @Test
    public void con_la_carga_a_mano_disponible_si_se_puede_quitar() {
        TerminalPos t = terminal(FormatoTerminalPos.TIPO_MAQUINA);
        t.setCargaManualPermitida(Boolean.TRUE);

        assertTrue(service.desasignarFormato(t));
        assertNull(t.getFormatoTerminalPos());
    }

    @Test
    public void heredar_la_configuracion_general_no_bloquea_quitar_el_formato() {
        // null = hereda, y la general hoy permite la carga a mano. Bloquear aca obligaria a tocar
        // una perilla que nadie configuro.
        TerminalPos t = terminal(FormatoTerminalPos.TIPO_MAQUINA);
        t.setCargaManualPermitida(null);

        assertTrue(service.desasignarFormato(t));
    }

    // ── Normalizacion del codigo ───────────────────────────────────────────────────────────

    @Test
    public void un_codigo_de_solo_espacios_se_guarda_como_null() {
        // La validacion se saltea cuando el codigo esta en blanco, pero el indice unico de V224.5
        // es parcial sobre `codigo <> \'\'` y "   " NO es cadena vacia: dos terminales asi pasaban
        // la frase y chocaban contra Postgres, y el operador se comia el error crudo.
        TerminalPos t = terminal(null);
        t.setCodigo("   ");

        service.save(t);

        assertNull(t.getCodigo());
    }

    @Test
    public void el_codigo_se_guarda_sin_espacios_al_borde() {
        TerminalPos t = terminal(null);
        t.setCodigo("  A1  ");

        service.save(t);

        assertEquals("A1", t.getCodigo());
    }

    // ── Busqueda exacta por serie ──────────────────────────────────────────────────────────

    @Test
    public void la_busqueda_por_serie_no_deja_pasar_comodines_de_like() {
        // El valor viene del propio cupon --texto libre capturado por el regex-- y quien llama lo
        // acepta sin preguntar cuando hay uno solo. Por eso la consulta es exacta: un `%` aca
        // seria un comodin de SQL y podria resolver contra la maquina equivocada.
        service.findPorSerie("JF%");

        verify(repository).findBySerieIgnoreCaseAndActivoTrue("JF%");
        verify(repository, never()).filterTerminalPos(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void una_serie_vacia_no_consulta_la_base() {
        assertTrue(service.findPorSerie("   ").isEmpty());
        assertTrue(service.findPorSerie(null).isEmpty());
        verify(repository, never()).findBySerieIgnoreCaseAndActivoTrue(anyString());
    }
}
