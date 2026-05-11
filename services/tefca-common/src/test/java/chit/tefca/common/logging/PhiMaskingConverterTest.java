package chit.tefca.common.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhiMaskingConverterTest {

    @Test
    void masksSsn() {
        assertThat(PhiMaskingConverter.mask("Patient ssn=123-45-6789 admitted"))
                .isEqualTo("Patient ssn=[REDACTED-SSN] admitted");
    }

    @Test
    void masksMrnKeyValue() {
        assertThat(PhiMaskingConverter.mask("looking up MRN: ABC123XYZ for patient"))
                .contains("[REDACTED-MRN]")
                .doesNotContain("ABC123XYZ");
    }

    @Test
    void masksPatientId() {
        assertThat(PhiMaskingConverter.mask("patient_id=PT-9999 found"))
                .contains("[REDACTED-MRN]")
                .doesNotContain("PT-9999");
    }

    @Test
    void masksEmail() {
        assertThat(PhiMaskingConverter.mask("notify alice@hospital.org"))
                .isEqualTo("notify [REDACTED-EMAIL]");
    }

    @Test
    void masksPhone() {
        assertThat(PhiMaskingConverter.mask("call 415-555-0101 today"))
                .isEqualTo("call [REDACTED-PHONE] today");
    }

    @Test
    void masksDob() {
        assertThat(PhiMaskingConverter.mask("dob=1985-03-14 admitted"))
                .contains("[REDACTED-DOB]")
                .doesNotContain("1985-03-14");
        assertThat(PhiMaskingConverter.mask("born 03/14/1985"))
                .contains("[REDACTED-DOB]");
    }

    @Test
    void masksBearerToken() {
        assertThat(PhiMaskingConverter.mask("Authorization: Bearer eyJhbGciOiJIUzI1.payload.sig"))
                .doesNotContain("eyJhbGciOiJIUzI1");
    }

    @Test
    void leavesNonPhiAlone() {
        String safe = "Request orgId=ORG-001 nodeId=NODE-A operation=XCPD";
        assertThat(PhiMaskingConverter.mask(safe)).isEqualTo(safe);
    }

    @Test
    void handlesNullAndEmpty() {
        assertThat(PhiMaskingConverter.mask(null)).isNull();
        assertThat(PhiMaskingConverter.mask("")).isEmpty();
    }
}
