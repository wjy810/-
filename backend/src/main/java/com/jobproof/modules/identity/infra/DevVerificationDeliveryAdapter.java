package com.jobproof.modules.identity.infra;

import com.jobproof.infrastructure.mail.DevMailMessage;
import com.jobproof.infrastructure.mail.DevMailbox;
import com.jobproof.modules.identity.domain.VerificationChannel;
import com.jobproof.modules.identity.domain.VerificationDeliveryMessage;
import com.jobproof.modules.identity.domain.VerificationDeliveryPort;
import com.jobproof.modules.identity.domain.VerificationDeliveryReceipt;
import org.springframework.stereotype.Component;

@Component
public class DevVerificationDeliveryAdapter implements VerificationDeliveryPort {

    private final DevMailbox mailbox;

    public DevVerificationDeliveryAdapter(DevMailbox mailbox) {
        this.mailbox = mailbox;
    }

    @Override
    public String providerCode() {
        return "dev";
    }

    @Override
    public boolean supports(VerificationChannel channel) {
        return channel == VerificationChannel.EMAIL || channel == VerificationChannel.SMS;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public VerificationDeliveryReceipt deliver(VerificationDeliveryMessage message) {
        String subject = message.channel() == VerificationChannel.EMAIL
                ? "JobProof AI 验证码"
                : "JobProof AI 短信验证码（本地模拟）";
        mailbox.deliver(new DevMailMessage(
                message.destination(),
                subject,
                "CONTACT_" + message.purpose().name(),
                message.code()));
        return VerificationDeliveryReceipt.local();
    }
}
