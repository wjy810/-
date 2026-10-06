package com.jobproof.modules.careerplanning.application;

import com.jobproof.shared.deletion.DeletionModuleHandler;
import com.jobproof.shared.export.AccountExportContributor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Account export and permanent-deletion coverage for the complete planning aggregate. */
@Component
public class CareerPlanningDataLifecycle implements DeletionModuleHandler, AccountExportContributor {
    private static final List<String> ACCOUNT_TABLES = List.of(
            "career_ability_validation_evidences",
            "career_ability_validations",
            "career_learning_weekly_reviews",
            "career_learning_evidences",
            "career_learning_plan_tasks",
            "career_learning_plan_revisions",
            "career_learning_plans",
            "career_canvas_ai_proposal_items",
            "career_canvas_ai_proposals",
            "node_relations",
            "canvas_nodes",
            "canvas_versions",
            "career_goal_transitions",
            "career_goals",
            "career_recommendations",
            "career_recommendation_sets",
            "career_planning_stream_events",
            "career_planning_messages",
            "career_planning_interview_rounds",
            "career_planning_evidence_permissions",
            "career_planning_profile_items",
            "career_planning_profiles",
            "career_planning_sessions");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate deletionTransaction;

    public CareerPlanningDataLifecycle(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.deletionTransaction = new TransactionTemplate(transactionManager);
        this.deletionTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public String moduleCode() {
        return "career-planning";
    }

    @Override
    public String moduleKey() {
        return "careerPlanning";
    }

    @Override
    public Result onAccountDeletion(String accountId) {
        try {
            deletionTransaction.executeWithoutResult(status -> deleteAccountData(accountId));
            return Result.succeeded("职业规划画像、授权、访谈、推荐、目标、画布、计划、验证和版本历史已清理");
        } catch (RuntimeException exception) {
            return Result.failed("职业规划数据清理失败，删除申请保持可重试");
        }
    }

    private void deleteAccountData(String accountId) {
        for (String table : ACCOUNT_TABLES) {
            jdbc.update("DELETE FROM " + table + " WHERE account_id=?", accountId);
        }
        jdbc.update("DELETE FROM async_tasks WHERE account_id=? AND task_type LIKE 'CAREER_PLANNING%'", accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> contribute(String accountId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", "career-planning-v1");
        result.put("sessions", rows("career_planning_sessions", accountId, "created_at"));
        result.put("profiles", rows("career_planning_profiles", accountId, "created_at"));
        result.put("profileItems", rows("career_planning_profile_items", accountId, "created_at"));
        result.put("evidencePermissions", rows("career_planning_evidence_permissions", accountId, "created_at"));
        result.put("interviewRounds", rows("career_planning_interview_rounds", accountId, "created_at"));
        result.put("messages", rows("career_planning_messages", accountId, "sequence_no"));
        result.put("recommendationSets", rows("career_recommendation_sets", accountId, "created_at"));
        result.put("recommendations", rows("career_recommendations", accountId, "created_at"));
        result.put("goals", rows("career_goals", accountId, "created_at"));
        result.put("canvasVersions", rows("canvas_versions", accountId, "version_no"));
        result.put("canvasNodes", rows("canvas_nodes", accountId, "version_id,sort_order"));
        result.put("canvasRelations", rows("node_relations", accountId, "version_id,created_at"));
        result.put("canvasProposals", rows("career_canvas_ai_proposals", accountId, "created_at"));
        result.put("canvasProposalItems", rows("career_canvas_ai_proposal_items", accountId, "proposal_id,sequence_no"));
        result.put("learningPlans", rows("career_learning_plans", accountId, "created_at"));
        result.put("learningPlanRevisions", rows("career_learning_plan_revisions", accountId, "plan_id,revision_no"));
        result.put("learningPlanTasks", rows("career_learning_plan_tasks", accountId, "plan_id,week_no,sort_order"));
        result.put("learningEvidences", rows("career_learning_evidences", accountId, "created_at"));
        result.put("weeklyReviews", rows("career_learning_weekly_reviews", accountId, "plan_id,week_no"));
        result.put("validations", rows("career_ability_validations", accountId, "created_at"));
        result.put("validationEvidences", rows("career_ability_validation_evidences", accountId, "created_at"));
        result.put("goalTransitions", rows("career_goal_transitions", accountId, "created_at"));
        result.put("streamEvents", rows("career_planning_stream_events", accountId, "session_id,sequence_no"));
        return result;
    }

    private List<Map<String, Object>> rows(String table, String accountId, String orderBy) {
        return jdbc.queryForList("SELECT * FROM " + table + " WHERE account_id=? ORDER BY " + orderBy, accountId);
    }
}
