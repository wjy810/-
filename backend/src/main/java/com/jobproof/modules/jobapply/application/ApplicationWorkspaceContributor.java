package com.jobproof.modules.jobapply.application;

import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.workspace.WorkspaceSummaryContributor;
import org.springframework.stereotype.Component;

/** 工作台：投递进展、最近被打开的简历与临近的下一步。 */
@Component
class ApplicationWorkspaceContributor implements WorkspaceSummaryContributor {

    private final JobApplicationService applications;

    ApplicationWorkspaceContributor(JobApplicationService applications) {
        this.applications = applications;
    }

    @Override
    public String section() {
        return "applications";
    }

    @Override
    public Object summarize(CurrentAccount current) {
        return current.operator() ? null : applications.summary(current.accountId());
    }
}
