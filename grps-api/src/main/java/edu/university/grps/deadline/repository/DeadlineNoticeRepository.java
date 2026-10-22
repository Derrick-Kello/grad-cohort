package edu.university.grps.deadline.repository;

import edu.university.grps.deadline.domain.DeadlineNotice;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadlineNoticeRepository extends JpaRepository<DeadlineNotice, UUID> {

    boolean existsByStudentIdAndNoticeKey(UUID studentId, String noticeKey);
}
