/**
 * Generates an HTML report from the cucumber.json output.
 * Run: node scripts/generate-report.cjs
 */
'use strict';

const reporter = require('cucumber-html-reporter');
const path = require('path');

const options = {
  theme: 'bootstrap',
  jsonFile: path.join(__dirname, '..', 'reports', 'cucumber.json'),
  output: path.join(__dirname, '..', 'reports', 'cucumber-report.html'),
  reportSuiteAsScenarios: true,
  scenarioTimestamp: true,
  launchReport: false,
  metadata: {
    'App Version': '1.0.0',
    'Test Environment': 'local',
    Browser: 'Chromium',
    'Test Runner': 'Cucumber.js v11',
  },
};

reporter.generate(options);
console.log('HTML report generated at:', options.output);
