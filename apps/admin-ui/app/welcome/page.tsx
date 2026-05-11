import { TopNavPublic } from '@/components/landing/TopNavPublic';
import { Hero } from '@/components/landing/Hero';
import { CapabilitiesGrid } from '@/components/landing/CapabilitiesGrid';
import { StandardsSection } from '@/components/landing/StandardsSection';
import { ComplianceSection } from '@/components/landing/ComplianceSection';
import { ContactSection } from '@/components/landing/ContactSection';
import { Footer } from '@/components/landing/Footer';

/**
 * Public welcome / landing page.
 *
 * Spring Security's unauthenticated-browser entry-point redirects to
 * /admin/welcome/ so this page must be permit-all. It renders the same
 * marketing landing content as the root page and offers the Cognito
 * "Sign in securely" CTA so users can start the OIDC flow.
 *
 * The auth.tsx AuthProvider route-guard also redirects unauthenticated
 * visitors here. Having a real Next.js route at this path prevents the
 * SPA router from falling through to the 404 page when the fallback
 * index.html is served for /admin/welcome/.
 */
export default function WelcomePage() {
  return (
    <div className="min-h-screen bg-white">
      <TopNavPublic />
      <main>
        <Hero />
        <CapabilitiesGrid />
        <StandardsSection />
        <ComplianceSection />
        <ContactSection />
      </main>
      <Footer />
    </div>
  );
}
