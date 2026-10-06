package com.jobproof.shared.workspace;

import com.jobproof.shared.auth.CurrentAccount;

/**
 * 工作台概览扩展点（docs/03 §6.4）。
 *
 * <p>每个业务模块在自己的 application 包中实现一个贡献者，只读取本模块数据并返回一个可序列化的片段。
 * 工作台模块只依赖这个接口，不依赖任何业务模块，从而保持模块边界。
 */
public interface WorkspaceSummaryContributor {

    /** 概览 JSON 中的字段名，例如 {@code resumes}。 */
    String section();

    /** 读取当前账号的概览片段；抛出异常时该片段降级为 null，不影响其他片段。 */
    Object summarize(CurrentAccount current);
}
