package chit.tefca.ingress.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Admin entry-point routing.
 *
 * <p>The admin console is a Next.js static export. The site root
 * (/, /admin, /admin/) serves the public marketing landing page
 * (index.html). The /admin/dashboard/ route and other authenticated
 * pages are guarded by Spring Security and bounce unauthenticated
 * users to the login flow.
 */
@Controller
public class AdminUiController {

    @GetMapping("/")
    public String redirectRoot() {
        return "forward:/admin/index.html";
    }

    @GetMapping("/admin")
    public String redirectAdminBare() {
        return "redirect:/admin/";
    }

    @GetMapping("/admin/")
    public String redirectAdminRoot() {
        return "forward:/admin/index.html";
    }
}
