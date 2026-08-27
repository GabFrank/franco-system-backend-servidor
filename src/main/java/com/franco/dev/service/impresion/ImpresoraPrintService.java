package com.franco.dev.service.impresion;

import com.franco.dev.domain.empresarial.Impresora;
import com.franco.dev.domain.empresarial.enums.TipoConexion;
import com.franco.dev.utilitarios.print.PerfilPapelHelper;
import com.franco.dev.utilitarios.print.TicketFormato;
import com.franco.dev.utilitarios.print.escpos.EscPos;
import com.franco.dev.utilitarios.print.output.PrinterOutputStream;
import com.franco.dev.utilitarios.print.output.TcpIpOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.print.PrintService;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Abre el destino de impresion correcto a partir de una {@link Impresora} del registro:
 * <ul>
 *   <li>CUPS / USB / SMB / BLUETOOTH: cola local del sistema via {@link PrinterOutputStream}.
 *       En SMB la cola apunta a un share de Windows (device-uri {@code smb://...}); el que habla
 *       CIFS es el backend smb de CUPS, para Java es una cola mas.</li>
 *   <li>RED / inalambrica: socket a IP:puerto via {@link TcpIpOutputStream} (RAW/JetDirect).</li>
 * </ul>
 * Devuelve un {@link EscPos} listo para escribir y el {@link TicketFormato} con las
 * columnas correspondientes al perfil de papel.
 *
 * NOTA: este servicio es la capa de ejecucion local. El ruteo filial<->central (decidir
 * si imprime local o reenvia a otro host segun {@code impresora.sucursal}) es Fase 3.
 */
@Service
public class ImpresoraPrintService {

    private static final Logger log = LoggerFactory.getLogger(ImpresoraPrintService.class);
    private static final int PUERTO_RAW_POR_DEFECTO = 9100;

    /** Abre el OutputStream crudo hacia la impresora (CUPS o TCP/IP). */
    public OutputStream abrirOutput(Impresora impresora) throws IOException {
        if (impresora == null) {
            throw new IllegalArgumentException("Impresora nula");
        }
        if (impresora.getConexion() == TipoConexion.RED) {
            int puerto = impresora.getPuerto() != null ? impresora.getPuerto() : PUERTO_RAW_POR_DEFECTO;
            log.info("Imprimiendo por RED a {}:{}", impresora.getIp(), puerto);
            return new TcpIpOutputStream(impresora.getIp(), puerto);
        }
        // CUPS / USB / SMB / BLUETOOTH: cola local por nombre. En SMB el device-uri de la cola
        // apunta al share de Windows y smbspool hace la entrega; aca no cambia nada.
        PrintService ps = PrinterOutputStream.getPrintServiceByName(impresora.getColaCups());
        log.info("Imprimiendo por cola local '{}'", impresora.getColaCups());
        return new PrinterOutputStream(ps);
    }

    /** Abre un EscPos listo para escribir sobre el destino de la impresora. */
    public EscPos abrirEscPos(Impresora impresora) throws IOException {
        return new EscPos(abrirOutput(impresora));
    }

    /** Formato de columnas de la impresora (campo {@code columnas} o el default del perfil). */
    public TicketFormato formato(Impresora impresora) {
        Integer columnas = impresora != null ? impresora.getColumnas() : null;
        if (columnas == null && impresora != null) {
            columnas = PerfilPapelHelper.columnas(impresora.getPerfilPapel());
        }
        return new TicketFormato(columnas);
    }
}
