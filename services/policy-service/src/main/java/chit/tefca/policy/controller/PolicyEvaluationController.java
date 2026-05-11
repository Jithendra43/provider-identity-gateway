package chit.tefca.policy.controller;

import chit.tefca.common.security.SecurityConstants;
import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyEvaluationResponse;
import chit.tefca.policy.service.PolicyEvaluationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(SecurityConstants.API_V1_PREFIX + "/policy")
@RequiredArgsConstructor
public class PolicyEvaluationController {

    private final PolicyEvaluationService evaluationService;

    @PostMapping("/evaluate")
    public ResponseEntity<PolicyEvaluationResponse> evaluate(@Valid @RequestBody PolicyEvaluationRequest request) {
        PolicyEvaluationResponse response = evaluationService.evaluate(request);
        return ResponseEntity.ok(response);
    }
}
