package com.jobproof.modules.site.application;

import com.jobproof.infrastructure.config.JobProofProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * What this deployment offers, for the signed-out landing page and the app navigation: which
 * features are switched on, whether registration is open, and who operates the site. The
 * frontend hides or marks unavailable features instead of letting users hit a disabled API.
 */
@Service
public class SiteCapabilitiesService {
    private final JobProofProperties properties;
    private final Features features;

    public SiteCapabilitiesService(
            JobProofProperties properties,
            @Value("${jobproof.ai.workbench.enabled:false}") boolean aiWorkbench,
            @Value("${jobproof.job-match.enabled:false}") boolean jobMatch,
            @Value("${jobproof.career-planning.enabled:false}") boolean careerPlanning,
            @Value("${jobproof.ai.gateway.enabled:false}") boolean aiGateway) {
        this.properties = properties;
        this.features = new Features(aiWorkbench, jobMatch, careerPlanning, aiGateway);
    }

    public Capabilities capabilities() {
        JobProofProperties.Policies policies = properties.getPolicies();
        JobProofProperties.Operator operator = properties.getOperator();
        return new Capabilities(
                features,
                policies.isRegistrationOpen(),
                new PolicyStatus(policies.isPublished(), policies.getTermsVersion(), policies.getPrivacyVersion()),
                new OperatorView(operator.getName(), operator.getIcpRecord(), operator.getPublicSecurityRecord(),
                        operator.getContactEmail(), operator.getContactPhone(), operator.getAddress(),
                        operator.getFeedbackUrl()));
    }

    /** {@code aiAssistant}: the model gateway is configured (mock interview scoring, resume suggestions). */
    public record Features(boolean aiWorkbench, boolean jobMatch, boolean careerPlanning, boolean aiAssistant) {}
    public record PolicyStatus(boolean published, String termsVersion, String privacyVersion) {}
    public record OperatorView(String name, String icpRecord, String publicSecurityRecord, String contactEmail,
            String contactPhone, String address, String feedbackUrl) {}
    public record Capabilities(Features features, boolean registrationOpen, PolicyStatus policies, OperatorView operator) {}
}
