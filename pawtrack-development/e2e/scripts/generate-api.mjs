// Generates TypeScript types from the OpenAPI contracts (source of truth) into src/proxy (gitignored).
// Runs before every test run, so a contract change that breaks the tests fails at compile time.
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import openapiTS, { astToString } from 'openapi-typescript';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const contractsDir = resolve(root, '../contracts');
const outDir = resolve(root, 'src/proxy');
const contracts = ['identity', 'pet', 'health', 'notification'];

mkdirSync(outDir, { recursive: true });
for (const name of contracts) {
  const ast = await openapiTS(pathToFileURL(resolve(contractsDir, `${name}.yaml`)));
  const banner = `// GENERATED from contracts/${name}.yaml by scripts/generate-api.mjs. Do not edit.\n`;
  writeFileSync(resolve(outDir, `${name}.ts`), banner + astToString(ast));
  console.log(`openapi: ${name}.yaml -> src/proxy/${name}.ts`);
}
