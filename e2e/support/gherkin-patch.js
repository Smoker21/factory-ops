/**
 * Patches the @cucumber/gherkin dialects to include Traditional Chinese keywords
 * that were used in the feature files (情境, 情境大綱) but are not in the
 * standard zh-TW dialect (which uses 場景/劇本, 場景大綱/劇本大綱 instead).
 *
 * This module must be required BEFORE any Gherkin parsing occurs.
 */
const { dialects } = require('@cucumber/gherkin');

if (dialects['zh-TW']) {
  if (!dialects['zh-TW'].scenario.includes('情境')) {
    dialects['zh-TW'].scenario.push('情境');
  }
  if (!dialects['zh-TW'].scenarioOutline.includes('情境大綱')) {
    dialects['zh-TW'].scenarioOutline.push('情境大綱');
  }
}
