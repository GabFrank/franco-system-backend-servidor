package com.franco.dev.config.multitenant;

import org.springframework.data.domain.Page;

import java.util.List;

public interface CustomPage<T> extends Page<T> {
    List<MultiPage> getMultiPageableList();
    void setMultiPageableList(List<MultiPage> multiPageableList);
}
