package com.franco.dev.config.multitenant;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

public class CustomPageImpl<T> extends PageImpl<T> implements CustomPage<T> {
    private List<MultiPage> multiPageableList;

    public CustomPageImpl(List<T> content, Pageable pageable, long total, List<MultiPage> multiPageableList) {
        super(content, pageable, total);
        this.multiPageableList = multiPageableList;
    }

    @Override
    public List<MultiPage> getMultiPageableList() {
        return multiPageableList;
    }

    @Override
    public void setMultiPageableList(List<MultiPage> multiPageableList) {
        this.multiPageableList = multiPageableList;
    }

    // Other necessary methods can be overridden if needed
}