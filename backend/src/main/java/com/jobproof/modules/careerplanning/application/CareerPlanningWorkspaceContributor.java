package com.jobproof.modules.careerplanning.application;

import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.CareerCanvasDashboard;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.workspace.WorkspaceSummaryContributor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 工作台：能力画布数量与最近画布进度。 */
@Component
class CareerPlanningWorkspaceContributor implements WorkspaceSummaryContributor {

    private final CareerPlanningService planning;

    CareerPlanningWorkspaceContributor(CareerPlanningService planning) {
        this.planning = planning;
    }

    @Override
    public String section() {
        return "careerCanvases";
    }

    @Override
    public Object summarize(CurrentAccount current) {
        CareerCanvasDashboard dashboard = planning.dashboard(current, null, "ALL", "RECENT");
        List<Map<String, Object>> recent = dashboard.items().stream().limit(2).map(item -> {
            Map<String, Object> row = new HashMap<>();
            row.put("sessionId", item.sessionId());
            row.put("title", item.title());
            row.put("progress", item.overallProgress());
            row.put("nodeCount", item.nodeCount());
            row.put("updatedAt", item.updatedAt());
            return row;
        }).toList();
        return Map.of("total", dashboard.stats().canvasCount(), "recent", recent);
    }
}
