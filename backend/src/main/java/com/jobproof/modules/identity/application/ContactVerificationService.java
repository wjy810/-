package com.jobproof.modules.identity.application;

import com.jobproof.infrastructure.config.JobProofProperties;
import com.jobproof.modules.identity.domain.AccountRules;
import com.jobproof.modules.identity.domain.VerificationChannel;
import com.jobproof.modules.identity.domain.VerificationDeliveryMessage;
import com.jobproof.modules.identity.domain.VerificationDeliveryPort;
import com.jobproof.modules.identity.domain.VerificationDeliveryReceipt;
import com.jobproof.modules.identity.domain.VerificationMode;
import com.jobproof.modules.identity.domain.VerificationPurpose;
import com.jobproof.modules.identity.infra.ContactVerificationChallengeEntity;
import com.jobproof.modules.identity.infra.ContactVerificationChallengeJpaRepository;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.security.Tokens;
import com.jobproof.shared.time.ClockPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactVerificationService {

    private static final int CODE_LENGTH = 6;

    private final ContactVerificationChallengeJpaRepository challenges;
    private final List<VerificationDeliveryPort> deliveryPorts;
    private final JobProofProperties properties;
    private final ClockPort clock;
    private final VerificationHasher hasher;
    private final VerificationRateLimitService rateLimits;

    public ContactVerificationService(
            ContactVerificationChallengeJpaRepository challenges,
            List<VerificationDeliveryPort> deliveryPorts,
            JobProofProperties properties,
            ClockPort clock,
            VerificationHasher hasher,
            VerificationRateLimitService rateLimits) {
        this.challenges = challenges;
        this.deliveryPorts = deliveryPorts;
        this.properties = properties;
        this.clock = clock;
        this.hasher = hasher;
        this.rateLimits = rateLimits;
    }

    @Transactional(readOnly = true)
    public Capabilities capabilities() {
        return new Capabilities(
                deliveryPort(VerificationChannel.EMAIL).isPresent(),
                deliveryPort(VerificationChannel.SMS).isPresent(),
                CODE_LENGTH,
                properties.getVerification().getCodeTtlMinutes(),
                properties.getVerification().getResendCooldownSeconds(),
                properties.getPolicies().isRegistrationOpen(),
                properties.getPolicies().getTermsVersion(),
                properties.getPolicies().getPrivacyVersion());
    }

    @Transactional
    public RequestResult request(
            VerificationChannel channel,
            String rawDestination,
            VerificationPurpose purpose,
            String requestIp) {
        if (channel == null || purpose == null) {
            throw AppException.user("VERIFICATION_REQUEST_INVALID", "验证方式和用途不能为空");
        }
        String destination = normalizeDestination(channel, rawDestination);
        VerificationDeliveryPort port = deliveryPort(channel).orElseThrow(ContactVerificationService::unavailable);
        Instant now = clock.now();
        String destinationHash = hasher.contactHash(channel, destination);
        enforceCooldown(channel, destinationHash, purpose, now);
        String ipHash = hasher.ipHash(requestIp == null || requestIp.isBlank() ? "unknown" : requestIp);
        rateLimits.checkAndRecord(channel, destinationHash, ipHash, now);

        String challengeId = Ids.newId();
        String code = port.deliveryMode() == VerificationMode.LOCAL_CODE ? Tokens.sixDigitCode() : null;
        Instant expiresAt = now.plus(Duration.ofMinutes(properties.getVerification().getCodeTtlMinutes()));
        ContactVerificationChallengeEntity challenge = new ContactVerificationChallengeEntity();
        challenge.setId(challengeId);
        challenge.setChannel(channel.name());
        challenge.setDestinationHash(destinationHash);
        challenge.setDestinationMasked(maskDestination(channel, destination));
        challenge.setPurpose(purpose.name());
        challenge.setProviderCode(port.providerCode());
        challenge.setVerificationMode(port.deliveryMode().name());
        challenge.setCodeHash(code == null ? null : hasher.codeHash(challengeId, code));
        challenge.setRequestIpHash(ipHash);
        challenge.setExpiresAt(expiresAt);
        challenge.setFailedAttempts(0);
        challenge.setCreatedAt(now);
        challenges.saveAndFlush(challenge);

        try {
            VerificationDeliveryReceipt receipt = port.deliver(new VerificationDeliveryMessage(
                    channel, destination, purpose, code, properties.getVerification().getCodeTtlMinutes()));
            if (receipt.mode() != port.deliveryMode()) throw unavailable();
            challenge.setProviderRequestId(receipt.providerRequestId());
            challenges.saveAndFlush(challenge);
        } catch (RuntimeException exception) {
            challenges.delete(challenge);
            challenges.flush();
            throw exception instanceof AppException ? exception : unavailable();
        }
        return new RequestResult(
                challengeId,
                channel,
                challenge.getDestinationMasked(),
                expiresAt,
                properties.getVerification().getResendCooldownSeconds());
    }

    @Transactional(noRollbackFor = AppException.class)
    public ConfirmationResult confirm(String challengeId, String rawDestination, String rawCode) {
        if (challengeId == null || challengeId.isBlank()) throw invalidCode();
        ContactVerificationChallengeEntity challenge = challenges.findByIdForUpdate(challengeId)
                .orElseThrow(ContactVerificationService::invalidCode);
        Instant now = clock.now();
        if (!openForConfirmation(challenge, now)) {
            expireIfNecessary(challenge, now);
            throw invalidCode();
        }

        VerificationChannel channel = VerificationChannel.valueOf(challenge.getChannel());
        String destination;
        try {
            destination = normalizeDestination(channel, rawDestination);
        } catch (AppException exception) {
            registerFailure(challenge, now);
            throw invalidCode();
        }
        if (!constantTimeEquals(challenge.getDestinationHash(), hasher.contactHash(channel, destination))) {
            registerFailure(challenge, now);
            throw invalidCode();
        }
        String code = rawCode == null ? "" : rawCode.trim();
        if (!code.matches("\\d{" + CODE_LENGTH + "}")) {
            registerFailure(challenge, now);
            throw invalidCode();
        }

        VerificationMode mode = VerificationMode.valueOf(challenge.getVerificationMode());
        boolean valid;
        if (mode == VerificationMode.LOCAL_CODE) {
            valid = constantTimeEquals(challenge.getCodeHash(), hasher.codeHash(challenge.getId(), code));
        } else {
            VerificationDeliveryPort port = deliveryPortByCode(challenge.getProviderCode(), channel)
                    .orElseThrow(ContactVerificationService::unavailable);
            valid = port.verify(
                    destination,
                    VerificationPurpose.valueOf(challenge.getPurpose()),
                    code,
                    challenge.getProviderRequestId());
        }
        if (!valid) {
            registerFailure(challenge, now);
            throw invalidCode();
        }

        String token = Tokens.randomToken();
        challenge.setVerifiedAt(now);
        challenge.setVerificationTokenHash(hasher.tokenHash(token));
        challenge.setVerificationTokenExpiresAt(now.plus(
                Duration.ofMinutes(properties.getVerification().getTokenTtlMinutes())));
        challenges.saveAndFlush(challenge);
        return new ConfirmationResult(
                token,
                channel,
                VerificationPurpose.valueOf(challenge.getPurpose()),
                challenge.getDestinationMasked(),
                challenge.getVerificationTokenExpiresAt());
    }

    @Transactional
    public VerifiedContact consumeVerifiedToken(
            String rawToken,
            VerificationChannel channel,
            String rawDestination,
            VerificationPurpose purpose) {
        if (rawToken == null || rawToken.isBlank() || channel == null || purpose == null) throw invalidToken();
        ContactVerificationChallengeEntity challenge = challenges
                .findByVerificationTokenHashForUpdate(hasher.tokenHash(rawToken))
                .orElseThrow(ContactVerificationService::invalidToken);
        String destination = normalizeDestination(channel, rawDestination);
        Instant now = clock.now();
        boolean valid = challenge.getVerifiedAt() != null
                && challenge.getConsumedAt() == null
                && challenge.getVerificationTokenExpiresAt() != null
                && challenge.getVerificationTokenExpiresAt().isAfter(now)
                && channel.name().equals(challenge.getChannel())
                && purpose.name().equals(challenge.getPurpose())
                && constantTimeEquals(challenge.getDestinationHash(), hasher.contactHash(channel, destination));
        if (!valid) throw invalidToken();
        challenge.setConsumedAt(now);
        challenges.saveAndFlush(challenge);
        return new VerifiedContact(channel, destination, purpose, challenge.getVerifiedAt());
    }

    public static String normalizeDestination(VerificationChannel channel, String raw) {
        return channel == VerificationChannel.EMAIL
                ? AccountRules.normalizeEmail(raw)
                : AccountRules.normalizePhoneE164(raw);
    }

    private boolean openForConfirmation(ContactVerificationChallengeEntity challenge, Instant now) {
        return challenge.getVerifiedAt() == null
                && challenge.getConsumedAt() == null
                && challenge.getExpiresAt().isAfter(now)
                && challenge.getFailedAttempts() < properties.getVerification().getMaxFailedAttempts();
    }

    private void registerFailure(ContactVerificationChallengeEntity challenge, Instant now) {
        challenge.setFailedAttempts(challenge.getFailedAttempts() + 1);
        expireIfNecessary(challenge, now);
        challenges.saveAndFlush(challenge);
    }

    private void enforceCooldown(
            VerificationChannel channel,
            String destinationHash,
            VerificationPurpose purpose,
            Instant now) {
        challenges.findFirstByChannelAndDestinationHashAndPurposeOrderByCreatedAtDesc(
                        channel.name(), destinationHash, purpose.name())
                .filter(previous -> previous.getCreatedAt().plusSeconds(
                        properties.getVerification().getResendCooldownSeconds()).isAfter(now))
                .ifPresent(previous -> {
                    throw AppException.rateLimited("VERIFICATION_RESEND_TOO_SOON", "请稍后再获取验证码");
                });
    }

    private Optional<VerificationDeliveryPort> deliveryPort(VerificationChannel channel) {
        if (!properties.getVerification().isEnabled()) return Optional.empty();
        String configured = channel == VerificationChannel.EMAIL
                ? properties.getVerification().getEmailProvider()
                : properties.getVerification().getSmsProvider();
        if (configured == null || configured.isBlank() || "disabled".equalsIgnoreCase(configured)) {
            return Optional.empty();
        }
        return deliveryPorts.stream()
                .filter(port -> configured.equalsIgnoreCase(port.providerCode()))
                .filter(port -> port.supports(channel))
                .filter(VerificationDeliveryPort::available)
                .findFirst();
    }

    private Optional<VerificationDeliveryPort> deliveryPortByCode(String code, VerificationChannel channel) {
        return deliveryPorts.stream()
                .filter(port -> code.equalsIgnoreCase(port.providerCode()))
                .filter(port -> port.supports(channel))
                .filter(VerificationDeliveryPort::available)
                .findFirst();
    }

    private static String maskDestination(VerificationChannel channel, String destination) {
        if (channel == VerificationChannel.EMAIL) {
            int at = destination.indexOf('@');
            String local = destination.substring(0, at);
            String maskedLocal = local.length() <= 2 ? local.charAt(0) + "*" : local.substring(0, 2) + "***";
            return maskedLocal + destination.substring(at);
        }
        String local = destination.substring(3);
        return "+86 " + local.substring(0, 3) + "****" + local.substring(7);
    }

    private void expireIfNecessary(ContactVerificationChallengeEntity challenge, Instant now) {
        if (challenge.getConsumedAt() == null
                && (!challenge.getExpiresAt().isAfter(now)
                || challenge.getFailedAttempts() >= properties.getVerification().getMaxFailedAttempts())) {
            challenge.setConsumedAt(now);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private static AppException unavailable() {
        return AppException.dependency("VERIFICATION_PROVIDER_UNAVAILABLE", "当前验证通道暂时不可用");
    }

    private static AppException invalidCode() {
        return AppException.user("VERIFICATION_CODE_INVALID", "验证码无效或已过期");
    }

    private static AppException invalidToken() {
        return AppException.user("VERIFICATION_TOKEN_INVALID", "验证凭据无效或已使用");
    }

    public record Capabilities(
            boolean emailEnabled,
            boolean smsEnabled,
            int codeLength,
            long codeTtlMinutes,
            long resendCooldownSeconds,
            boolean registrationEnabled,
            String termsVersion,
            String privacyVersion) {
    }

    public record RequestResult(
            String challengeId,
            VerificationChannel channel,
            String destinationMasked,
            Instant expiresAt,
            long resendAfterSeconds) {
    }

    public record ConfirmationResult(
            String verificationToken,
            VerificationChannel channel,
            VerificationPurpose purpose,
            String destinationMasked,
            Instant verificationTokenExpiresAt) {
    }

    public record VerifiedContact(
            VerificationChannel channel,
            String destination,
            VerificationPurpose purpose,
            Instant verifiedAt) {
    }
}
