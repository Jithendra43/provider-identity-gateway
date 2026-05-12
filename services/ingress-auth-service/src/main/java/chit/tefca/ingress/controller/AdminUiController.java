package chit.tefca.ingress.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

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

    /**
     * The Next.js static export emits each page as a directory with an
     * {@code index.html} (e.g. {@code dashboard/index.html}). Browsers that
     * land on the no-trailing-slash variant ({@code /admin/dashboard}) hit
     * Spring's resource handler which has no mapping → falls through to
     * Whitelabel error 500. Redirect to the canonical trailing-slash form
     * so the static asset resolves.
     *
     * <p>The {@code @PathVariable} captures any single segment that does NOT
     * include a dot (so {@code /admin/chit-logo.png} or
     * {@code /admin/_next/static/...} are not redirected).
     */
    @GetMapping("/admin/{page:[^.]+}")
    public String redirectTrailingSlash(@PathVariable String page, HttpServletRequest req) {
        String qs = req.getQueryString();
        return "redirect:/admin/" + page + "/" + (qs != null ? "?" + qs : "");
    }
}