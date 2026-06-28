/**
 * validate_and_summarize.js
 * --------------------------
 * Beginner-friendly Node.js script — the "quality control" stage of the
 * pipeline. It runs AFTER the Java program has produced
 * data/processed_bank_data.csv, and BEFORE you open Power BI.
 *
 * What it does:
 *   1. Reads data/processed_bank_data.csv
 *   2. Validates it: checks for missing values, negative numbers where
 *      they shouldn't be, and duplicate bank/year rows
 *   3. Flags statistical outliers (z-score > 2) per metric, per year —
 *      e.g. it will correctly flag UBS's 2023 Net Income as an outlier,
 *      because that year included a one-off accounting gain from the
 *      Credit Suisse takeover. This is a great real example of why a
 *      data scientist always checks for outliers before trusting a chart!
 *   4. Writes data/summary_stats.json, a compact summary used by
 *      js/preview_dashboard.html
 *
 * HOW TO RUN:
 *   node validate_and_summarize.js
 *
 * Requires Node.js only — no npm packages needed.
 */

const fs = require("fs");
const path = require("path");

const DATA_DIR = fs.existsSync(path.join(__dirname, "..", "data"))
  ? path.join(__dirname, "..", "data")
  : path.join(__dirname, "data");

const INPUT_FILE = path.join(DATA_DIR, "processed_bank_data.csv");
const OUTPUT_FILE = path.join(DATA_DIR, "summary_stats.json");

function parseCSV(text) {
  const [headerLine, ...lines] = text.trim().split("\n");
  const headers = headerLine.split(",");
  return lines
    .filter((l) => l.trim().length > 0)
    .map((line) => {
      const cells = line.split(",");
      const row = {};
      headers.forEach((h, i) => (row[h] = cells[i]));
      return row;
    });
}

function toNum(v) {
  if (v === undefined || v === "" || v === null) return null;
  const n = Number(v);
  return Number.isNaN(n) ? null : n;
}

function mean(arr) {
  return arr.reduce((a, b) => a + b, 0) / arr.length;
}

function stdDev(arr) {
  const m = mean(arr);
  const variance = mean(arr.map((v) => (v - m) ** 2));
  return Math.sqrt(variance);
}

function main() {
  if (!fs.existsSync(INPUT_FILE)) {
    console.error(
      "Could not find " + INPUT_FILE + "\nRun the Java program first: \n  cd ../java && javac BankFinancialAnalyzer.java && java BankFinancialAnalyzer"
    );
    process.exit(1);
  }

  const raw = fs.readFileSync(INPUT_FILE, "utf8");
  const rows = parseCSV(raw).map((r) => ({
    ...r,
    Year: toNum(r.Year),
    Revenue_USD_M: toNum(r.Revenue_USD_M),
    NetIncome_USD_M: toNum(r.NetIncome_USD_M),
    TotalAssets_USD_M: toNum(r.TotalAssets_USD_M),
    ROE_Pct: toNum(r.ROE_Pct),
    CET1_Pct: toNum(r.CET1_Pct),
    NetMargin_Pct: toNum(r.NetMargin_Pct),
    FinancialHealthScore: toNum(r.FinancialHealthScore),
  }));

  console.log(`Loaded ${rows.length} rows from processed_bank_data.csv`);

  // ---- 1. Validation checks --------------------------------------------
  const issues = [];
  const seen = new Set();
  for (const r of rows) {
    const key = r.BankID + "-" + r.Year;
    if (seen.has(key)) issues.push(`Duplicate row for BankID ${r.BankID}, Year ${r.Year}`);
    seen.add(key);

    for (const field of ["Revenue_USD_M", "NetIncome_USD_M", "TotalAssets_USD_M", "ROE_Pct", "CET1_Pct"]) {
      if (r[field] === null) issues.push(`Missing ${field} for ${r.BankName} ${r.Year}`);
    }
    if (r.Revenue_USD_M !== null && r.Revenue_USD_M <= 0) {
      issues.push(`Non-positive revenue for ${r.BankName} ${r.Year}`);
    }
  }

  // ---- 2. Outlier detection (z-score per metric, per year) -------------
  const outliers = [];
  const years = [...new Set(rows.map((r) => r.Year))].sort();
  const metricsToCheck = ["NetIncome_USD_M", "ROE_Pct", "FinancialHealthScore"];

  for (const year of years) {
    const yearRows = rows.filter((r) => r.Year === year);
    for (const metric of metricsToCheck) {
      const values = yearRows.map((r) => r[metric]).filter((v) => v !== null);
      const m = mean(values);
      const sd = stdDev(values);
      if (sd === 0) continue;
      for (const r of yearRows) {
        const z = (r[metric] - m) / sd;
        if (Math.abs(z) > 2) {
          outliers.push({
            bank: r.BankName,
            year,
            metric,
            value: r[metric],
            zScore: Math.round(z * 100) / 100,
          });
        }
      }
    }
  }

  // ---- 3. Build summary stats for the preview dashboard -----------------
  const banks = [...new Map(rows.map((r) => [r.BankID, r.BankName])).entries()];
  const latestYear = Math.max(...years);

  const byRegionLatest = {};
  for (const r of rows.filter((r) => r.Year === latestYear)) {
    if (!byRegionLatest[r.Region]) byRegionLatest[r.Region] = { revenue: 0, netIncome: 0, count: 0 };
    byRegionLatest[r.Region].revenue += r.Revenue_USD_M;
    byRegionLatest[r.Region].netIncome += r.NetIncome_USD_M;
    byRegionLatest[r.Region].count += 1;
  }

  const summary = {
    generatedAt: new Date().toISOString(),
    rowCount: rows.length,
    years,
    latestYear,
    banks: banks.map(([id, name]) => ({ id, name })),
    validationIssues: issues,
    outliers,
    regionTotalsLatestYear: byRegionLatest,
    series: rows.map((r) => ({
      bankId: r.BankID,
      bank: r.BankName,
      region: r.Region,
      year: r.Year,
      revenue: r.Revenue_USD_M,
      netIncome: r.NetIncome_USD_M,
      roe: r.ROE_Pct,
      cet1: r.CET1_Pct,
      netMargin: r.NetMargin_Pct,
      healthScore: r.FinancialHealthScore,
      lat: Number(r.Latitude),
      lon: Number(r.Longitude),
    })),
  };

  fs.writeFileSync(OUTPUT_FILE, JSON.stringify(summary, null, 2));

  console.log(`\nValidation issues found: ${issues.length}`);
  issues.forEach((i) => console.log("  - " + i));

  console.log(`\nStatistical outliers (|z-score| > 2): ${outliers.length}`);
  outliers.forEach((o) =>
    console.log(`  - ${o.bank} ${o.year}: ${o.metric} = ${o.value} (z=${o.zScore})`)
  );

  console.log(`\nSummary written to ${OUTPUT_FILE}`);
  console.log("This file also powers js/preview_dashboard.html — open that file in a browser to see a quick visual check before building the Power BI dashboard.");
}

main();
