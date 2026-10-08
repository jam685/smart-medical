package com.hospital.smartmedical.common.result;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {
    private long total;
    private List<T> list;
    private int page;
    private int size;

    public PageResult() {}

    public PageResult(long total, List<T> list, int page, int size) {
        this.total = total;
        this.list = list;
        this.page = page;
        this.size = size;
    }

    public static <T> PageResult<T> of(long total, List<T> list, int page, int size) {
        return new PageResult<>(total, list, page, size);
    }
}
