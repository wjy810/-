package com.jobproof.modules.airesume.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobproof.shared.error.AppException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeWritingQualityPolicyTest {
    private final ResumeWritingQualityPolicy policy = new ResumeWritingQualityPolicy();

    @Test
    void acceptsSubstantiveExperienceBulletWithinFieldSpecificRange() {
        String value = "通过梳理接口调用链和异常日志，设计并实现分层排查与回归验证流程，解决联调阶段的边界问题，形成可复用的检查清单并支持后续稳定交付";
        var result = policy.validate("EXPERIENCE", "/experiences/0/description", value, false);
        assertThat(result.passed()).isTrue();
        assertThat(result.metrics()).containsEntry("policyVersion", ResumeWritingQualityPolicy.VERSION);
    }

    @Test
    void rejectsShortAndHollowWritingInsteadOfPaddingIt() {
        assertThatThrownBy(() -> policy.validate("EXPERIENCE", "/experiences/0/description",
                "负责相关工作并完成相关任务", false))
                .isInstanceOf(AppException.class)
                .extracting(value -> ((AppException) value).reason())
                .isEqualTo("AI_CHANGE_LENGTH_INVALID");
    }

    @Test
    void rejectsDuplicateSuggestionsInOneChangeSet() {
        String value = "通过梳理接口调用链和异常日志设计排查流程并完成回归验证";
        assertThatThrownBy(() -> policy.assertDistinct(List.of(value, value + "。")))
                .isInstanceOf(AppException.class)
                .extracting(candidate -> ((AppException) candidate).reason())
                .isEqualTo("AI_CHANGE_DUPLICATE");
    }
}
