package com.jobproof.modules.identity.infra;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeResponse;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.jobproof.infrastructure.config.JobProofProperties;
import com.jobproof.modules.identity.application.VerificationHasher;
import com.jobproof.modules.identity.domain.VerificationChannel;
import com.jobproof.modules.identity.domain.VerificationDeliveryMessage;
import com.jobproof.modules.identity.domain.VerificationDeliveryPort;
import com.jobproof.modules.identity.domain.VerificationDeliveryReceipt;
import com.jobproof.modules.identity.domain.VerificationPurpose;
import com.jobproof.modules.identity.domain.VerificationMode;
import com.jobproof.shared.error.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "jobproof.verification.sms-provider", havingValue = "aliyun-dypns")
public class AliyunSmsVerificationDeliveryAdapter implements VerificationDeliveryPort {

    private final JobProofProperties properties;
    private final VerificationHasher hasher;
    private final Client client;

    @Autowired
    public AliyunSmsVerificationDeliveryAdapter(JobProofProperties properties, VerificationHasher hasher) {
        this(properties, hasher, createClient(properties));
    }

    AliyunSmsVerificationDeliveryAdapter(
            JobProofProperties properties,
            VerificationHasher hasher,
            Client client) {
        this.properties = properties;
        this.hasher = hasher;
        this.client = client;
    }

    @Override
    public String providerCode() {
        return "aliyun-dypns";
    }

    @Override
    public boolean supports(VerificationChannel channel) {
        return channel == VerificationChannel.SMS;
    }

    @Override
    public boolean available() {
        var sms = properties.getVerification().getAliyun();
        return client != null
                && hasher.contactSecretConfigured()
                && !clean(sms.getSignName()).isEmpty()
                && !clean(sms.getTemplateCode()).isEmpty();
    }

    @Override
    public VerificationMode deliveryMode() {
        return VerificationMode.PROVIDER_CHECK;
    }

    @Override
    public VerificationDeliveryReceipt deliver(VerificationDeliveryMessage message) {
        if (!available()) throw unavailable();
        try {
            long seconds = Math.max(60L, message.ttlMinutes() * 60L);
            long minutes = Math.max(1L, message.ttlMinutes());
            String templateParam = "{\"code\":\"##code##\",\"min\":\"" + minutes + "\"}";
            var sms = properties.getVerification().getAliyun();
            SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest()
                    .setPhoneNumber(localPhone(message.destination()))
                    .setSignName(sms.getSignName())
                    .setTemplateCode(sms.getTemplateCode())
                    .setTemplateParam(templateParam)
                    .setCodeLength(6L)
                    .setCodeType(1L)
                    .setValidTime(seconds)
                    .setInterval(properties.getVerification().getResendCooldownSeconds())
                    .setDuplicatePolicy(1L)
                    .setReturnVerifyCode(false);
            SendSmsVerifyCodeResponse response = client.sendSmsVerifyCode(request);
            if (response.getBody() == null
                    || !Boolean.TRUE.equals(response.getBody().getSuccess())
                    || !"OK".equals(response.getBody().getCode())
                    || response.getBody().getModel() == null) {
                throw unavailable();
            }
            return VerificationDeliveryReceipt.providerCheck(response.getBody().getModel().getBizId());
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    @Override
    public boolean verify(String destination, VerificationPurpose purpose, String code, String providerRequestId) {
        if (!available()) throw unavailable();
        try {
            CheckSmsVerifyCodeRequest request = new CheckSmsVerifyCodeRequest()
                    .setPhoneNumber(localPhone(destination))
                    .setVerifyCode(code);
            CheckSmsVerifyCodeResponse response = client.checkSmsVerifyCode(request);
            return response.getBody() != null
                    && Boolean.TRUE.equals(response.getBody().getSuccess())
                    && response.getBody().getModel() != null
                    && "PASS".equals(response.getBody().getModel().getVerifyResult());
        } catch (TeaException exception) {
            if ("isv.ValidateFail".equals(clean(exception.getCode()))) return false;
            throw unavailable();
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private static Client createClient(JobProofProperties properties) {
        try {
            var sms = properties.getVerification().getAliyun();
            if (clean(sms.getAccessKeyId()).isEmpty() || clean(sms.getAccessKeySecret()).isEmpty()) return null;
            Config config = new Config()
                    .setAccessKeyId(sms.getAccessKeyId())
                    .setAccessKeySecret(sms.getAccessKeySecret())
                    .setEndpoint(sms.getEndpoint());
            return new Client(config);
        } catch (Exception exception) {
            return null;
        }
    }

    private static String localPhone(String e164) {
        return e164 != null && e164.startsWith("+86") ? e164.substring(3) : e164;
    }

    private static AppException unavailable() {
        return AppException.dependency("VERIFICATION_PROVIDER_UNAVAILABLE", "短信验证服务暂时不可用，请稍后重试");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
