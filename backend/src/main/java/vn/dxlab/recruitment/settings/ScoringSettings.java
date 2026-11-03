package vn.dxlab.recruitment.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

/** Tham số tính điểm; bảng chỉ có đúng một dòng {@code id = 1}. */
@Getter
@Setter
@Entity
@Table(name = "scoring_settings")
@EntityListeners(AuditingEntityListener.class)
public class ScoringSettings {

    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    @Column(name = "weight_general", nullable = false, precision = 4, scale = 2)
    private BigDecimal weightGeneral;

    @Column(name = "weight_professional", nullable = false, precision = 4, scale = 2)
    private BigDecimal weightProfessional;

    /** Điểm sàn: Tổng ≥ ngưỡng này thì gợi ý Đạt. */
    @Column(name = "pass_threshold", nullable = false, precision = 4, scale = 2)
    private BigDecimal passThreshold;

    /** Thời hạn Lead duyệt CV kể từ khi hồ sơ được giao. */
    @Column(name = "sla_hours", nullable = false)
    private int slaHours;

    @Column(name = "slot_minutes", nullable = false)
    private int slotMinutes;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;
}
