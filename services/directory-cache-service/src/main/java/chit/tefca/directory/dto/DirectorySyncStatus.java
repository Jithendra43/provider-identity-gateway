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
public class DirectorySyncStatus {

    private Long snapshotId;
    private String versionLabel;
    private String status;
    private int orgCount;
    private int nodeCount;
    private int endpointCount;
    private int capabilityCount;
    private String sourceUrl;
    private String errorMessage;
    private Instant startedAt;
    private Instant completedAt;
}
