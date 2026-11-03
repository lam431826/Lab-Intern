package vn.dxlab.recruitment.track;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import vn.dxlab.recruitment.common.BaseEntity;

/**
 * Tiêu chí chấm điểm, thang 1–10. Thang hướng dẫn: Trung bình (1–6), Khá – Đạt (7–8), Xuất sắc (9–10).
 * Không xóa cứng tiêu chí đã có điểm; đặt {@code active = false}.
 */
@Getter
@Setter
@Entity
@Table(name = "criteria")
public class Criterion extends BaseEntity {

    /** NULL với tiêu chí chung. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id")
    private Track track;

    @Enumerated(EnumType.STRING)
    @Column(name = "crit_group", nullable = false, length = 20)
    private CriterionGroup group;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "rubric_low", length = 1000)
    private String rubricLow;

    @Column(name = "rubric_mid", length = 1000)
    private String rubricMid;

    @Column(name = "rubric_high", length = 1000)
    private String rubricHigh;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active = true;
}
