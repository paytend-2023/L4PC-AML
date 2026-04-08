package com.paytend.l4pc.aml.service;

import com.paytend.l4pc.aml.api.ScreeningRequest;
import com.paytend.l4pc.aml.api.ScreeningResponse;
import com.paytend.l4pc.aml.domain.ScreeningCustomerEntity;
import com.paytend.l4pc.aml.domain.ScreeningCustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Ongoing screening task per AML spec §5.2:
 * - High risk customers: screened daily
 * - Medium risk: weekly
 * - Low risk: monthly
 *
 * Runs on a configurable schedule. Picks up customers whose next_screening_at has passed.
 */
@Component
@ConditionalOnProperty(name = "aml.ongoing-screening.enabled", havingValue = "true", matchIfMissing = false)
public class OngoingScreeningTask {

    private static final Logger log = LoggerFactory.getLogger(OngoingScreeningTask.class);

    private final ScreeningCustomerRepository customerRepository;
    private final ScreeningService screeningService;

    public OngoingScreeningTask(ScreeningCustomerRepository customerRepository,
                                ScreeningService screeningService) {
        this.customerRepository = customerRepository;
        this.screeningService = screeningService;
    }

    @Scheduled(cron = "${aml.ongoing-screening.cron:0 0 2 * * ?}")
    public void executeOngoingScreening() {
        Instant now = Instant.now();
        List<ScreeningCustomerEntity> due = customerRepository.findDueForScreening(now);

        log.info("Ongoing screening: {} customers due for re-screening", due.size());

        int screened = 0;
        int alerts = 0;

        for (ScreeningCustomerEntity customer : due) {
            try {
                ScreeningRequest request = new ScreeningRequest(
                        customer.getCustomerId(),
                        customer.getCustomerName(),
                        customer.getDateOfBirth(),
                        customer.getNationality(),
                        customer.getIdNumber(),
                        null,
                        ScreeningService.TYPE_ONGOING,
                        null,
                        "SYSTEM",
                        "L4PC-AML-ONGOING");

                ScreeningResponse response = screeningService.screen(request);
                screened++;

                if (ScreeningService.STATUS_ALERT.equals(response.status())) {
                    alerts++;
                }

                // Update customer risk level based on latest screening
                customer.setRiskLevel(response.risk_level());
                customer.setScreeningFrequency(frequencyForRiskLevel(response.risk_level()));
                customer.setLastScreenedAt(now);
                customer.setNextScreenedAt(calculateNextScreening(response.risk_level(), now));
                customer.setUpdatedAt(now);
                customerRepository.save(customer);

            } catch (Exception e) {
                log.error("Ongoing screening failed for customer {}: {}",
                        customer.getCustomerId(), e.getMessage(), e);
            }
        }

        log.info("Ongoing screening completed: {} screened, {} alerts generated", screened, alerts);
    }

    private Instant calculateNextScreening(String riskLevel, Instant from) {
        return switch (riskLevel) {
            case "HIGH" -> from.plus(1, ChronoUnit.DAYS);
            case "MEDIUM" -> from.plus(7, ChronoUnit.DAYS);
            default -> from.plus(30, ChronoUnit.DAYS);
        };
    }

    private String frequencyForRiskLevel(String riskLevel) {
        return switch (riskLevel) {
            case "HIGH" -> "DAILY";
            case "MEDIUM" -> "WEEKLY";
            default -> "MONTHLY";
        };
    }
}
