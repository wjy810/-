package com.jobproof.modules.site.application;

import com.jobproof.infrastructure.config.JobProofProperties;
import com.jobproof.modules.aigateway.port.AiChannelPort;
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
    private final AiChannelPort channels;
    private final String resumeModel;
    private final String jobMatchModel;
    private final String careerPlanningModel;

    public SiteCapabilitiesService(
            JobProofProperties properties,
            AiChannelPort channels,
            @Value("${jobproof.ai.workbench.enabled:false}") boolean aiWorkbench,
            @Value("${jobproof.job-match.enabled:false}") boolean jobMatch,
            @Value("${jobproof.career-planning.enabled:false}") boolean careerPlanning,
            @Value("${jobproof.ai.gateway.enabled:false}") boolean aiGateway,
            @Value("${jobproof.ai.resume-model:qwen-plus}") String resumeModel,
            @Value("${jobproof.job-match.model:qwen-plus}") String jobMatchModel,
            @Value("${jobproof.career-planning.model:qwen-plus}") String careerPlanningModel) {
        this.properties = properties;
        this.features = new Features(aiWorkbench, jobMatch, careerPlanning, aiGateway);
        this.channels = channels;
        this.resumeModel = resumeModel;
        this.jobMatchModel = jobMatchModel;
        this.careerPlanningModel = careerPlanningModel;
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
                        operator.getFeedbackUrl()),
                new AiReadiness(ready(resumeModel), ready(jobMatchModel), ready(careerPlanningModel)));
    }

    /** A platform channel can serve this model right now (personal channels are per user and not counted). */
    private boolean ready(String model) {
        if (!features.aiAssistant() || model == null || model.isBlank()) return false;
        try {
            return !channels.findEligible("", model.trim()).isEmpty();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** {@code aiAssistant}: the model gateway is configured (mock interview scoring, resume suggestions). */
    public record Features(boolean aiWorkbench, boolean jobMatch, boolean careerPlanning, boolean aiAssistant) {}
    public record PolicyStatus(boolean published, String termsVersion, String privacyVersion) {}
    public record OperatorView(String name, String icpRecord, String publicSecurityRecord, String contactEmail,
            String contactPhone, String address, String feedbackUrl) {}
    /**
     * Whether each AI feature has a model to talk to, so pages can say so up front instead of failing after
     * the user has filled everything in. {@code resume} covers the resume workbench and mock interview.
     */
    public record AiReadiness(boolean resume, boolean jobMatch, boolean careerPlanning) {}
    public record Capabilities(Features features, boolean registrationOpen, PolicyStatus policies, OperatorView operator,
            AiReadiness ai) {}
}
