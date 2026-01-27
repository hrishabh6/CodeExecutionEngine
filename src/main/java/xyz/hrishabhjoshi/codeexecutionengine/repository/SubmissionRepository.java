package xyz.hrishabhjoshi.codeexecutionengine.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.hrishabhjoshi.codeexecutionengine.model.Submission;
import xyz.hrishabhjoshi.codeexecutionengine.model.SubmissionStatus;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Submission entity operations.
 */
@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /**
     * Find by unique submission ID (UUID).
     */
    Optional<Submission> findBySubmissionId(String submissionId);

    /**
     * Find recent submissions by user.
     */
    @Query("""
        SELECT s FROM Submission s 
        WHERE s.userId = :userId 
        ORDER BY s.queuedAt DESC
        """)
    List<Submission> findRecentByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * Find submissions by user and status.
     */
    List<Submission> findByUserIdAndStatus(Long userId, SubmissionStatus status);

    /**
     * Find submissions by question and status.
     */
    List<Submission> findByQuestionIdAndStatus(Long questionId, SubmissionStatus status);

    /**
     * Count pending submissions (for queue monitoring).
     */
    @Query("""
        SELECT COUNT(s) FROM Submission s 
        WHERE s.status IN ('QUEUED', 'COMPILING', 'RUNNING')
        """)
    Long countPendingSubmissions();

    /**
     * Get average runtime for a question (accepted submissions only).
     */
    @Query("""
        SELECT AVG(s.runtimeMs) FROM Submission s 
        WHERE s.questionId = :questionId 
        AND s.verdict = 'ACCEPTED'
        """)
    Double getAverageRuntimeByQuestionId(@Param("questionId") Long questionId);

    /**
     * Count accepted submissions for a question.
     */
    @Query("""
        SELECT COUNT(s) FROM Submission s 
        WHERE s.questionId = :questionId 
        AND s.verdict = 'ACCEPTED'
        """)
    Long countAcceptedByQuestionId(@Param("questionId") Long questionId);

    /**
     * Count total submissions for a question.
     */
    Long countByQuestionId(Long questionId);
}
