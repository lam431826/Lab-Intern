package vn.dxlab.recruitment.track;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import vn.dxlab.recruitment.common.BaseEntity;
import vn.dxlab.recruitment.user.AppUser;

/** Ngạch tuyển dụng: AI, Graphic Design, SE, Game Developer, IA, Game Design. */
@Getter
@Setter
@Entity
@Table(name = "tracks")
public class Track extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    /** Chỉ tiêu trúng tuyển. */
    @Column(nullable = false)
    private int quota;

    @Column(name = "slot_min", nullable = false)
    private int slotMin;

    @Column(name = "slot_max", nullable = false)
    private int slotMax;

    /** Bắt buộc có Portfolio (Graphic Design, Game Design); thiếu thì gắn cờ lọc nhanh. */
    @Column(name = "requires_portfolio", nullable = false)
    private boolean requiresPortfolio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_user_id")
    private AppUser lead;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
