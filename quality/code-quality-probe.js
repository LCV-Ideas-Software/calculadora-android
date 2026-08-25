/**
 * Analysis-only JavaScript marker for GitHub Code Quality.
 *
 * This module is deliberately not imported by the Pages site, an Android
 * application, or any production runtime. Its only purpose is to keep one
 * supported language in this repository so native Code Quality has a
 * deterministic target before real application source exists.
 */
export const CODE_QUALITY_PROBE = Object.freeze({
  repository: "LCV-Ideas-Software/calculadora-android",
  purpose: "GitHub Code Quality language detection",
});
