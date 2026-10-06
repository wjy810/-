package com.jobproof.modules.identity.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeResponse;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeResponseBody;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeResponseBody.CheckSmsVerifyCodeResponseBodyModel;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponseBody;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponseBody.SendSmsVerifyCodeResponseBodyModel;
import com.aliyun.tea.TeaException;
import com.jobproof.infrastructure.config.JobProofProperties;
import com.jobproof.modules.identity.application.VerificationHasher;
import com.jobproof.modules.identity.domain.VerificationChannel;
import com.jobproof.modules.identity.domain.VerificationDeliveryMessage;
import com.jobproof.modules.identity.domain.VerificationMode;
import com.jobproof.modules.identity.domain.VerificationPurpose;
import com.jobproof.shared.error.AppException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class VerificationDeliveryAdaptersTest {

    @Test
    void qqSmtpBuildsHtmlMailAndMapsAuthenticationOrTimeoutFailures() throws Exception {
        JobProofProperties properties = configuredProperties();
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        QqSmtpVerificationDeliveryAdapter adapter = new QqSmtpVerificationDeliveryAdapter(
                sender, properties, new VerificationHasher(properties), "sender@qq.com", "rotated-test-code");

        assertTrue(adapter.available());
        assertEquals(VerificationMode.LOCAL_CODE, adapter.deliver(emailMessage()).mode());
        verify(sender).send(message);
        assertEquals("【JobProof AI】身份验证码", message.getSubject());
        assertTrue(String.valueOf(message.getContent()).contains("482731"));

        doThrow(new MailAuthenticationException("rejected"))
                .when(sender).send(any(MimeMessage.class));
        assertProviderUnavailable(() -> adapter.deliver(emailMessage()));

        doThrow(new MailSendException("timeout"))
                .when(sender).send(any(MimeMessage.class));
        assertProviderUnavailable(() -> adapter.deliver(emailMessage()));
    }

    @Test
    void aliyunRequiresSuccessfulSendAndPassVerification() throws Exception {
        JobProofProperties properties = configuredProperties();
        Client client = mock(Client.class);
        AliyunSmsVerificationDeliveryAdapter adapter = new AliyunSmsVerificationDeliveryAdapter(
                properties, new VerificationHasher(properties), client);
        when(client.sendSmsVerifyCode(any(SendSmsVerifyCodeRequest.class)))
                .thenReturn(sendResponse(true, "OK", "biz-1"));

        var receipt = adapter.deliver(smsMessage());

        assertEquals(VerificationMode.PROVIDER_CHECK, receipt.mode());
        assertEquals("biz-1", receipt.providerRequestId());
        ArgumentCaptor<SendSmsVerifyCodeRequest> request = ArgumentCaptor.forClass(SendSmsVerifyCodeRequest.class);
        verify(client).sendSmsVerifyCode(request.capture());
        assertEquals("13800138000", request.getValue().getPhoneNumber());
        assertFalse(Boolean.TRUE.equals(request.getValue().getReturnVerifyCode()));

        when(client.checkSmsVerifyCode(any(CheckSmsVerifyCodeRequest.class)))
                .thenReturn(checkResponse(true, "PASS"));
        assertTrue(adapter.verify("+8613800138000", VerificationPurpose.REGISTER, "482731", "biz-1"));

        when(client.checkSmsVerifyCode(any(CheckSmsVerifyCodeRequest.class)))
                .thenReturn(checkResponse(true, "UNKNOWN"));
        assertFalse(adapter.verify("+8613800138000", VerificationPurpose.REGISTER, "482731", "biz-1"));

        when(client.checkSmsVerifyCode(any(CheckSmsVerifyCodeRequest.class)))
                .thenReturn(checkResponse(false, "PASS"));
        assertFalse(adapter.verify("+8613800138000", VerificationPurpose.REGISTER, "482731", "biz-1"));

        TeaException invalidCode = new TeaException();
        invalidCode.setCode("isv.ValidateFail");
        when(client.checkSmsVerifyCode(any(CheckSmsVerifyCodeRequest.class))).thenThrow(invalidCode);
        assertFalse(adapter.verify("+8613800138000", VerificationPurpose.REGISTER, "000000", "biz-1"));
    }

    @Test
    void aliyunMapsProviderFailuresToOneUnavailableError() throws Exception {
        JobProofProperties properties = configuredProperties();
        Client client = mock(Client.class);
        AliyunSmsVerificationDeliveryAdapter adapter = new AliyunSmsVerificationDeliveryAdapter(
                properties, new VerificationHasher(properties), client);

        when(client.sendSmsVerifyCode(any(SendSmsVerifyCodeRequest.class)))
                .thenReturn(sendResponse(false, "InternalError", null));
        assertProviderUnavailable(() -> adapter.deliver(smsMessage()));

        when(client.checkSmsVerifyCode(any(CheckSmsVerifyCodeRequest.class)))
                .thenThrow(new RuntimeException("timeout"));
        assertProviderUnavailable(() -> adapter.verify(
                "+8613800138000", VerificationPurpose.LOGIN_RECOVERY, "482731", "biz-1"));
    }

    private static JobProofProperties configuredProperties() {
        JobProofProperties properties = new JobProofProperties();
        properties.getVerification().setContactHmacSecret("contact-hmac-test-secret");
        properties.getVerification().setCodeHmacSecret("code-hmac-test-secret");
        properties.getVerification().getQqSmtp().setFrom("sender@qq.com");
        properties.getVerification().getQqSmtp().setFromName("JobProof AI");
        properties.getVerification().getAliyun().setSignName("JobProofTest");
        properties.getVerification().getAliyun().setTemplateCode("SMS_TEST");
        return properties;
    }

    private static VerificationDeliveryMessage emailMessage() {
        return new VerificationDeliveryMessage(
                VerificationChannel.EMAIL,
                "person@example.com",
                VerificationPurpose.REGISTER,
                "482731",
                10);
    }

    private static VerificationDeliveryMessage smsMessage() {
        return new VerificationDeliveryMessage(
                VerificationChannel.SMS,
                "+8613800138000",
                VerificationPurpose.REGISTER,
                null,
                10);
    }

    private static SendSmsVerifyCodeResponse sendResponse(boolean success, String code, String bizId) {
        SendSmsVerifyCodeResponseBody body = new SendSmsVerifyCodeResponseBody()
                .setSuccess(success)
                .setCode(code);
        if (bizId != null) {
            body.setModel(new SendSmsVerifyCodeResponseBodyModel().setBizId(bizId));
        }
        return new SendSmsVerifyCodeResponse().setBody(body);
    }

    private static CheckSmsVerifyCodeResponse checkResponse(boolean success, String result) {
        return new CheckSmsVerifyCodeResponse().setBody(new CheckSmsVerifyCodeResponseBody()
                .setSuccess(success)
                .setModel(new CheckSmsVerifyCodeResponseBodyModel().setVerifyResult(result)));
    }

    private static void assertProviderUnavailable(ThrowingAction action) {
        AppException exception = assertThrows(AppException.class, action::run);
        assertEquals("VERIFICATION_PROVIDER_UNAVAILABLE", exception.reason());
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
