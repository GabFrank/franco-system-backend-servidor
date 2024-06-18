package com.franco.dev.config.multitenant;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.service.empresarial.SucursalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.Entity;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@Service
public class MultiTenantService {

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private DatabaseSessionManager databaseSessionManager;

    @Autowired
    private SucursalService sucursalService;

    // Method that accepts a Function with parameters
    public <T, R> R compartir(String key, Function<T, R> someFuncWithParameters, T parameter) {
        try {

            if(key!=null){
                databaseSessionManager.unbindSession();  // Unbind the current session
                tenantContext.setCurrentTenant(key);     // Set the tenant context
                databaseSessionManager.bindSession();    // Bind a new session for the current tenant
                if(isEntity(parameter)){
                    T copy = copyEntityWithId(parameter);
                    return someFuncWithParameters.apply(copy);
                } else {
                    return someFuncWithParameters.apply(parameter);
                }

            } else {
                Set<String> tenantKeys = tenantContext.getAllTenantKeys();
                List<R> resultList = new ArrayList<>();
                for (String s : tenantKeys) {
                    databaseSessionManager.unbindSession();
                    try {
                        tenantContext.setCurrentTenant(s);     // Set the tenant context
                    } catch (Exception e){
                        System.out.println("Sucursal " + s + " no tiene backup configurado");
                        continue;
                    }
                    databaseSessionManager.bindSession();    // Bind a new session for the current tenant
                    if(isEntity(parameter)){
                        T copy = copyEntityWithId(parameter);
                        resultList.addAll((Collection<? extends R>) someFuncWithParameters.apply(copy));
                    } else {
                        resultList.addAll((Collection<? extends R>) someFuncWithParameters.apply(parameter));
                    }

                }
                return (R) parameter;
            }
        } finally {
            // Perform operations after executing the function
            tenantContext.clear();  // Clear the tenant context

            // Optionally rebind the previous session if needed
            // databaseSessionManager.rebindPreviousSession();
        }
    }

    public <R> R compartir(String key, Function<Object[], R> someFuncWithParameters, Object... parameters) {
        try {
            if (key != null) {
                databaseSessionManager.unbindSession();  // Unbind the current session
                tenantContext.setCurrentTenant(key);     // Set the tenant context
                databaseSessionManager.bindSession();    // Bind a new session for the current tenant
                Object[] copiedParameters = copyParametersWithId(parameters);
                return someFuncWithParameters.apply(copiedParameters);
            } else {
                Set<String> tenantKeys = tenantContext.getAllTenantKeys();
                for (String s : tenantKeys) {
                    databaseSessionManager.unbindSession();
                    try {
                        tenantContext.setCurrentTenant(s);  // Set the tenant context
                    } catch (Exception e) {
                        System.out.println("Sucursal " + s + " no tiene backup configurado");
                    }
                    databaseSessionManager.bindSession();  // Bind a new session for the current tenant
                    Object[] copiedParameters = copyParametersWithId(parameters);
                    someFuncWithParameters.apply(copiedParameters);
                }
                return null;  // Adjust this if you need to return something else
            }
        } finally {
            // Perform operations after executing the function
            tenantContext.clear();  // Clear the tenant context

            // Optionally rebind the previous session if needed
            // databaseSessionManager.rebindPreviousSession();
        }
    }

    private Object[] copyParametersWithId(Object[] parameters) {
        Object[] copiedParameters = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Object param = parameters[i];
            if (param != null && isEntity(param)) {
                copiedParameters[i] = copyEntityWithId(param);
            } else {
                copiedParameters[i] = param;
            }
        }
        return copiedParameters;
    }

    private boolean isEntity(Object obj) {
        // Check if the object is an instance of an entity
        return obj.getClass().isAnnotationPresent(Entity.class);
    }

    private <T> T copyEntityWithId(T entity) {
        try {
            // Create a new instance of the entity
            T copy = (T) entity.getClass().getDeclaredConstructor().newInstance();

            // Copy the ID field
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            Object idValue = idField.get(entity);
            idField.set(copy, idValue);

            // Copy other fields as needed (assuming a method to copy properties exists)
            copyProperties(entity, copy);

            return copy;
        } catch (Exception e) {
            throw new RuntimeException("Failed to copy entity", e);
        }
    }

    // Method to copy properties from one entity to another
    private <T> void copyProperties(T source, T target) {
        // Use reflection or a library like BeanUtils to copy properties
        // Example using Apache Commons BeanUtils:
        try {
            org.apache.commons.beanutils.BeanUtils.copyProperties(target, source);
        } catch (Exception e) {
            throw new RuntimeException("Failed to copy properties", e);
        }
    }


}

