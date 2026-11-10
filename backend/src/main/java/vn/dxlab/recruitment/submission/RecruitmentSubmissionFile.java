package vn.dxlab.recruitment.submission;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import vn.dxlab.recruitment.common.BaseEntity;

@Getter
@Setter
@Entity
@Table(name = "recruitment_submission_files")
public class RecruitmentSubmissionFile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false, unique = true)
    private RecruitmentSubmissionItem item;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "file_data", nullable = false, columnDefinition = "VARBINARY(MAX)")
    private byte[] data;
}
