package com.jobproof.modules.taxonomy.web;

import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.taxonomy.application.JobTaxonomyService;
import com.jobproof.modules.taxonomy.application.JobTaxonomyService.NodeView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/job-taxonomy")
public class JobTaxonomyController {

    private final JobTaxonomyService taxonomy;

    public JobTaxonomyController(JobTaxonomyService taxonomy) {
        this.taxonomy = taxonomy;
    }

    @GetMapping
    public ApiResponse<List<NodeView>> list(@RequestParam(required = false) String parentId,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(taxonomy.tree(parentId, keyword));
    }
}
