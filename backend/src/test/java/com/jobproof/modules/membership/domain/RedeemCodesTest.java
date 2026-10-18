package com.jobproof.modules.membership.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RedeemCodesTest {

    @Test
    void codesAreGroupedAndAvoidLookAlikeCharacters() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String code = RedeemCodes.generate();
            assertThat(code).matches("JP-[2-9A-HJKMNP-Z]{4}(-[2-9A-HJKMNP-Z]{4}){3}");
            assertThat(code).doesNotContain("0", "O", "1", "I", "L");
            seen.add(code);
        }
        assertThat(seen).hasSize(200);
    }

    @Test
    void typingIsForgivingButOnlyRealCodesPass() {
        assertThat(RedeemCodes.normalize("jp-abcd-efgh-jkmn-pqrs")).contains("ABCDEFGHJKMNPQRS");
        assertThat(RedeemCodes.normalize(" ABCD EFGH JKMN PQRS ")).contains("ABCDEFGHJKMNPQRS");
        assertThat(RedeemCodes.normalize("ABCDEFGHJKMNPQRS")).contains("ABCDEFGHJKMNPQRS");
        assertThat(RedeemCodes.normalize("JP-ABCD-EFGH-JKMN-PQR0")).isEmpty();
        assertThat(RedeemCodes.normalize("JP-ABCD-EFGH-JKMN")).isEmpty();
        assertThat(RedeemCodes.normalize(null)).isEmpty();
        assertThat(RedeemCodes.hint("ABCDEFGHJKMNPQRS")).isEqualTo("PQRS");
        assertThat(RedeemCodes.format("ABCDEFGHJKMNPQRS")).isEqualTo("JP-ABCD-EFGH-JKMN-PQRS");
    }
}
