package vn.dxlab.recruitment.scoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import vn.dxlab.recruitment.candidate.Candidate;
import vn.dxlab.recruitment.common.BaseEntity;
import vn.dxlab.recruitment.user.AppUser;

import java.time.Instant;

/** Nhận xét và đề xuất của một giám khảo cho một ứng viên; thay file NHẬN XÉT ỨNG VIÊN.xlsx. */
@Getter
@Setter
@Entity
@Table(name = "evaluator_notes")
public class EvaluatorNote extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluator_id", nullable = false)
    private AppUser evaluator;

    @Column(length = 2000)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Recommendation recommendation;

    /** NULL = phiếu nháp. Giám khảo chỉ xem được điểm của người khác sau khi đã nộp. */
    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private int version;
}
