package com.jobproof.modules.resume.domain;

import com.jobproof.shared.error.AppException;
import java.util.List;
import java.util.stream.Collectors;

public final class ResumeFreezePolicy {

    private ResumeFreezePolicy() {
    }

    public static void assertNoPendingAi(boolean pendingAi, List<String> pendingIds) {
        if (pendingAi) {
            String hint = pendingIds == null || pendingIds.isEmpty()
                    ? ""
                    : " 未确认项=" + String.join(",", pendingIds);
            throw AppException.conflict(
                    "UNCONFIRMED_AI_FACTS",
                    "存在未确认的 AI 事实，不能标记为可导出或冻结。请先确认、更正或删除候选。" + hint);
        }
    }

    public static void assertOutcomesResolved(List<KeyOutcome> outcomes) {
        if (outcomes == null || outcomes.isEmpty()) {
            return;
        }
        List<String> unresolved = outcomes.stream()
                .filter(item -> !item.resolved())
                .map(item -> item.text() == null || item.text().isBlank() ? item.id() : item.text())
                .collect(Collectors.toList());
        if (!unresolved.isEmpty()) {
            throw AppException.conflict(
                    "OUTCOME_EVIDENCE_REQUIRED",
                    "关键成果须关联允许使用的证据，或逐条确认「暂无证据仍要冻结」。未处理："
                            + String.join("；", unresolved));
        }
    }

    public static void assertReadyAndResolved(
            ResumeMasterStatus status,
            boolean pendingAi,
            List<String> pendingIds,
            List<KeyOutcome> outcomes) {
        assertNoPendingAi(pendingAi, pendingIds);
        assertOutcomesResolved(outcomes);
        if (status != ResumeMasterStatus.READY_TO_EXPORT) {
            throw AppException.conflict("MASTER_NOT_READY_TO_EXPORT", "只有可导出主档才能冻结版本");
        }
    }

    public static void assertCanMarkReady(
            ResumeMasterStatus status,
            boolean pendingAi,
            List<String> pendingIds,
            List<KeyOutcome> outcomes) {
        ResumeMasterPolicy.assertEditable(status);
        assertNoPendingAi(pendingAi, pendingIds);
        assertOutcomesResolved(outcomes);
    }

    public static void assertContentImmutable(ResumeVersionStatus status) {
        if (status.contentImmutable()) {
            throw AppException.conflict("RESUME_VERSION_IMMUTABLE", "已冻结或已归档版本不可原地修改或覆盖");
        }
    }

    public static void assertBindable(ResumeVersionStatus status) {
        if (!status.bindable()) {
            throw AppException.conflict(
                    "RESUME_VERSION_NOT_FROZEN",
                    "只有已冻结版本可以进入后续导出流程，不能使用草稿或未确认版本");
        }
    }

    public static void assertCustomizeNotFrozen(ResumeVersionStatus status) {
        if (status.frozenOrBound()) {
            throw AppException.conflict("RESUME_VERSION_IMMUTABLE", "定制任务不得把版本直接变成已冻结或已绑定");
        }
    }
}
