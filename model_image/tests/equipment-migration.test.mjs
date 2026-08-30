import assert from "node:assert/strict";
import crypto from "node:crypto";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import {
  initV2Catalog,
  migrateEquipmentCatalog,
} from "../equipment/tools/migrate-equipment-catalog-v1-to-v2.mjs";
import { writeJson } from "../lib/pipeline-contract.mjs";

const ONE_BY_ONE_PNG = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
  "base64",
);

test("v1 migration registers existing final PNGs without copying or guessing review fields", async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "equipment-migration-"));
  try {
    const catalogDirectory = path.join(root, "equipment");
    const inputPath = path.join(catalogDirectory, "equipment-catalog-v1.json");
    const outputPath = path.join(catalogDirectory, "equipment-catalog.json");
    const finalPath = path.join(catalogDirectory, "final", "dumbbell_adjustable.png");
    await fs.mkdir(path.dirname(finalPath), { recursive: true });
    await fs.writeFile(finalPath, ONE_BY_ONE_PNG);
    await writeJson(inputPath, {
      schemaVersion: 1,
      coordinateSystem: { origin: "top_left", unit: "normalized_0_to_1" },
      assets: [
        {
          id: "dumbbell_v1",
          type: "dumbbell",
          viewId: "front",
          file: "final/dumbbell_adjustable.png",
          canvas: { width: 999, height: 999 },
          anchors: { grip_center: [0.5, 0.5] },
        },
        {
          id: "source_only_v1",
          type: "bench",
          viewId: "front",
          file: "source/bench.png",
          canvas: { width: 10, height: 10 },
          anchors: { center: [0.5, 0.5] },
        },
      ],
    });

    const result = await migrateEquipmentCatalog({ inputPath, outputPath });
    const output = JSON.parse(await fs.readFile(outputPath, "utf8"));
    const migrated = output.assets[0];

    assert.equal(result.report.copiedImages, 0);
    assert.equal(result.report.registeredExistingFinalImages, 1);
    assert.deepEqual(result.report.skippedSourceAssets, [{
      id: "source_only_v1",
      file: "source/bench.png",
      reason: "non_final_reference_not_imported",
    }]);
    assert.equal(migrated.file, "final/dumbbell_adjustable.png");
    assert.equal(migrated.width, 1);
    assert.equal(migrated.height, 1);
    assert.equal(migrated.sha256, crypto.createHash("sha256").update(ONE_BY_ONE_PNG).digest("hex"));
    assert.equal(migrated.status, null);
    assert.equal(migrated.renderClass, null);
    assert.deepEqual(result.report.pendingDecisions, [
      { id: "dumbbell_v1", field: "status" },
      { id: "dumbbell_v1", field: "renderClass" },
    ]);
    assert.equal(await fs.readFile(finalPath, "base64"), ONE_BY_ONE_PNG.toString("base64"));
  } finally {
    await fs.rm(root, { recursive: true, force: true });
  }
});

test("v2 init creates an empty catalog and no asset decisions", async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "equipment-catalog-init-"));
  try {
    const outputPath = path.join(root, "equipment-catalog.json");
    const result = await initV2Catalog(outputPath);
    assert.deepEqual(result.catalog.assets, []);
    assert.equal(result.catalog.schemaVersion, 2);
    assert.deepEqual(result.report.pendingDecisions, []);
  } finally {
    await fs.rm(root, { recursive: true, force: true });
  }
});
