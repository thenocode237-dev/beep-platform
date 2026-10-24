import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), "../..");

/** @type {import('next').NextConfig} */
export default {
  output: "standalone",
  outputFileTracingRoot: root,
  transpilePackages: ["@beep/ui", "@beep/design-tokens"],
  poweredByHeader: false,
  reactStrictMode: true,
};
