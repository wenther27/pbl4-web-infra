package com.example.cinehub.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PageResponse<T> {
    private List<T> content;    // danh sách phim trang hiện tại
    private int currentPage;
    private int totalPages;
    private long totalElements;    //Tổng số phim
    private boolean isLast;

    public PageResponse(List<T> content, int currentPage, int totalPages, long totalElements, boolean isLast) {
        this.content = content;
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        this.totalElements = totalElements;
        this.isLast = isLast;
    }
}
