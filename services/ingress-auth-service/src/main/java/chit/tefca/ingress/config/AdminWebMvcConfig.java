package chit.tefca.ingress.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Serves the embedded Next.js admin UI from classpath:/static/admin/.
 *
 * Spring Boot's default static handler does serve /admin/index.html for root
 * directory requests, but it will not auto-resolve nested directory URLs like
 * /admin/login/ -> /admin/login/index.html. This configurer adds that
 * directory-index resolution explicitly and falls back to the SPA root
 * /admin/index.html for any non-file path that has no matching directory.
 */
@Configuration
public class AdminWebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Hashed Next.js build assets (filename includes content hash) — safe to cache forever.
        registry.addResourceHandler("/admin/_next/static/**")
                .addResourceLocations("classpath:/static/admin/_next/static/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());

        // HTML / SPA fallback / everything else — never cache, so newly deployed
        // admin UI changes (new buttons, new fields) appear on the next page load
        // instead of being hidden behind the browser cache.
        registry.addResourceHandler("/admin/**")
                .addResourceLocations("classpath:/static/admin/")
                .setCacheControl(CacheControl.noCache().mustRevalidate())
                .resourceChain(false)
                .addResolver(new SpaIndexResolver());
    }

    /**
     * Resolution rules (first match wins):
     *   1. Path matches a real file under static/admin/  -> serve it.
     *   2. Path ends with '/' OR has no extension AND  static/admin/<path>/index.html exists -> serve that.
     *   3. Otherwise fall back to /admin/index.html so the Next.js client router
     *      can handle deep links it generated itself.
     */
    static class SpaIndexResolver extends PathResourceResolver {
        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource direct = super.getResource(resourcePath, location);
            if (direct != null) return direct;

            String normalized = resourcePath.endsWith("/")
                    ? resourcePath.substring(0, resourcePath.length() - 1)
                    : resourcePath;
            if (!normalized.isEmpty() && !normalized.contains(".")) {
                Resource dirIndex = super.getResource(normalized + "/index.html", location);
                if (dirIndex != null) return dirIndex;
            }

            // Final fallback for SPA deep links.
            return super.getResource("index.html", location);
        }
    }
}
