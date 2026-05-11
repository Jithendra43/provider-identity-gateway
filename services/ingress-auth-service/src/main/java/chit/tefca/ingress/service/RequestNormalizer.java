package chit.tefca.ingress.service;

import chit.tefca.common.dto.TefcaRequest;
import chit.tefca.common.enums.TefcaOperation;
import chit.tefca.common.correlation.CorrelationIdHolder;
import chit.tefca.ingress.dto.PatientDiscoveryRequest;
import chit.tefca.ingress.dto.DocumentQueryRequest;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Normalizes inbound operation-specific requests into the canonical TefcaRequest envelope.
 * The original request fields are preserved on the {@code payload} map so they can be
 * forwarded verbatim to the downstream partner endpoint.
 */
@Service
public class RequestNormalizer {

    public TefcaRequest normalizePatientDiscovery(PatientDiscoveryRequest request, String orgId, String nodeId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (request.getExchangePurpose() != null) payload.put("exchangePurpose", request.getExchangePurpose().name());
        putIfNotNull(payload, "patientFirstName",  request.getPatientFirstName());
        putIfNotNull(payload, "patientLastName",   request.getPatientLastName());
        putIfNotNull(payload, "patientDateOfBirth",request.getPatientDateOfBirth());
        putIfNotNull(payload, "patientGender",     request.getPatientGender());
        putIfNotNull(payload, "patientId",         request.getPatientId());
        putIfNotNull(payload, "patientIdSystem",   request.getPatientIdSystem());
        putIfNotNull(payload, "targetOrgId",       request.getTargetOrgId());

        return TefcaRequest.builder()
                .correlationId(CorrelationIdHolder.get())
                .operation(TefcaOperation.PATIENT_DISCOVERY)
                .exchangePurpose(request.getExchangePurpose())
                .requesterOrgId(orgId)
                .requesterNodeId(nodeId)
                .targetOrgId(request.getTargetOrgId())
                .patientId(request.getPatientId())
                .patientIdSystem(request.getPatientIdSystem())
                .payload(payload)
                .build();
    }

    public TefcaRequest normalizeDocumentQuery(DocumentQueryRequest request, String orgId, String nodeId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (request.getExchangePurpose() != null) payload.put("exchangePurpose", request.getExchangePurpose().name());
        putIfNotNull(payload, "patientId",       request.getPatientId());
        putIfNotNull(payload, "patientIdSystem", request.getPatientIdSystem());
        putIfNotNull(payload, "targetOrgId",     request.getTargetOrgId());
        putIfNotNull(payload, "documentType",    request.getDocumentType());

        return TefcaRequest.builder()
                .correlationId(CorrelationIdHolder.get())
                .operation(TefcaOperation.DOCUMENT_QUERY)
                .exchangePurpose(request.getExchangePurpose())
                .requesterOrgId(orgId)
                .requesterNodeId(nodeId)
                .targetOrgId(request.getTargetOrgId())
                .patientId(request.getPatientId())
                .patientIdSystem(request.getPatientIdSystem())
                .payload(payload)
                .build();
    }

    private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) map.put(key, value);
    }
}
