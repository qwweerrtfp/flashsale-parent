package com.ye94z.common.core.dto;

import java.io.Serializable;
import java.util.List;

/**
 * 通用分页返回
 */
public class PageResponse<T> implements Serializable {
    private long total;       // 总条数
    private int pages;        // 总页数
    private int pageNum;      // 当前页码（从1开始）
    private int pageSize;     // 每页大小
    private List<T> items;    // 数据

    public PageResponse() {}

    public static <T> PageResponse<T> of(long total, int pages, int pageNum, int pageSize, List<T> items) {
        PageResponse<T> pr = new PageResponse<>();
        pr.setTotal(total);
        pr.setPages(pages);
        pr.setPageNum(pageNum);
        pr.setPageSize(pageSize);
        pr.setItems(items);
        return pr;
    }

    // getters & setters
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public int getPages() { return pages; }
    public void setPages(int pages) { this.pages = pages; }
    public int getPageNum() { return pageNum; }
    public void setPageNum(int pageNum) { this.pageNum = pageNum; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    public List<T> getItems() { return items; }
    public void setItems(List<T> items) { this.items = items; }
}