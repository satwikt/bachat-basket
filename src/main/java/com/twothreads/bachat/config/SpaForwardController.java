package com.twothreads.bachat.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Lets a browser refresh on a React route (/merchant, /customer) load the single-page app. */
@Controller
public class SpaForwardController {

    @GetMapping({"/merchant", "/customer"})
    public String forward() {
        return "forward:/index.html";
    }
}
