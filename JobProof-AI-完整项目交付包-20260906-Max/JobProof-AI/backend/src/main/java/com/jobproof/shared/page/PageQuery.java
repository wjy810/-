package com.jobproof.shared.page;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class PageQuery {

    @Min(0)
    private int page = 0;

    @Min(1)
    @Max(100)
    private int size = 20;

    public int page() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int size() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
