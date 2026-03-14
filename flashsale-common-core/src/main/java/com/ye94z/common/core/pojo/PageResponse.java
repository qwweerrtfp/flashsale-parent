package com.ye94z.common.core.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 通用分页返回模型。
 * 这里没有绑定任何分页框架，调用方只需要按约定填充总数、页码和数据列表即可。
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class PageResponse<T> implements Serializable {
    /** 总记录数。 */
    private long total;
    /** 总页数。 */
    private int pages;
    /** 当前页码，从 1 开始。 */
    private int pageNum;
    /** 当前页大小。 */
    private int pageSize;
    /** 当前页的数据项。 */
    private List<T> items;
}
