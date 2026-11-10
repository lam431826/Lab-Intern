package vn.dxlab.recruitment.submission;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import vn.dxlab.recruitment.common.BaseEntity;
import vn.dxlab.recruitment.user.AppUser;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "recruitment_submissions")
public class RecruitmentSubmission extends BaseEntity {

    @Column(name = "term_code", nullable = false, length = 20)
    private String termCode;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "submission_status", nullable = false, length = 20)
    private SubmissionStatus status = SubmissionStatus.DRAFT;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(length = 200)
    private String recipient;

    @Column(name = "external_reference", length = 300)
    private String externalReference;

    @Column(length = 2000)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by")
    private AppUser submittedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaces_submission_id")
    private RecruitmentSubmission replaces;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<RecruitmentSubmissionItem> items = new ArrayList<>();

    @Version
    @Column(name = "row_version", nullable = false)
    private int version;
}
