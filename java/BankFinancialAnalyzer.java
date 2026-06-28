import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * BankFinancialAnalyzer
 * ----------------------
 * A beginner-friendly Java program that plays the role of the "ETL / data
 * science" stage of this project.
 *
 * What it does:
 *   1. Reads three raw CSV files (banks, financials, fx_rates)
 *   2. Joins them together (like a SQL JOIN, done by hand)
 *   3. Converts every bank's Revenue / Net Income / Total Assets into USD,
 *      so European and American banks become directly comparable
 *   4. Calculates extra metrics that are NOT in the raw data:
 *        - Net Profit Margin (%)
 *        - Year-over-year Revenue growth (%)
 *        - Year-over-year Net Income growth (%)
 *        - A composite 0-100 "Financial Health Score" for each bank/year,
 *          combining profitability (ROE), capital strength (CET1),
 *          margin, and growth.
 *   5. Writes everything to data/processed_bank_data.csv, which is the
 *      file you import into Power BI.
 *
 * HOW TO RUN (no installation needed beyond a JDK):
 *   javac BankFinancialAnalyzer.java
 *   java BankFinancialAnalyzer
 *
 * It expects to be run from the project's "java" folder, with a sibling
 * "data" folder containing banks.csv, financials.csv and fx_rates.csv.
 */
public class BankFinancialAnalyzer {

    // ---- Simple data holder for one bank's static info -------------------
    static class Bank {
        int id;
        String name, ticker, country, region, hqCity, currency;
        double lat, lon;
    }

    // ---- Simple data holder for one bank-year record ----------------------
    static class Record {
        int bankId, year;
        String currency;
        double revenue, netIncome, totalAssets, roe, cet1;
        int employees;

        // computed fields
        double revenueUSD, netIncomeUSD, totalAssetsUSD;
        double netMarginPct;
        Double revenueGrowthPct;   // null for the first year of each bank
        Double netIncomeGrowthPct; // null for the first year of each bank
        double healthScore;        // filled in later, per-year normalization
    }

    public static void main(String[] args) throws IOException {
        Path dataDir = Paths.get("..", "data");
        if (!Files.exists(dataDir.resolve("banks.csv"))) {
            // allow running from the data folder itself too
            dataDir = Paths.get("data");
        }

        Map<Integer, Bank> banks = readBanks(dataDir.resolve("banks.csv"));
        Map<Integer, double[]> fx = readFx(dataDir.resolve("fx_rates.csv")); // year -> [EURtoUSD, GBPtoUSD]
        List<Record> records = readFinancials(dataDir.resolve("financials.csv"));

        convertToUSD(records, fx);
        computeMarginsAndGrowth(records);
        computeHealthScores(records);

        Path outPath = dataDir.resolve("processed_bank_data.csv");
        writeOutput(outPath, banks, records);

        System.out.println("Done! Wrote " + records.size() + " rows to " + outPath.toAbsolutePath());
        printTopAndBottom(banks, records);
    }

    // ------------------------------------------------------------------
    static Map<Integer, Bank> readBanks(Path path) throws IOException {
        Map<Integer, Bank> map = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(path);
        for (int i = 1; i < lines.size(); i++) { // skip header
            String[] c = lines.get(i).split(",", -1);
            Bank b = new Bank();
            b.id = Integer.parseInt(c[0]);
            b.name = c[1];
            b.ticker = c[2];
            b.country = c[3];
            b.region = c[4];
            b.hqCity = c[5];
            b.lat = Double.parseDouble(c[6]);
            b.lon = Double.parseDouble(c[7]);
            b.currency = c[8];
            map.put(b.id, b);
        }
        return map;
    }

    static Map<Integer, double[]> readFx(Path path) throws IOException {
        Map<Integer, double[]> map = new HashMap<>();
        List<String> lines = Files.readAllLines(path);
        for (int i = 1; i < lines.size(); i++) {
            String[] c = lines.get(i).split(",", -1);
            int year = Integer.parseInt(c[0]);
            double eur = Double.parseDouble(c[1]);
            double gbp = Double.parseDouble(c[2]);
            map.put(year, new double[]{eur, gbp});
        }
        return map;
    }

    static List<Record> readFinancials(Path path) throws IOException {
        List<Record> list = new ArrayList<>();
        List<String> lines = Files.readAllLines(path);
        for (int i = 1; i < lines.size(); i++) {
            String[] c = lines.get(i).split(",", -1);
            Record r = new Record();
            r.bankId = Integer.parseInt(c[0]);
            r.year = Integer.parseInt(c[1]);
            r.currency = c[2];
            r.revenue = Double.parseDouble(c[3]);
            r.netIncome = Double.parseDouble(c[4]);
            r.totalAssets = Double.parseDouble(c[5]);
            r.roe = Double.parseDouble(c[6]);
            r.cet1 = Double.parseDouble(c[7]);
            r.employees = Integer.parseInt(c[8]);
            list.add(r);
        }
        return list;
    }

    // ------------------------------------------------------------------
    static void convertToUSD(List<Record> records, Map<Integer, double[]> fx) {
        for (Record r : records) {
            double rate = 1.0; // USD already
            double[] yearRates = fx.get(r.year);
            if (r.currency.equals("EUR")) rate = yearRates[0];
            else if (r.currency.equals("GBP")) rate = yearRates[1];

            r.revenueUSD = r.revenue * rate;
            r.netIncomeUSD = r.netIncome * rate;
            r.totalAssetsUSD = r.totalAssets * rate;
        }
    }

