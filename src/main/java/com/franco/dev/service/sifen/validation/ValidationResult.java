package com.franco.dev.service.sifen.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultado de una validación que puede contener errores o advertencias
 */
public class ValidationResult {
    private boolean valid;
    private List<String> errors;
    private List<String> warnings;

    public ValidationResult() {
        this.valid = true;
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void addError(String error) {
        this.errors.add(error);
        this.valid = false;
    }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    public void addErrors(List<String> errors) {
        this.errors.addAll(errors);
        if (!errors.isEmpty()) {
            this.valid = false;
        }
    }

    public void addWarnings(List<String> warnings) {
        this.warnings.addAll(warnings);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public String getErrorSummary() {
        if (errors.isEmpty()) {
            return "Sin errores";
        }
        return String.join("; ", errors);
    }

    public String getWarningSummary() {
        if (warnings.isEmpty()) {
            return "Sin advertencias";
        }
        return String.join("; ", warnings);
    }
}
