package ru.itmo.spacemarine.dto;

import java.util.List;

public class PageDto<T> {
    public List<T> content;
    public int page;
    public int size;
    public long totalElements;
    public int totalPages;

    public PageDto() {
    }

    public PageDto(List<T> content, int page, int size, long totalElements) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = size == 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }
}
