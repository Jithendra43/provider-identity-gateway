import { TopNavPublic } from '@/components/landing/TopNavPublic';
import { Hero } from '@/components/landing/Hero';
import { CapabilitiesGrid } from '@/components/landing/CapabilitiesGrid';
import { StandardsSection } from '@/components/landing/StandardsSection';
import { ComplianceSection } from '@/components/landing/ComplianceSection';
import { ContactSection } from '@/components/landing/ContactSection';
import { Footer } from '@/components/landing/Footer';

export default function LandingPage() {
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
