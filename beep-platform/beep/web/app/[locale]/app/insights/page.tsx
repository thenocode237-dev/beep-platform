import { EcosystemPage } from "@/components/EcosystemPage";
export const dynamic = "force-dynamic";
export default function Page(props: {
  params: Promise<{ locale: string }>;
  searchParams: Promise<{ invite?: string; ref?: string }>;
}) {
  return <EcosystemPage {...props} section="insights" />;
}
