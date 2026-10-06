package com.jobproof.modules.career.application;

import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.workspace.WorkspaceSummaryContributor;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 工作台：求职资料完整度与待完善项。 */
@Component
class CareerWorkspaceContributor implements WorkspaceSummaryContributor {

    private final CareerLibraryService library;

    CareerWorkspaceContributor(CareerLibraryService library) {
        this.library = library;
    }

    @Override
    public String section() {
        return "profile";
    }

    @Override
    public Object summarize(CurrentAccount current) {
        CareerLibraryService.OverviewView overview = library.overview(current);
        return Map.of(
                "completeness", overview.profileCompleteness(),
                "missing", overview.missingItems().stream().map(CareerLibraryService.MissingItem::label).toList());
    }
}
