package com.jobproof.modules.workspace.application;

import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.workspace.WorkspaceSummaryContributor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 聚合各模块贡献的工作台片段。单个模块失败只会让对应片段为 null 并记入 degraded，
 * 其他片段照常返回（docs/01 DASH 验收：任一子数据失败不影响其他区块）。
 */
@Service
public class WorkspaceOverviewService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceOverviewService.class);

    private final List<WorkspaceSummaryContributor> contributors;

    public WorkspaceOverviewService(List<WorkspaceSummaryContributor> contributors) {
        this.contributors = List.copyOf(contributors);
    }

    public Map<String, Object> overview(CurrentAccount current) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> degraded = new ArrayList<>();
        for (WorkspaceSummaryContributor contributor : contributors) {
            try {
                result.put(contributor.section(), contributor.summarize(current));
            } catch (RuntimeException failure) {
                log.warn("workspace section degraded section={} reason={}", contributor.section(), failure.toString());
                result.put(contributor.section(), null);
                degraded.add(contributor.section());
            }
        }
        result.put("degraded", List.copyOf(degraded));
        return result;
    }
}
