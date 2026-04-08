package com.paytend.l4pc.aml.web;

import com.paytend.l4pc.aml.api.ScreeningRequest;
import com.paytend.l4pc.aml.api.ScreeningResponse;
import com.paytend.l4pc.aml.service.ScreeningService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pc/aml/screening")
public class ScreeningController {

    private final ScreeningService screeningService;

    public ScreeningController(ScreeningService screeningService) {
        this.screeningService = screeningService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScreeningResponse screen(@Valid @RequestBody ScreeningRequest request) {
        return screeningService.screen(request);
    }

    @GetMapping("/{screeningId}")
    public ScreeningResponse getScreening(@PathVariable String screeningId) {
        return screeningService.getScreening(screeningId);
    }

    @GetMapping
    public List<ScreeningResponse> getScreeningsByCustomer(@RequestParam("customer_id") String customerId) {
        return screeningService.getScreeningsByCustomer(customerId);
    }
}
