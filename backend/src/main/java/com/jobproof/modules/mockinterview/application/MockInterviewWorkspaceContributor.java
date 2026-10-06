package com.jobproof.modules.mockinterview.application;

import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.workspace.WorkspaceSummaryContributor;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 工作台：模拟面试统计与可继续的场次。 */
@Component
class MockInterviewWorkspaceContributor implements WorkspaceSummaryContributor {

    private final MockInterviewService interviews;

    MockInterviewWorkspaceContributor(MockInterviewService interviews) {
        this.interviews = interviews;
    }

    @Override
    public String section() {
        return "mockInterviews";
    }

    @Override
    public Object summarize(CurrentAccount current) {
        MockInterviewService.DashboardView dashboard = interviews.dashboard(current);
        Map<String, Object> result = new HashMap<>();
        result.put("total", dashboard.total());
        result.put("completed", dashboard.completed());
        result.put("averageScore", dashboard.completed() > 0 ? dashboard.averageScore() : null);
        MockInterviewService.SessionSummary resumable = dashboard.resumable();
        if (resumable == null) {
            result.put("resumable", null);
        } else {
            Map<String, Object> session = new HashMap<>();
            session.put("id", resumable.id());
            session.put("title", resumable.title());
            session.put("positionName", resumable.positionName());
            session.put("answeredCount", resumable.answeredCount());
            session.put("questionCount", resumable.questionCount());
            session.put("updatedAt", resumable.updatedAt());
            result.put("resumable", session);
        }
        return result;
    }
}
