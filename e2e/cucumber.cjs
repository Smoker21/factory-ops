// Cucumber configuration (CommonJS)
module.exports = {
  default: {
    paths: ['features/**/*.feature'],
    requireModule: ['ts-node/register'],
    require: [
      'support/gherkin-patch.js',
      'support/world.ts',
      'support/hooks.ts',
      'steps/**/*.steps.ts',
    ],
    format: [
      'progress-bar',
      'html:reports/cucumber.html',
      'json:reports/cucumber.json',
    ],
    formatOptions: {
      snippetInterface: 'async-await',
    },
    parallel: 1,
    language: 'zh-TW',
    timeout: 30000,
  },
};
