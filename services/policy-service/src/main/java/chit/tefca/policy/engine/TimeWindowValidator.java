package chit.tefca.policy.engine;

import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyExplanation;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Validates that the request falls within allowed time windows.
 * Certain exchange purposes may be restricted to business hours
 * or have maintenance windows where requests are denied.
 */
@Component
public class TimeWindowValidator {

    private static final int BUSINESS_START_HOUR = 6;
    private static final int BUSINESS_END_HOUR = 23;
    private static final ZoneId EASTERN = ZoneId.of("America/New_York");

    public PolicyExplanation validate(PolicyEvaluationRequest request) {
        ZonedDateTime now = ZonedDateTime.now(EASTERN);

        // Emergency/Treatment requests are always allowed
        if ("TREATMENT".equals(request.getExchangePurpose().name())) {
            return pass("Treatment requests are not subject to time window restrictions");
        }

        // Check maintenance window (Sunday 2-6 AM ET)
        if (now.getDayOfWeek() == DayOfWeek.SUNDAY
                && now.getHour() >= 2 && now.getHour() < BUSINESS_START_HOUR) {
            return PolicyExplanation.builder()
                    .ruleId("POLICY-008")
                    .ruleName("Time Window Validation")
                    .category("TIME_WINDOW")
                    .result("FAIL")
                    .reason("Request denied during scheduled maintenance window (Sunday 2-6 AM ET)")
                    .build();
        }

        // All other times are permitted
        return pass("Request is within allowed time window (current: " + now.toLocalTime() + " ET)");
    }

    private PolicyExplanation pass(String reason) {
        return PolicyExplanation.builder()
                .ruleId("POLICY-008")
                .ruleName("Time Window Validation")
                .category("TIME_WINDOW")
                .result("PASS")
                .reason(reason)
                .build();
    }
}
