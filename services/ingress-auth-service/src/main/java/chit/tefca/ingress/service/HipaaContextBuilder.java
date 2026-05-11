package chit.tefca.ingress.service;

import chit.tefca.common.dto.TefcaRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the HIPAA / TEFCA enforcement context that the ingress sends to the
 * policy service. Sources, in priority order:
 *   1. Explicit X-Tefca-* request headers (operator-asserted),
 *   2. Inline {@code _hipaa} block on the normalized payload (machine-set),
 *   3. Heuristic detection on payload keys (defense in depth).
 *
 * Recognized request headers:
 *   X-Tefca-Data-Classes        comma-separated, e.g. "PSYCHOTHERAPY_NOTES,HIV"
 *   X-Tefca-Breakglass          "true"|"false"
 *   X-Tefca-Breakglass-Reason   free text justification (≥ 20 chars required)
 *   X-Tefca-Consent-Part2       opaque consent record id
 *   X-Tefca-Consent-Auth        opaque individual-authorization record id
 *   X-Tefca-Consent-Tpo         opaque TPO consent id
 *   X-Tefca-Partner-Baa         "true"|"false" (operator override; normally
 *                                supplied by directory cache)
 */
@Component
public class HipaaContextBuilder {

    public static final String HEADER_DATA_CLASSES = "X-Tefca-Data-Classes";
    public static final String HEADER_BREAKGLASS = "X-Tefca-Breakglass";
    public static final String HEADER_BREAKGLASS_REASON = "X-Tefca-Breakglass-Reason";
    public static final String HEADER_CONSENT_PART2 = "X-Tefca-Consent-Part2";
    public static final String HEADER_CONSENT_AUTH = "X-Tefca-Consent-Auth";
    public static final String HEADER_CONSENT_TPO = "X-Tefca-Consent-Tpo";
    public static final String HEADER_PARTNER_BAA = "X-Tefca-Partner-Baa";

    private static final List<String> KNOWN_CLASSES = Arrays.asList(
            "PSYCHOTHERAPY_NOTES", "SUBSTANCE_USE_DISORDER", "HIV",
            "MENTAL_HEALTH", "REPRODUCTIVE_HEALTH", "GENETIC", "SSN",
            "MARKETING", "SALE_OF_PHI"
    );

    public Map<String, Object> build(HttpServletRequest http, TefcaRequest normalized) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        List<String> dataClasses = new ArrayList<>();
        Map<String, String> consents = new HashMap<>();
        Map<String, Object> partner = new HashMap<>();

        if (http != null) {
            String hc = http.getHeader(HEADER_DATA_CLASSES);
            if (hc != null && !hc.isBlank()) {
                for (String c : hc.split(",")) {
                    String norm = c.trim().toUpperCase(Locale.ROOT);
                    if (!norm.isEmpty()) dataClasses.add(norm);
                }
            }
            String bg = http.getHeader(HEADER_BREAKGLASS);
            if ("true".equalsIgnoreCase(bg)) {
                ctx.put("breakglass", true);
                String reason = http.getHeader(HEADER_BREAKGLASS_REASON);
                if (reason != null) ctx.put("breakglassJustification", reason);
            }
            String p2 = http.getHeader(HEADER_CONSENT_PART2);
            if (p2 != null && !p2.isBlank()) consents.put("PART_2", p2);
            String auth = http.getHeader(HEADER_CONSENT_AUTH);
            if (auth != null && !auth.isBlank()) consents.put("INDIVIDUAL_AUTH", auth);
            String tpo = http.getHeader(HEADER_CONSENT_TPO);
            if (tpo != null && !tpo.isBlank()) consents.put("TPO", tpo);
            String baa = http.getHeader(HEADER_PARTNER_BAA);
            if (baa != null && !baa.isBlank()) {
                partner.put("baaOnFile", Boolean.parseBoolean(baa));
            }
        }

        // Heuristic detection on payload as defense in depth.
        if (normalized != null && normalized.getPayload() instanceof Map<?, ?> pl) {
            Object purpose = pl.get("purposeOfUse");
            if (purpose != null) {
                String pu = purpose.toString().toUpperCase(Locale.ROOT);
                if (pu.contains("MARKETING")) dataClasses.add("MARKETING");
                if (pu.contains("SALE")) dataClasses.add("SALE_OF_PHI");
            }
            Object hipaa = pl.get("_hipaa");
            if (hipaa instanceof Map<?, ?> h) {
                Object dc = h.get("dataClasses");
                if (dc instanceof List<?> list) {
                    for (Object o : list) {
                        String norm = String.valueOf(o).toUpperCase(Locale.ROOT);
                        if (KNOWN_CLASSES.contains(norm)) dataClasses.add(norm);
                    }
                }
            }
        }

        if (!dataClasses.isEmpty()) {
            // de-duplicate while preserving order
            List<String> dedup = new ArrayList<>(new LinkedHashMap<String, Object>() {{
                for (String s : dataClasses) put(s, null);
            }}.keySet());
            ctx.put("dataClasses", dedup);
        }
        if (!consents.isEmpty()) ctx.put("consentRefs", consents);
        if (!partner.isEmpty()) ctx.put("partnerAttributes", partner);
        return ctx;
    }
}
