package com.franco.dev.config.multitenant;

import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.InventarioService;
import graphql.GraphQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import javax.persistence.Entity;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

@Service
public class MultiTenantService {

    private final Logger log = LoggerFactory.getLogger(MultiTenantService.class);

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private DatabaseSessionManager databaseSessionManager;

    @Autowired
    private SucursalService sucursalService;

    // Method that accepts a Function with parameters
    public <T, R> R compartir(String key, Function<T, R> someFuncWithParameters, T parameter, @Nullable Boolean error) throws GraphQLException {
        try {

            if (key != null) {
                databaseSessionManager.unbindSession();  // Unbind the current session
                tenantContext.setCurrentTenant(key);     // Set the tenant context
                databaseSessionManager.bindSession();    // Bind a new session for the current tenant
                if (isEntity(parameter)) {
                    T copy = copyEntityWithId(parameter);
                    Object result = someFuncWithParameters.apply(copy);
                    if (result instanceof Collection) {
                        return (R) result;
                    } else {
                        return (R) result;
                    }
                } else {
                    Object result = someFuncWithParameters.apply(parameter);
                    if (result instanceof Collection) {
                        return (R) result;
                    } else {
                        return (R) result;
                    }
//                        resultList.addAll((Collection<? extends R>) someFuncWithParameters.apply(parameter));
                }
            } else {
                Set<String> tenantKeys = tenantContext.getAllTenantKeys();
                List<R> resultList = new ArrayList<>();
                for (String s : tenantKeys) {
                    databaseSessionManager.unbindSession();
                    try {
                        tenantContext.setCurrentTenant(s);     // Set the tenant context
                    } catch (Exception e) {
                        System.out.println("Sucursal " + s + " no tiene backup configurado");
                        continue;
                    }
                    databaseSessionManager.bindSession();    // Bind a new session for the current tenant
                    if (isEntity(parameter)) {
                        T copy = copyEntityWithId(parameter);
                        Object result = someFuncWithParameters.apply(copy);
                        if (result instanceof Collection) {
                            resultList.addAll((Collection<? extends R>) result);
                        } else {
                            resultList.add((R) result);
                        }
//                        resultList.addAll((Collection<? extends R>) someFuncWithParameters.apply(copy));
                    } else {
                        Object result = someFuncWithParameters.apply(parameter);
                        if (result instanceof Collection) {
                            resultList.addAll((Collection<? extends R>) result);
                        } else {
                            resultList.add((R) result);
                        }
//                        resultList.addAll((Collection<? extends R>) someFuncWithParameters.apply(parameter));
                    }

                }
                return (R) resultList;
            }
        } catch (Exception e) {
            if (error) {
                throw new GraphQLException("Problema al realizar la operacion");
            } else {
                e.printStackTrace();
            }
            return null;
        } finally {
            // Perform operations after executing the function
            tenantContext.clear();  // Clear the tenant context

            // Optionally rebind the previous session if needed
            // databaseSessionManager.rebindPreviousSession();
        }
    }

    public <T, R> R compartir(String key, Function<T, R> someFuncWithParameters, T parameter) {
        try {

            if (key != null) {
                databaseSessionManager.unbindSession();  // Unbind the current session
                tenantContext.setCurrentTenant(key);     // Set the tenant context
                databaseSessionManager.bindSession();    // Bind a new session for the current tenant
                if (parameter != null && isEntity(parameter)) {
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
                    } catch (Exception e) {
                        System.out.println("Sucursal " + s + " no tiene backup configurado");
                        continue;
                    }
                    databaseSessionManager.bindSession();    // Bind a new session for the current tenant
                    if (isEntity(parameter)) {
                        T copy = copyEntityWithId(parameter);
                        Object result = someFuncWithParameters.apply(copy);
                        if (result instanceof Collection) {
                            resultList.addAll((Collection<? extends R>) result);
                        } else {
                            resultList.add((R) result);
                        }
//                        resultList.addAll((Collection<? extends R>) someFuncWithParameters.apply(copy));
                    } else {
                        Object result = someFuncWithParameters.apply(parameter);
                        if (result instanceof Collection) {
                            resultList.addAll((Collection<? extends R>) result);
                        } else {
                            resultList.add((R) result);
                        }
//                        resultList.addAll((Collection<? extends R>) someFuncWithParameters.apply(parameter));
                    }

                }
                return (R) resultList;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            // Perform operations after executing the function
            tenantContext.clear();  // Clear the tenant context

            // Optionally rebind the previous session if needed
            // databaseSessionManager.rebindPreviousSession();
        }
    }

    public <R> R compartir(String key, Function<Object[], R> someFuncWithParameters, Object... parameters) {
        ExecutorService executorService = null;
        try {
            if (key != null) {
                databaseSessionManager.unbindSession();  // Unbind the current session
                tenantContext.setCurrentTenant(key);     // Set the tenant context
                databaseSessionManager.bindSession();    // Bind a new session for the current tenant
                Object[] copiedParameters = copyParametersWithId(parameters);
                return someFuncWithParameters.apply(copiedParameters);
            } else {
                Set<String> tenantKeys = tenantContext.getAllTenantKeys();
                executorService = Executors.newFixedThreadPool(tenantKeys.size());
                List<Future<R>> futures = new ArrayList<>();

                for (String tenantKey : tenantKeys) {
                    Future<R> future = executorService.submit(() -> {
                        databaseSessionManager.unbindSession();
                        try {
                            tenantContext.setCurrentTenant(tenantKey);  // Set the tenant context
                            databaseSessionManager.bindSession();       // Bind a new session for the current tenant
                            Object[] copiedParameters = copyParametersWithId(parameters);
                            return someFuncWithParameters.apply(copiedParameters);
                        } catch (Exception e) {
                            System.out.println("Sucursal " + tenantKey + " no tiene backup configurado");
                            return null;
                        } finally {
                            tenantContext.clear();  // Clear the tenant context after each execution
                        }
                    });
                    futures.add(future);
                }

                // Gather all the results
                List<R> results = new ArrayList<>();
                for (Future<R> future : futures) {
                    try {
                        R result = future.get(); // Blocks until the thread is done
                        if (result != null) {
                            results.add(result);
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                }

                // Combine or return the results as needed
                // Assuming you need to combine them into a list or similar, otherwise, modify the return type and logic accordingly.
                return (R) results;

            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (executorService != null) {
                executorService.shutdown();
            }
            tenantContext.clear();  // Clear the tenant context
        }
    }

    public <R> R compartir(String key, @Nullable Boolean getError, Function<Object[], R> someFuncWithParameters, Object... parameters) {
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
        } catch (Exception e) {
            if (getError) {
                throw new GraphQLException("Problema al realizar la operacion");
            } else {
                log.info("No se pudo establecer conexion con la sucursal: " + key);
            }
            return null;
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
        if(obj==null) return false;
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

    public Boolean checkConnection(Boolean throwError, String... parameters){
        Boolean res = true;
        try {
            for(String tenantKey: parameters){
                Object obj = compartir(tenantKey, (Long id) -> sucursalService.findById(id), Long.valueOf(1), true);
            }
        } catch (NullPointerException e){
            if(throwError){
                throw new GraphQLException("Alguna filial no esta configurada");
            } else {
                e.printStackTrace();
            }
            res = false;
        }
        return res;
    }

}