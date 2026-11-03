package vn.dxlab.recruitment.candidate;

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
import vn.dxlab.recruitment.common.BaseEntity;
import vn.dxlab.recruitment.track.Track;
import vn.dxlab.recruitment.user.AppUser;

import java.time.Instant;

/** Hồ sơ ứng viên. Mã SV là khóa tự nhiên dùng khi nhập lại file Excel. */
@Getter
@Setter
@Entity
@Table(name = "candidates")
public class Candidate extends BaseEntity {

    @Column(name = "student_code", nullable = false, length = 20)
    private String studentCode;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(length = 254)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String major;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_id", nullable = false)
    private Track track;

    @Column(name = "cv_link", length = 500)
    private String cvLink;

    @Column(name = "portfolio_link", length = 500)
    private String portfolioLink;

    @Enumerated(EnumType.STRING)
    @Column(name = "screening_status", nullable = false, length = 20)
    private ScreeningStatus screeningStatus = ScreeningStatus.PENDING;

    @Column(name = "screening_reason", length = 1000)
    private String screeningReason;

    @Column(name = "cv_personal_info")
    private Integer cvPersonalInfo;

    @Column(name = "cv_experience")
    private Integer cvExperience;

    @Column(name = "cv_skills")
    private Integer cvSkills;

    @Column(name = "cv_projects")
    private Integer cvProjects;

    @Column(name = "cv_certificates")
    private Integer cvCertificates;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screened_by")
    private AppUser screenedBy;

    @Column(name = "screened_at")
    private Instant screenedAt;

    /** Mốc tính SLA duyệt CV. */
    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt = Instant.now();

    @Version
    @Column(name = "row_version", nullable = false)
    private int version;
}
