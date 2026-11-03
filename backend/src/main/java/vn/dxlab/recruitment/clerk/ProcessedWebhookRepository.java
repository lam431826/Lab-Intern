package vn.dxlab.recruitment.clerk;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedWebhookRepository extends JpaRepository<ProcessedWebhook, String> {
}
