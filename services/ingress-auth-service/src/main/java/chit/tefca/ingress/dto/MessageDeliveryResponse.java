package chit.tefca.ingress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDeliveryResponse {
    private String messageId;
    private String status;
    private String targetOrgId;
}
