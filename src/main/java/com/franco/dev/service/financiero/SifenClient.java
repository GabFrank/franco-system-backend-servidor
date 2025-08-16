package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SifenClient {

    // URLs SIFEN (por defecto a ambiente de pruebas)
    @Value("${sifen.wsdl.sync.recibe:https://sifen-test.set.gov.py/de/ws/sync/recibe.wsd?wsdl}")
    private String syncRecibeUrl;

    @Value("${sifen.wsdl.async.recibe-lote:https://sifen-test.set.gov.py/de/ws/async/recibe-lote.wsdl?wsdl}")
    private String asyncRecibeLoteUrl;

    @Value("${sifen.wsdl.eventos:https://sifen-test.set.gov.py/de/ws/eventos/evento.wsdl?wsdl}")
    private String eventosUrl;

    @Value("${sifen.wsdl.consulta:https://sifen-test.set.gov.py/de/ws/consultas/consulta.wsdl?wsdl}")
    private String consultaUrl;

    @Value("${sifen.wsdl.consulta-lote:https://sifen-test.set.gov.py/de/ws/consultas/consulta-lote.wsdl?wsdl}")
    private String consultaLoteUrl;

    @Value("${sifen.wsdl.consulta-ruc:https://sifen-test.set.gov.py/de/ws/consultas/consulta-ruc.wsdl?wsdl}")
    private String consultaRucUrl;

    public String enviarLote(List<DocumentoElectronico> documentos) {
        // Placeholder: aquí se invocaría el WS de envío de lote con los XMLs firmados
        return "dummy-protocol-" + System.currentTimeMillis();
    }

    public String consultarLote(String idProtocolo) {
        // Placeholder: aquí se invocaría el WS de consulta de lote
        return "<respuesta lote='" + idProtocolo + "'>OK</respuesta>";
    }
}


