package vn.dxlab.recruitment.result;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import vn.dxlab.recruitment.candidate.Candidate;
import vn.dxlab.recruitment.common.BaseEntity;
import vn.dxlab.recruitment.track.Track;
import vn.dxlab.recruitment.user.AppUser;

import java.time.Instant;

/**
 * Kết luận của hội đồng. Điểm tổng không lưu ở đây mà luôn tính lại từ bảng scores.
 * Sau khi Giảng viên phê duyệt ({@code approved = true}) thì không sửa được nữa trừ khi Giảng viên mở lại.
 */
@Getter
@Setter
@Entity
@Table(name = "decisions")
public class Decision extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, unique = true)
    private Candidate candidate;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_status", nullable = false, length = 20)
    private FinalStatus finalStatus = FinalStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_track_id")
    private Track transferTrack;

    @Column(length = 1000)
    private String note;

    @Column(nullable = false)
    private boolean approved;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private AppUser approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_status", nullable = false, length = 20)
    private EmailStatus emailStatus = EmailStatus.NOT_SENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "offer_status", nullable = false, length = 20)
    private OfferStatus offerStatus = OfferStatus.NONE;

    @Version
    @Column(name = "row_version", nullable = false)
    private int version;
}
