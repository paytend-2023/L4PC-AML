package com.paytend.l4pc.aml.web;

import com.paytend.l4pc.aml.domain.DataSyncRecordEntity;
import com.paytend.l4pc.aml.domain.DataSyncRecordRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/pc/aml/data-sync")
public class DataSyncController {

    private final DataSyncRecordRepository syncRecordRepository;

    public DataSyncController(DataSyncRecordRepository syncRecordRepository) {
        this.syncRecordRepository = syncRecordRepository;
    }

    @GetMapping
    public List<DataSyncRecordEntity> getSyncHistory(
            @RequestParam(value = "provider", defaultValue = "DOW_JONES") String provider) {
        return syncRecordRepository.findByProviderOrderByStartedAtDesc(provider);
    }
}
