# Power BI Step-by-Step Guide
## Top 10 Global Banks — Financial Health Dashboard (2021–2025)

This guide assumes **zero prior Power BI experience**. Follow it top to bottom.
You'll need **Power BI Desktop** (free, Windows only — install from
https://www.microsoft.com/en-us/power-platform/products/power-bi/downloads).
If you're on Mac, use a Windows VM, a lab PC, or Power BI Service in a browser
(import works the same way once the .pbix is built on Windows).

Before you start, make sure you've run the Java program and the Node.js
script at least once (see README.md) so that `data/processed_bank_data.csv`
exists and is up to date.

---

## Step 1 — Get the data into Power BI

1. Open Power BI Desktop.
2. **Home tab → Get Data → Excel workbook**.
3. Select `data/bank_financial_data.xlsx`.
4. In the Navigator window, tick the box for **Processed_for_PowerBI** only
   (this is the single clean table you need — everything else in that
   workbook is the "working" data behind it).
5. Click **Transform Data** (not "Load" yet) — this opens Power Query Editor.

### Clean up data types (in Power Query Editor)
6. Click the column header icons to confirm types:
   - `Year` → Whole Number
   - `Latitude`, `Longitude` → Decimal Number
   - `Revenue_USD_M`, `NetIncome_USD_M`, `TotalAssets_USD_M`,
     `ROE_Pct`, `CET1_Pct`, `NetMargin_Pct`, `RevenueGrowth_YoY_Pct`,
     `NetIncomeGrowth_YoY_Pct`, `FinancialHealthScore` → Decimal Number
   - `BankID`, `Employees` → Whole Number
   - Everything else (`BankName`, `Ticker`, `Country`, `Region`, `HQCity`)
     → Text
7. Click **Close & Apply** (top-left). Your table loads into the data model.

---

## Step 2 — Build a Date/Year table (optional but good practice)

Since we only have whole years (no months), you can skip a full date table.
Instead, just make sure `Year` is treated as a **whole number**, not a date,
so Power BI doesn't try to auto-create a date hierarchy on it (which adds
unwanted "Quarter"/"Month" buckets). Right-click the `Year` column in the
**Data** view → if a date hierarchy was auto-created in any visual, remove it
and use the plain `Year` field instead.

---

## Step 3 — Create DAX measures

Go to the **Data** view (left sidebar, table icon). Click on the
`Processed_for_PowerBI` table, then **Table tools → New measure**. Add these
one at a time (copy-paste the formula, rename as shown):

```DAX
Total Revenue (USD M) = SUM(Processed_for_PowerBI[Revenue_USD_M])
```

```DAX
Total Net Income (USD M) = SUM(Processed_for_PowerBI[NetIncome_USD_M])
```

```DAX
Avg ROE % = AVERAGE(Processed_for_PowerBI[ROE_Pct])
```

```DAX
Avg CET1 % = AVERAGE(Processed_for_PowerBI[CET1_Pct])
```

```DAX
Avg Health Score = AVERAGE(Processed_for_PowerBI[FinancialHealthScore])
```

```DAX
Net Income YoY % = AVERAGE(Processed_for_PowerBI[NetIncomeGrowth_YoY_Pct])
```

```DAX
Selected Year =
IF(
    HASONEVALUE(Processed_for_PowerBI[Year]),
    VALUES(Processed_for_PowerBI[Year]),
    BLANK()
)
```

```DAX
Rank by Health Score =
RANKX(
    FILTER(
        Processed_for_PowerBI,
        Processed_for_PowerBI[Year] = MAX(Processed_for_PowerBI[Year])
    ),
    [Avg Health Score],,
    DESC
)
```

Each measure should appear with a calculator icon in the field list. If you
see a red squiggle or an error, double-check the column name spelling
matches exactly (Power BI is case-sensitive in some contexts).

---

## Step 4 — Build the report pages

Create **3 pages** (right-click the page tab at the bottom → Rename):
`Overview`, `Region Comparison`, `Bank Deep Dive`.

### Page 1 — Overview

1. **KPI Cards** (Visualizations pane → Card):
   - Card 1: `Total Revenue (USD M)`
   - Card 2: `Total Net Income (USD M)`
   - Card 3: `Avg ROE %`
   - Card 4: `Avg Health Score`
   Place these in a row at the top.

2. **Slicer** for `Year`: Visualizations → Slicer, drag `Year` into it. Set
   it to a horizontal list or dropdown (Format pane → Slicer settings →
   Style). This lets you click a year and have the whole page update.

