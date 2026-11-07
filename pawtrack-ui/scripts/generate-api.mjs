// Generates TypeScript types from the OpenAPI contracts (source of truth) into a gitignored folder.
// Runs automatically before start/build/test/lint. Override the contract location with CONTRACTS_DIR
// (the Docker build passes the contracts as a named build context).
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import openapiTS, { astToString } from 'openapi-typescript';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const contractsDir = resolve(root, process.env.CONTRACTS_DIR ?? '../pawtrack-development/contracts');
const outDir = resolve(root, 'src/app/core/api/generated');
const contracts = ['identity', 'pet'];

mkdirSync(outDir, { recursive: true });
for (const name of contracts) {
  const source = resolve(contractsDir, `${name}.yaml`);
  const ast = await openapiTS(pathToFileURL(source));
  const banner = `// GENERATED from contracts/${name}.yaml by scripts/generate-api.mjs. Do not edit.\n/* eslint-disable */\n`;
  writeFileSync(resolve(outDir, `${name}.ts`), banner + astToString(ast));
  console.log(`openapi: ${name}.yaml -> src/app/core/api/generated/${name}.ts`);
}
