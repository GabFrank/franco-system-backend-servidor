package com.franco.dev.service;

import com.franco.dev.domain.EmbebedPrimaryKey;

public class EmbeddedEntity {
    private Long id;
    private Long sucursalId;

    public EmbebedPrimaryKey getEId(){
        return new EmbebedPrimaryKey(id, sucursalId);
    }

}
