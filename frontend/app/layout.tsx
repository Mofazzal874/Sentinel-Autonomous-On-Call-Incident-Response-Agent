import type { Metadata } from "next";
import { IBM_Plex_Mono, Manrope } from "next/font/google";
import "./globals.css";

const sans = Manrope({
  subsets: ["latin"],
  variable: "--font-sans",
  display: "swap",
});

const mono = IBM_Plex_Mono({
  subsets: ["latin"],
  variable: "--font-mono",
  weight: ["400", "500", "600"],
  display: "swap",
});

export const metadata: Metadata = {
  metadataBase: new URL("https://sentinel-mofazzal874.centralindia.cloudapp.azure.com"),
  title: "Sentinel | Incident Operations Console",
  description:
    "Investigate incidents, inspect grounded remediation proposals, and verify deterministic safety decisions.",
  openGraph: {
    title: "Sentinel | Incident Operations Console",
    description: "Evidence-grounded incident response with deterministic safety controls.",
    type: "website",
    images: [{ url: "/og.png", width: 1200, height: 630, alt: "Sentinel incident-response control flow" }],
  },
  twitter: {
    card: "summary_large_image",
    title: "Sentinel | Incident Operations Console",
    description: "Investigate quickly. Keep humans in control.",
    images: ["/og.png"],
  },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body className={`${sans.variable} ${mono.variable}`}>{children}</body>
    </html>
  );
}
