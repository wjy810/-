package com.jobproof.shared.export;

import java.util.Map;

/**
 * 账号级导出扩展点，各核心模块提供自己的账户数据片段。
 */
public interface AccountExportContributor {

    String moduleKey();

    Map<String, Object> contribute(String accountId);
}
