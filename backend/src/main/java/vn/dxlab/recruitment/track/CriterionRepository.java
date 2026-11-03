package vn.dxlab.recruitment.track;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CriterionRepository extends JpaRepository<Criterion, Long> {

    List<Criterion> findByGroupAndActiveTrueOrderBySortOrderAsc(CriterionGroup group);

    List<Criterion> findByTrackIdAndActiveTrueOrderBySortOrderAsc(Long trackId);

    /** Tiêu chí dùng để chấm ứng viên của một ngạch: tiêu chí chung trước, chuyên môn sau. */
    @Query("""
            select c from Criterion c left join c.track t
            where c.active = true
              and (c.group = vn.dxlab.recruitment.track.CriterionGroup.GENERAL or t.id = :trackId)
            order by c.group, c.sortOrder
            """)
    List<Criterion> findApplicableToTrack(Long trackId);
}
