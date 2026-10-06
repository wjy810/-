package com.jobproof.modules.airesume.application;

import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.workspace.WorkspaceSummaryContributor;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 工作台：简历数量、可导出数量与最近编辑的简历（含 AI 卡片确认进度和最近使用的模板）。
 */
@Component
class ResumeWorkspaceContributor implements WorkspaceSummaryContributor {

    private static final int RECENT_LIMIT = 4;

    private final JdbcTemplate jdbc;

    ResumeWorkspaceContributor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String section() {
        return "resumes";
    }

    @Override
    public Object summarize(CurrentAccount current) {
        if (!"SEEKER".equals(current.role())) {
            throw AppException.forbidden("SEEKER_ONLY", "仅求职者账号拥有简历");
        }
        String accountId = current.accountId();
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT m.id, m.title, m.status, m.updated_at, c.id conversation_id, c.onboarding_stage
                  FROM resume_masters m
                  LEFT JOIN ai_resume_conversations c ON c.master_id=m.id AND c.account_id=m.account_id
                 WHERE m.account_id=? AND m.status<>'ARCHIVED'
                 ORDER BY m.updated_at DESC
                """, (rs, n) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("title", rs.getString("title"));
                    row.put("status", rs.getString("status"));
                    Timestamp updated = rs.getTimestamp("updated_at");
                    row.put("updatedAt", updated == null ? null : updated.toInstant());
                    row.put("conversationId", rs.getString("conversation_id"));
                    row.put("onboardingStage", rs.getString("onboarding_stage"));
                    return row;
                }, accountId);

        long exportable = rows.stream()
                .filter(row -> "READY_TO_EXPORT".equals(row.get("status")) || "READY_FOR_PREVIEW".equals(row.get("onboardingStage")))
                .count();

        List<Map<String, Object>> recent = new ArrayList<>();
        for (Map<String, Object> row : rows.subList(0, Math.min(RECENT_LIMIT, rows.size()))) {
            Map<String, Object> item = new HashMap<>(row);
            item.remove("onboardingStage");
            String conversationId = (String) row.get("conversationId");
            int total = 0;
            int confirmed = 0;
            if (conversationId != null) {
                Map<String, Object> counts = jdbc.queryForMap("""
                        SELECT COUNT(*) total, COALESCE(SUM(CASE WHEN status='CONFIRMED' THEN 1 ELSE 0 END),0) confirmed
                          FROM ai_resume_cards WHERE account_id=? AND conversation_id=?
                        """, accountId, conversationId);
                total = ((Number) counts.get("total")).intValue();
                confirmed = ((Number) counts.get("confirmed")).intValue();
            }
            item.put("totalModules", total);
            item.put("confirmedModules", confirmed);
            item.put("templateName", jdbc.query("""
                    SELECT t.display_name
                      FROM resume_layout_preferences p
                      JOIN resume_layout_templates t ON t.id=p.template_id
                     WHERE p.account_id=? AND p.master_id=?
                     ORDER BY p.updated_at DESC
                     LIMIT 1
                    """, (rs, n) -> rs.getString(1), accountId, row.get("id")).stream().findFirst().orElse(null));
            recent.add(item);
        }
        return Map.of("total", rows.size(), "exportable", exportable, "recent", recent);
    }
}