3. **Map visual** (this is the "localisation" piece):
   - Visualizations → **Map** (or **ArcGIS Map** for a nicer look).
   - Drag `Latitude` into the **Latitude** field, `Longitude` into
     **Longitude**.
   - Drag `BankName` into **Legend**.
   - Drag `NetIncome_USD_M` into **Size** (bigger bubble = more profit).
   - Drag `Region` into **Legend** instead of BankName if you'd rather
     color by region (Europe vs Americas) — try both and see which tells
     the story better.
   - Add a tooltip: drag `ROE_Pct`, `CET1_Pct`, `HQCity` into the
     **Tooltips** field well so hovering shows details.

4. **Bar chart**: Revenue by bank.
   - Visualizations → Clustered bar chart.
   - Axis: `BankName`. Values: `Total Revenue (USD M)`.
   - Sort descending (click the "..." on the visual → Sort by Total Revenue).

### Page 2 — Region Comparison

1. **Line chart**: trend over time.
   - Axis: `Year`. Values: `Total Net Income (USD M)`. Legend: `Region`.
   - This shows Europe's combined net income vs Americas' combined net
     income, 2021→2025, as two lines.

2. **Clustered column chart**: Avg ROE % and Avg CET1 % by Region, for the
   selected year (use the same `Year` slicer from page 1 — Power BI slicers
   carry across pages if you set them to "Sync slicers", see Step 5).

3. **Scatter chart**: ROE vs CET1 ratio.
   - X axis: `Avg CET1 %`. Y axis: `Avg ROE %`. Legend: `Region`. Details:
     `BankName`. Size: `Total Revenue (USD M)`.
   - This is a classic "risk vs. reward" view: top-right = high profitability
     AND high capital safety buffer.

### Page 3 — Bank Deep Dive

1. **Slicer**: `BankName` (single-select, list style).
2. **Line chart**: `Year` on axis, `Revenue_USD_M` and `NetIncome_USD_M`
   both as values (two lines) for the selected bank.
3. **Gauge or KPI visual**: `FinancialHealthScore` for the selected
   bank/year vs the average across all 10 banks (target line).
4. **Table**: all columns from `Processed_for_PowerBI`, filtered to the
   selected bank, so the user can see the raw numbers behind the charts.

---

## Step 5 — Make slicers sync across pages

1. **View tab → Sync slicers** (opens a pane).
2. For the `Year` slicer, tick "Sync" and "Visible" for all 3 pages.
3. This way, picking 2024 on the Overview page also filters Region
   Comparison and Bank Deep Dive.

---

## Step 6 — Polish

- **Theme**: View tab → Themes → pick a clean theme, or use **Format → Edit
  custom theme** and set a primary color for "Americas" (e.g. blue) and a
  second accent for "Europe" (e.g. amber) — this matches the colors used in
  the JS preview dashboard for consistency.
- **Titles**: give every visual a clear title via the Format pane (General →
  Title).
- **Conditional formatting**: On the bar chart of `FinancialHealthScore`,
  use Format → Data colors → "Conditional formatting" with a color scale
  (red→yellow→green) so low/high scores are visually obvious.
- **Tooltips page** (optional, intermediate): build a small dedicated
  tooltip page that pops up bank details on hover over the map — see
  Power BI's "Report page tooltips" feature if you want to go further.

---

## Step 7 — Publish / Share

- **File → Save As** → save as `Bank_Financial_Health_Dashboard.pbix`.
- If you have a Power BI account (free for students with a school email via
  Microsoft 365 Education), **Home → Publish** to share it online and get a
  link, or export a static PDF via **File → Export → Export to PDF**.

---

## Troubleshooting

| Problem | Likely fix |
|---|---|
| Map shows no bubbles | Check Latitude/Longitude are typed as Decimal Number, not Text |
| Year slicer shows "2,021" with a comma | Format the Year column as Whole Number with no thousands separator (Data view → select column → Format → "0") |
| DAX measure shows error | Check exact column/table name spelling; table name is `Processed_for_PowerBI` |
| Numbers look 10x too big/small after re-running Java | You probably loaded an old cached copy — in Power BI, **Home → Refresh** after regenerating the Excel file |
| Europe vs Americas totals look wrong | Make sure currency conversion happened — only `Processed_for_PowerBI` (USD) should be used for cross-region comparisons, never `Financials_Raw` (native currency) |
