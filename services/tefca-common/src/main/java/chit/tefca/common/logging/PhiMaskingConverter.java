package chit.tefca.common.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logback conversion rule that masks Protected Health Information patterns in
 * log messages before they are written. Wired by {@code logback-spring.xml}:
 * <pre>
 *   &lt;conversionRule conversionWord="phi" converterClass="chit.tefca.common.logging.PhiMaskingConverter"/&gt;
 *   &lt;pattern&gt;... %phi(%msg) ...&lt;/pattern&gt;
 * </pre>
 *
 * <p>Patterns masked (HIPAA §164.514(b) safe-harbor identifiers):
 * <ul>
 *   <li>SSN — {@code 123-45-6789}</li>
 *   <li>MRN / patient_id key=value pairs</li>
 *   <li>Email addresses</li>
 *   <li>US phone numbers (10 digits, common formats)</li>
 *   <li>Dates of birth (yyyy-mm-dd / mm/dd/yyyy)</li>
 *   <li>Bearer tokens / Authorization headers</li>
 * </ul>
 *
 * <p>This is an enforcement-by-default control. Developers cannot opt out at
 * call sites; every log line going through an appender that uses {@code %phi}
 * is sanitised. Combine with {@code PhiSafeLogger} for argument-aware masking.
 */
public class PhiMaskingConverter extends ClassicConverter {

    private static final Pattern SSN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern MRN_KV = Pattern.compile("(?i)\\b(mrn|patient[_-]?id|medical[_-]?record[_-]?number)\\s*[=:]\\s*[\\w\\-./]+");
    private static final Pattern EMAIL = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("\\b(?:\\+?1[-. ]?)?\\(?\\d{3}\\)?[-. ]?\\d{3}[-. ]?\\d{4}\\b");
    private static final Pattern DOB_ISO = Pattern.compile("\\b(19|20)\\d{2}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])\\b");
    private static final Pattern DOB_US = Pattern.compile("\\b(0[1-9]|1[0-2])/(0[1-9]|[12]\\d|3[01])/(19|20)\\d{2}\\b");
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._\\-]+");

    @Override
    public String convert(ILoggingEvent event) {
        return mask(event.getFormattedMessage());
    }

    public static String mask(String input) {
        if (input == null || input.isEmpty()) return input;
        String out = input;
        out = SSN.matcher(out).replaceAll("[REDACTED-SSN]");
        out = MRN_KV.matcher(out).replaceAll(m -> Matcher.quoteReplacement(extractKey(m.group()) + "=[REDACTED-MRN]"));
        out = EMAIL.matcher(out).replaceAll("[REDACTED-EMAIL]");
        out = PHONE.matcher(out).replaceAll("[REDACTED-PHONE]");
        out = DOB_ISO.matcher(out).replaceAll("[REDACTED-DOB]");
        out = DOB_US.matcher(out).replaceAll("[REDACTED-DOB]");
        out = BEARER.matcher(out).replaceAll("Bearer [REDACTED]");
        return out;
    }

    private static String extractKey(String kv) {
        int eq = indexOfAny(kv, "=:");
        return eq > 0 ? kv.substring(0, eq).trim() : kv;
    }

    private static int indexOfAny(String s, String chars) {
        for (int i = 0; i < s.length(); i++) {
            if (chars.indexOf(s.charAt(i)) >= 0) return i;
        }
        return -1;
    }
}
