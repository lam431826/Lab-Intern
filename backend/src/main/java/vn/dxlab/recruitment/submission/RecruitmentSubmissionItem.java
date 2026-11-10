package vn.dxlab.recruitment.submission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import vn.dxlab.recruitment.common.BaseEntity;
import vn.dxlab.recruitment.track.Track;

@Getter
@Setter
@Entity
@Table(name = "recruitment_submission_items")
public class RecruitmentSubmissionItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private RecruitmentSubmission submission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_id", nullable = false)
    private Track track;

    @Column(name = "track_code_snapshot", nullable = false, length = 30)
    private String trackCodeSnapshot;

    @Column(name = "track_name_snapshot", nullable = false, length = 100)
    private String trackNameSnapshot;

    @Column(name = "position_title", nullable = false, length = 200)
    private String positionTitle;

    @Column(nullable = false)
    private int quota;

    @Column(name = "jd_content", columnDefinition = "NVARCHAR(MAX)")
    private String jdContent;

    @Column(length = 2000)
    private String note;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_mime_type", length = 100)
    private String fileMimeType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_sha256", length = 64)
    private String fileSha256;

    @Version
    @Column(name = "row_version", nullable = false)
    private int version;

    public boolean hasFile() {
        return fileName != null;
    }
}
