package com.franco.dev.service;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.repository.HelperRepository;
import com.franco.dev.service.productos.ProductoService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public abstract class CrudService<T, Repository extends HelperRepository<T, S>, S> implements ICrudService<Repository> {

    public static final String GUARDAR = "0";
    public static final String ELIMINAR = "1";
    public static final String ACTUALIZAR = "2";

    public static final Logger log = Logger.getLogger(String.valueOf(CrudService.class));

    public List<T> findAll(Pageable pageable){

        return (List<T>) getRepository().findAllByOrderByIdAsc(pageable);
    }

    public List<T> findAll2(){
        return (List<T>) getRepository().findAllByOrderByIdAsc();
    }

    public Optional<T> findById(S id){
        if(id==null){
            return null;
        }
        T obj = (T) getRepository().findById(id);
        return (Optional<T>) obj;
    }

    public T getOne(S id){return getRepository().getOne(id);}

    @Transactional
    public T save(T entity){
        return (T) getRepository().save(entity);
    }
    @Transactional
    public T saveAndSend(T entity, Boolean recibir){
        return (T) getRepository().save(entity);
    }
    @Transactional
    public Boolean deleteById(S id){
        try {
            getRepository().deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional
    public Boolean delete(T entity){
        try {
            getRepository().delete(entity);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional
    public List<T> saveAll(List<T> entityList){
        return (List<T>) getRepository().saveAll(entityList);
    }

    public Long count(){
        return getRepository().count();
    }



}
