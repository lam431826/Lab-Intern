package vn.dxlab.recruitment.scoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import vn.dxlab.recruitment.candidate.Candidate;
import vn.dxlab.recruitment.common.BaseEntity;
import vn.dxlab.recruitment.track.Criterion;
import vn.dxlab.recruitment.user.AppUser;

/** Một ô điểm: một giám khảo chấm một tiêu chí của một ứng viên, thang 1–10. */
@Getter
@Setter
@Entity
@Table(name = "scores")
public class Score extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluator_id", nullable = false)
    private AppUser evaluator;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criterion_id", nullable = false)
    private Criterion criterion;

    @Column(nullable = false)
    private int value;

    @Version
    @Column(name = "row_version", nullable = false)
    private int version;
}
