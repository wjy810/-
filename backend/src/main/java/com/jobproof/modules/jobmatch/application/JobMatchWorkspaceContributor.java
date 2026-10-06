package com.jobproof.modules.jobmatch.application;

import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.workspace.WorkspaceSummaryContributor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 工作台：岗位匹配数量与最近任务。 */
@Component
class JobMatchWorkspaceContributor implements WorkspaceSummaryContributor {

    private final JobMatchService matches;

    JobMatchWorkspaceContributor(JobMatchService matches) {
        this.matches = matches;
    }

    @Override
    public String section() {
        return "jobMatches";
    }

    @Override
    public Object summarize(CurrentAccount current) {
        JobMatchService.DashboardView dashboard = matches.dashboard(current);
        List<Map<String, Object>> recent = dashboard.recent().stream().limit(3).map(item -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", item.id());
            row.put("title", item.title());
            row.put("company", item.company());
            row.put("status", item.status());
            row.put("score", item.score());
            row.put("updatedAt", item.updatedAt());
            return row;
        }).toList();
        return Map.of("total", dashboard.total(), "completed", dashboard.completed(), "recent", recent);
    }
}
