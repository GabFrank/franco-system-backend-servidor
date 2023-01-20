package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Maletin;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.repository.financiero.MaletinRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class MaletinService extends CrudService<Maletin, MaletinRepository, Long> {

    @Autowired
    private PropagacionService propagacionService;

    private final MaletinRepository repository;

    @Override
    public MaletinRepository getRepository() {
        return repository;
    }

//    public List<Maletin> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public Maletin findByDescripcion(String texto){
        Maletin m = repository.findByDescripcionIgnoreCase(texto);
        return m;
    }

    public List<Maletin> searchByAll(String texto){
        texto = texto.toUpperCase();
        return repository.findByAll(texto);
    }

    @Override
    public Maletin save(Maletin entity) {
        if(entity.getId()==null) entity.setCreadoEn(LocalDateTime.now());
        if(entity.getCreadoEn()==null) entity.setCreadoEn(LocalDateTime.now()   );
        Maletin e = super.save(entity);
//        personaPublisher.publish(p);
        propagacionService.propagarEntidad(e, TipoEntidad.MALETIN);
        return e;
    }

    public Maletin abrirMaletin(Long id){
        Maletin m = findById(id).orElse(null);
        if(m!=null){
            m.setAbierto(true);
            return save(m);
        }
        return null;
    }

    public Maletin cerrarMaletin(Long id){
        Maletin m = findById(id).orElse(null);
        if(m!=null){
            m.setAbierto(false);
            return save(m);
        }
        return null;
    }
}