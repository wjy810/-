package com.jobproof.infrastructure.mail;

public record DevMailMessage(String to, String subject, String purpose, String code) {
}
