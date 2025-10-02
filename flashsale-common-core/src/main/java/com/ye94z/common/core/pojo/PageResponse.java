package com.ye94z.common.core.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 通用分页返回
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class PageResponse<T> implements Serializable {
    private long total;       // 总条数
    private int pages;        // 总页数
    private int pageNum;      // 当前页码（从1开始）
    private int pageSize;     // 每页大小
    private List<T> items;    // 数据
}