package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.FacturaProveedorImport;
import com.franco.dev.domain.operaciones.enums.EstadoImport;
import com.franco.dev.graphql.operaciones.dto.FacturaImportPreviewDto;
import com.franco.dev.graphql.operaciones.input.ArchivoImportInput;
import com.franco.dev.graphql.operaciones.input.ConfirmarFacturaImportItemInput;
import com.franco.dev.service.operaciones.FacturaImportConfirmService;
import com.franco.dev.service.operaciones.FacturaImportOrchestratorService;
import com.franco.dev.service.operaciones.FacturaProveedorImportService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Component
public class FacturaProveedorImportGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final Logger log = LoggerFactory.getLogger(FacturaProveedorImportGraphQL.class);

    @Autowired
    private FacturaProveedorImportService service;

    @Autowired
    private FacturaImportOrchestratorService orchestrator;

    @Autowired
    private FacturaImportConfirmService confirmService;

    public Optional<FacturaProveedorImport> facturaProveedorImport(Long id) {
        return service.findById(id);
    }

    public Optional<FacturaImportPreviewDto> facturaImportPreview(Long id) {
        return confirmService.preview(id);
    }

    public FacturaProveedorImport confirmarFacturaImport(Long importId, Integer proveedorId,
                                                         List<ConfirmarFacturaImportItemInput> items,
                                                         String tipoBoleta, Boolean crearPedido,
                                                         Integer usuarioId) {
        Long pid = proveedorId != null ? proveedorId.longValue() : null;
        Long uid = usuarioId != null ? usuarioId.longValue() : null;
        return confirmService.confirmar(importId, pid, items, tipoBoleta, crearPedido, uid);
    }

    public Page<FacturaProveedorImport> facturaProveedorImports(String estado, Integer page, Integer size) {
        EstadoImport est = null;
        if (estado != null && !estado.isEmpty()) {
            try {
                est = EstadoImport.valueOf(estado);
            } catch (IllegalArgumentException ex) {
                log.warn("Estado desconocido ignorado: {}", estado);
            }
        }
        int p = page != null ? page : 0;
        int s = size != null ? size : 20;
        return service.findPaged(est, p, s);
    }

    public FacturaProveedorImport importarFacturaProveedor(List<ArchivoImportInput> archivos,
                                                            Integer usuarioId) {
        if (archivos == null || archivos.isEmpty()) {
            throw new IllegalArgumentException("archivos requerido");
        }
        List<FacturaImportOrchestratorService.ArchivoImport> lista = new ArrayList<>();
        for (ArchivoImportInput in : archivos) {
            byte[] bytes = decodificarBase64(in.getArchivoBase64());
            lista.add(new FacturaImportOrchestratorService.ArchivoImport(
                    bytes, in.getNombreArchivo(), in.getMimeType()));
        }
        Long uid = usuarioId != null ? usuarioId.longValue() : null;
        return orchestrator.procesarArchivos(lista, uid);
    }

    private byte[] decodificarBase64(String base64) {
        if (base64 == null) {
            throw new IllegalArgumentException("archivoBase64 requerido");
        }
        // Soportar data URI prefix (ej "data:application/pdf;base64,JVBERi0xLi4u")
        String cleaned = base64;
        int commaIdx = base64.indexOf(',');
        if (base64.startsWith("data:") && commaIdx > 0) {
            cleaned = base64.substring(commaIdx + 1);
        }
        return Base64.getDecoder().decode(cleaned);
    }
}
