package com.paytend.l4pc.aml.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PortalForwardController {

    @GetMapping(value = {"/", "/login", "/aml", "/aml/**"})
    public String forwardToPortal() {
        return "forward:/index.html";
    }
}
