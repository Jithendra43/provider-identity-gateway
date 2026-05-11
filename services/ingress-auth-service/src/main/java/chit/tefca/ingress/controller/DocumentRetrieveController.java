package chit.tefca.ingress.controller;

import chit.tefca.common.dto.TefcaResponse;
import chit.tefca.common.model.RequesterIdentity;
import chit.tefca.common.security.SecurityConstants;
import chit.tefca.ingress.dto.DocumentRetrieveRequest;
import chit.tefca.ingress.filter.JwtAuthenticationFilter;
import chit.tefca.ingress.service.IngressOrchestrator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(SecurityConstants.TEFCA_PREFIX + "/document-retrieve")
@RequiredArgsConstructor
public class DocumentRetrieveController {

    private final IngressOrchestrator orchestrator;
    private final chit.tefca.ingress.service.HipaaContextBuilder hipaaContextBuilder;

    @PostMapping
    public ResponseEntity<TefcaResponse> retrieveDocument(
            @Valid @RequestBody DocumentRetrieveRequest request,
            HttpServletRequest httpRequest) {
        RequesterIdentity identity = (RequesterIdentity) httpRequest
                .getAttribute(JwtAuthenticationFilter.REQUESTER_IDENTITY_ATTR);
        java.util.Map<String,Object> hipaa = hipaaContextBuilder.build(httpRequest, null);
        TefcaResponse response = orchestrator.processDocumentRetrieve(request, identity, hipaa);
        return ResponseEntity.ok(response);
    }
}
