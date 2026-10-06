package com.jobproof.shared.concurrency;

import com.jobproof.shared.error.AppException;

public final class Versions {

    private Versions() {
    }

    public static void assertExpected(Integer expected, int actual) {
        if (expected != null && expected != actual) {
            throw AppException.conflict(
                    "VERSION_CONFLICT",
                    "对象版本冲突，已拒绝覆盖。请刷新后查看双方内容再选择。当前版本=" + actual);
        }
    }
}
