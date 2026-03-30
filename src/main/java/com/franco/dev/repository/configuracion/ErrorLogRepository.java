package com.franco.dev.repository.configuracion;

import com.franco.dev.config.ErrorLog;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.repository.HelperRepository;

public interface ErrorLogRepository extends HelperRepository<ErrorLog, EmbebedPrimaryKey> {

    default Class<ErrorLog> getEntityClass() {
        return ErrorLog.class;
    }

}