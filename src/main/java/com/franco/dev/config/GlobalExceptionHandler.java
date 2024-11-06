//package com.franco.dev.config;
//
//import com.franco.dev.fmc.model.PushNotificationRequest;
//import com.franco.dev.fmc.service.PushNotificationService;
//import com.franco.dev.service.configuracion.ErrorLogService;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.dao.DataAccessException;
//import org.springframework.dao.DataIntegrityViolationException;
//import org.springframework.dao.DuplicateKeyException;
//import org.springframework.web.bind.annotation.ControllerAdvice;
//import org.springframework.web.bind.annotation.ExceptionHandler;
//
//@ControllerAdvice
//public class GlobalExceptionHandler {
//
//    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
//
//    private final PushNotificationService pushNotificationService;
//    @Autowired
//    private ErrorLogService errorLogService;
//
//    public GlobalExceptionHandler(PushNotificationService pushNotificationService) {
//        this.pushNotificationService = pushNotificationService;
//    }
//
//    private void logAndNotify(TipoError tipoError, NivelError nivelError, String message) {
//        ErrorLog error = new ErrorLog(tipoError, message, nivelError);
//        errorLogService.save(error);
//
//        PushNotificationRequest request = new PushNotificationRequest();
//        request.setTitle(tipoError + " Exception Occurred");
//        request.setMessage(message);
//        request.setTopic("error");
//        request.setData("/");  // Replace with actual target token
//
//        pushNotificationService.sendPushNotificationToToken(request);
//    }
//
//    @ExceptionHandler(Exception.class)
//    public void handleGeneralException(Exception ex) {
//        logAndNotify(TipoError.APLICACION, NivelError.ALERTA, ex.getMessage());
//    }
//
//    @ExceptionHandler(DataAccessException.class)
//    public void handleDatabaseException(DataAccessException ex) {
//        logAndNotify(TipoError.BASE_DE_DATOS, NivelError.ALERTA, ex.getMessage());
//    }
//
//    @ExceptionHandler({DataIntegrityViolationException.class, DuplicateKeyException.class})
//    public void handleDatabaseConstraintViolation(Exception ex) {
//        // Log or send notification for the constraint violation
//        logger.error("Database constraint violation: " + ex.getMessage());
//
//        // Send push notification
//        PushNotificationRequest request = new PushNotificationRequest();
//        request.setTitle("Database Constraint Violation");
//        request.setMessage("Constraint violation occurred: " + ex.getMessage());
//        request.setTopic("alerts");
//
//        pushNotificationService.sendPushNotificationToTopic(request);
//    }
//
//}
//
