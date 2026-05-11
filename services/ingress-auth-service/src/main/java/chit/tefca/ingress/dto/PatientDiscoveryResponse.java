package chit.tefca.ingress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientDiscoveryResponse {

    private List<PatientMatch> matches;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatientMatch {
        private String patientId;
        private String patientIdSystem;
        private String orgId;
        private String nodeId;
        private double matchScore;
    }
}
