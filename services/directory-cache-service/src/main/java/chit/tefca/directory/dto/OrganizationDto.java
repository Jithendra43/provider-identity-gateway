package chit.tefca.directory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationDto {

    private String orgId;
    private String name;
    private String oid;
    private String orgType;
    private boolean active;
    private String homeCommunityId;
    private Instant lastSyncedAt;
}
