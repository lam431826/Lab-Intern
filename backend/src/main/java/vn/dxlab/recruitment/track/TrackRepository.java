package vn.dxlab.recruitment.track;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrackRepository extends JpaRepository<Track, Long> {

    Optional<Track> findByCode(String code);

    List<Track> findAllByOrderBySortOrderAsc();

    List<Track> findByLeadId(Long leadUserId);
}
