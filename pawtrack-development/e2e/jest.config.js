/** @type {import('jest').Config} */
module.exports = {
  preset: 'ts-jest',
  testEnvironment: 'node',
  testMatch: ['<rootDir>/src/tests/**/*-test.ts'],
  globalSetup: '<rootDir>/src/globalSetup.ts',
  // Events travel through RabbitMQ, so some assertions wait for eventual consistency.
  testTimeout: 60_000,
  reporters: ['default', ['jest-junit', { outputDirectory: '.', outputName: 'junit.xml' }]],
};
