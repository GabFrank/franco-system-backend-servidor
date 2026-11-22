package com.franco.dev.service;

import com.franco.dev.repository.HelperRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.persistence.IdClass;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

@Service
public abstract class CrudService<T, Repository extends HelperRepository<T, S>, S> implements ICrudService<Repository> {

    public static final String GUARDAR = "0";
    public static final String ELIMINAR = "1";
    public static final String ACTUALIZAR = "2";
    public static final Logger log = Logger.getLogger(String.valueOf(CrudService.class));
    @Autowired
    public Environment env;
    /**
     * Las entidades con PK (id, sucursal_id) repiten el id entre sucursales: ordenadas solo por id, al
     * pasar de pagina las filas con el mismo id salian repetidas o no salian (#674). En esas el orden
     * termina en la sucursal; en las demas queda findAllByOrderByIdAsc, como siempre.
     */
    static final Sort ORDEN_PK_COMPUESTA = Sort.by("id", "sucursalId");

    private Boolean pkCompuesta;

    public List<T> findAll(Pageable pageable) {
        if (tienePkCompuesta()) {
            Pageable conSucursal = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    ORDEN_PK_COMPUESTA.and(pageable.getSort()));
            return getRepository().findAll(conSucursal).getContent();
        }
        return (List<T>) getRepository().findAllByOrderByIdAsc(pageable);
    }

    /** true si la entidad del servicio se identifica por (id, sucursal_id), con {@code @IdClass}. */
    boolean tienePkCompuesta() {
        if (pkCompuesta == null) {
            Class<?> entidad = ResolvableType.forClass(getClass()).as(CrudService.class).getGeneric(0).resolve();
            pkCompuesta = entidad != null && entidad.isAnnotationPresent(IdClass.class);
        }
        return pkCompuesta;
    }

    public List<T> findAll2() {
        return (List<T>) getRepository().findAllByOrderByIdAsc();
    }

    public Optional<T> findById(S id) {
        if (id == null) {
            return null;
        }
        T obj = (T) getRepository().findById(id);
        return (Optional<T>) obj;
    }

//    public T findByIdAndSucursalId(Long id, Long sucId){
//        return getRepository().findByIdAndSucursalId(id, sucId);
//    }

    public T getOne(S id) {
        return getRepository().getOne(id);
    }

    @Transactional
    public T save(T entity) {
        return (T) getRepository().save(entity);
    }

    @Transactional
    public T saveAndSend(T entity, Boolean recibir) {
        return (T) getRepository().save(entity);
    }

    @Transactional
    public T saveAndSend(T entity, Long sucId) {
        return (T) getRepository().save(entity);
    }

    @Transactional
    public Boolean deleteById(S id) {
        try {
            getRepository().deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    

    @Transactional
    public Boolean delete(T entity) {
        try {
            getRepository().delete(entity);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

//    @Transactional
//    public Boolean deleteByIdAndSucursalId(Long id, Long sucId) {
//        try {
//            getRepository().deleteByIdAndSucursalId(id, sucId);
//            return true;
//        } catch (Exception e) {
//            return false;
//        }
//    }

    @Transactional
    public List<T> saveAll(List<T> entityList) {
        return (List<T>) getRepository().saveAll(entityList);
    }

    public Long count() {
        return getRepository().count();
    }
}