    static void computeMarginsAndGrowth(List<Record> records) {
        // group by bank, sort by year, so we can compute YoY growth
        Map<Integer, List<Record>> byBank = new TreeMap<>();
        for (Record r : records) {
            byBank.computeIfAbsent(r.bankId, k -> new ArrayList<>()).add(r);
        }
        for (List<Record> list : byBank.values()) {
            list.sort(Comparator.comparingInt(r -> r.year));
            for (int i = 0; i < list.size(); i++) {
                Record r = list.get(i);
                r.netMarginPct = (r.netIncomeUSD / r.revenueUSD) * 100.0;
                if (i > 0) {
                    Record prev = list.get(i - 1);
                    r.revenueGrowthPct = ((r.revenueUSD - prev.revenueUSD) / prev.revenueUSD) * 100.0;
                    r.netIncomeGrowthPct = ((r.netIncomeUSD - prev.netIncomeUSD) / prev.netIncomeUSD) * 100.0;
                }
            }
        }
    }

    /**
     * Builds a 0-100 "Financial Health Score" for every bank-year by
     * min-max normalizing four metrics WITHIN EACH YEAR (so a bank is
     * judged against its peers in that same year), then combining them
     * with weights:
     *   35% Return on Equity (profitability)
     *   25% CET1 ratio (capital strength / safety)
     *   20% Net margin (efficiency)
     *   20% Net income YoY growth (momentum) - skipped for first-year rows
     */
    static void computeHealthScores(List<Record> records) {
        Map<Integer, List<Record>> byYear = new TreeMap<>();
        for (Record r : records) byYear.computeIfAbsent(r.year, k -> new ArrayList<>()).add(r);

        for (List<Record> yearGroup : byYear.values()) {
            double[] roeMinMax = minMax(yearGroup, r -> r.roe);
            double[] cet1MinMax = minMax(yearGroup, r -> r.cet1);
            double[] marginMinMax = minMax(yearGroup, r -> r.netMarginPct);
            double[] growthMinMax = minMax(yearGroup, r -> r.netIncomeGrowthPct == null ? 0 : r.netIncomeGrowthPct);

            for (Record r : yearGroup) {
                double roeScore = normalize(r.roe, roeMinMax);
                double cet1Score = normalize(r.cet1, cet1MinMax);
                double marginScore = normalize(r.netMarginPct, marginMinMax);
                double growthVal = r.netIncomeGrowthPct == null ? (growthMinMax[0] + growthMinMax[1]) / 2 : r.netIncomeGrowthPct;
                double growthScore = normalize(growthVal, growthMinMax);

                r.healthScore = roeScore * 0.35 + cet1Score * 0.25 + marginScore * 0.20 + growthScore * 0.20;
            }
        }
    }

    interface Metric { double get(Record r); }

    static double[] minMax(List<Record> list, Metric m) {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (Record r : list) {
            double v = m.get(r);
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        return new double[]{min, max};
    }

    static double normalize(double value, double[] minMax) {
        if (minMax[1] == minMax[0]) return 50.0; // avoid divide-by-zero, neutral score
        return ((value - minMax[0]) / (minMax[1] - minMax[0])) * 100.0;
    }

    // ------------------------------------------------------------------
    static void writeOutput(Path outPath, Map<Integer, Bank> banks, List<Record> records) throws IOException {
        records.sort(Comparator.<Record>comparingInt(r -> r.bankId).thenComparingInt(r -> r.year));
        StringBuilder sb = new StringBuilder();
        sb.append("BankID,BankName,Ticker,Country,Region,HQCity,Latitude,Longitude,Year,")
          .append("Revenue_USD_M,NetIncome_USD_M,TotalAssets_USD_M,ROE_Pct,CET1_Pct,Employees,")
          .append("NetMargin_Pct,RevenueGrowth_YoY_Pct,NetIncomeGrowth_YoY_Pct,FinancialHealthScore\n");

        for (Record r : records) {
            Bank b = banks.get(r.bankId);
            sb.append(b.id).append(',')
              .append(csv(b.name)).append(',')
              .append(b.ticker).append(',')
              .append(csv(b.country)).append(',')
              .append(b.region).append(',')
              .append(csv(b.hqCity)).append(',')
              .append(b.lat).append(',')
              .append(b.lon).append(',')
              .append(r.year).append(',')
              .append(round2(r.revenueUSD)).append(',')
              .append(round2(r.netIncomeUSD)).append(',')
              .append(round2(r.totalAssetsUSD)).append(',')
              .append(r.roe).append(',')
              .append(r.cet1).append(',')
              .append(r.employees).append(',')
              .append(round2(r.netMarginPct)).append(',')
              .append(r.revenueGrowthPct == null ? "" : round2(r.revenueGrowthPct)).append(',')
              .append(r.netIncomeGrowthPct == null ? "" : round2(r.netIncomeGrowthPct)).append(',')
              .append(round2(r.healthScore)).append('\n');
        }
        Files.write(outPath, sb.toString().getBytes());
    }

    static String csv(String s) { return s.contains(",") ? "\"" + s + "\"" : s; }
    static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    // ------------------------------------------------------------------
    static void printTopAndBottom(Map<Integer, Bank> banks, List<Record> records) {
        int latestYear = records.stream().mapToInt(r -> r.year).max().orElse(0);
        List<Record> latest = new ArrayList<>();
        for (Record r : records) if (r.year == latestYear) latest.add(r);
        latest.sort((a, c) -> Double.compare(c.healthScore, a.healthScore));

        System.out.println("\nFinancial Health Score ranking for " + latestYear + " ");
        int rank = 1;
        for (Record r : latest) {
            Bank b = banks.get(r.bankId);
            System.out.printf("%2d. %-20s (%-10s) Score=%5.1f  ROE=%5.1f%%  CET1=%5.1f%%  NetMargin=%5.1f%%%n",
                    rank++, b.name, b.region, r.healthScore, r.roe, r.cet1, r.netMarginPct);
        }
    }
}
