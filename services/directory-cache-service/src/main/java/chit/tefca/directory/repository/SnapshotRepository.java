package chit.tefca.directory.repository;

import chit.tefca.directory.model.DirectorySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SnapshotRepository extends JpaRepository<DirectorySnapshot, Long> {

    Optional<DirectorySnapshot> findTopByStatusOrderByCreatedAtDesc(DirectorySnapshot.SyncStatus status);

    Optional<DirectorySnapshot> findTopByOrderByCreatedAtDesc();

    Optional<DirectorySnapshot> findByVersionLabel(String versionLabel);
}
