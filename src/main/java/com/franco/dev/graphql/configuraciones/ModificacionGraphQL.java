package com.franco.dev.graphql.configuraciones;

import com.franco.dev.domain.configuraciones.ModificacionDetalle;
import com.franco.dev.domain.configuraciones.ModificacionRegistro;
import com.franco.dev.repository.configuraciones.ModificacionDetalleRepository;
import com.franco.dev.repository.configuraciones.ModificacionRegistroRepository;
import com.franco.dev.service.configuraciones.ModificacionService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ModificacionGraphQL implements GraphQLQueryResolver {

    @Autowired
    private ModificacionService service;

    @Autowired
    private ModificacionRegistroRepository registroRepository;

    @Autowired
    private ModificacionDetalleRepository detalleRepository;

    public ModificacionRegistro modificacionRegistro(Long id) {
        return service.findById(id).orElse(null);
    }

    public ModificacionRegistroPage modificacionesPorSchema(
            String schemaNombre,
            LocalDateTime inicio,
            LocalDateTime fin,
            Integer page,
            Integer size
    ) {
        if (page == null) page = 0;
        if (size == null) size = 15;
        
        Pageable pageable = PageRequest.of(page, size);
        Page<ModificacionRegistro> pageResult = registroRepository.findBySchemaNombreAndFechaBetween(
                schemaNombre, inicio, fin, pageable
        );
        
        ModificacionRegistroPage result = new ModificacionRegistroPage();
        result.setContent(pageResult.getContent());
        result.setPageNumber(page);
        result.setPageSize(size);
        result.setTotalElements((int) pageResult.getTotalElements());
        result.setTotalPages(pageResult.getTotalPages());
        
        return result;
    }

    public ModificacionRegistroPage modificacionesPorTipoEntidad(
            String tipoEntidad,
            Integer page,
            Integer size
    ) {
        if (page == null) page = 0;
        if (size == null) size = 15;
        
        Pageable pageable = PageRequest.of(page, size);
        Page<ModificacionRegistro> pageResult = registroRepository.findByTipoEntidad(
                tipoEntidad, pageable
        );
        
        ModificacionRegistroPage result = new ModificacionRegistroPage();
        result.setContent(pageResult.getContent());
        result.setPageNumber(page);
        result.setPageSize(size);
        result.setTotalElements((int) pageResult.getTotalElements());
        result.setTotalPages(pageResult.getTotalPages());
        
        return result;
    }

    public List<ModificacionDetalle> detallesModificacion(Integer modificacionRegistroId) {
        return detalleRepository.findByModificacionRegistroId(modificacionRegistroId.longValue());
    }

    // Clase interna para el resultado paginado
    public static class ModificacionRegistroPage {
        private List<ModificacionRegistro> content;
        private Integer pageNumber;
        private Integer pageSize;
        private Integer totalElements;
        private Integer totalPages;

        public List<ModificacionRegistro> getContent() {
            return content;
        }

        public void setContent(List<ModificacionRegistro> content) {
            this.content = content;
        }

        public Integer getPageNumber() {
            return pageNumber;
        }

        public void setPageNumber(Integer pageNumber) {
            this.pageNumber = pageNumber;
        }

        public Integer getPageSize() {
            return pageSize;
        }

        public void setPageSize(Integer pageSize) {
            this.pageSize = pageSize;
        }

        public Integer getTotalElements() {
            return totalElements;
        }

        public void setTotalElements(Integer totalElements) {
            this.totalElements = totalElements;
        }

        public Integer getTotalPages() {
            return totalPages;
        }

        public void setTotalPages(Integer totalPages) {
            this.totalPages = totalPages;
        }
    }
}

