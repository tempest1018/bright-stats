package org.brightstats.data;

import java.util.*;
import java.util.regex.Pattern;

/** Explicit import choices and reproducible preparation; text is never silently encoded. */
public final class DataPreparation {
    private DataPreparation() {}
    public enum Decimal { DOT, COMMA }
    public enum Kind { NUMERIC, TEXT, IGNORE }
    public enum Missing { REQUIRE_COMPLETE, MEAN_REPLACEMENT, REMOVE_ROWS }
    public record Settings(char delimiter, Decimal decimal, String missingMarker, Missing treatment) {
        public Settings {
            if (",;\t".indexOf(delimiter) < 0 || decimal == null || treatment == null)
                throw new IllegalArgumentException("Choose a delimiter, decimal format and missing-data treatment.");
            missingMarker = missingMarker == null ? "" : missingMarker.trim();
        }
    }
    public record Column(String name, Kind kind) {
        public Column { if (name == null || kind == null) throw new IllegalArgumentException("Invalid column definition"); name = name.trim(); }
    }
    public record Raw(List<String> headers, List<List<String>> rows) {
        public Raw {
            if(headers==null||rows==null||headers.isEmpty()||headers.size()>10000||rows.isEmpty()||rows.size()>1000000||(long)headers.size()*rows.size()>5000000)throw new IllegalArgumentException("Source shape exceeds one million rows, ten thousand columns or five million cells.");
            for(var row:rows)if(row==null||row.size()!=headers.size())throw new IllegalArgumentException("Source rows have different column counts.");
            headers = List.copyOf(headers); rows = rows.stream().map(List::copyOf).toList();
            if (headers.isEmpty() || rows.isEmpty()) throw new IllegalArgumentException("Expected source headers and data rows.");
            for (var row : rows) if (row.size() != headers.size()) throw new IllegalArgumentException("Source rows have different column counts.");
        }
    }
    public record Prepared(List<String> columns, double[][] values, List<List<String>> retainedRows,
                           List<Integer> sourceRows, int replacedCells, int removedRows, List<String> warnings) {
        public Prepared {
            columns = List.copyOf(columns); values = Arrays.stream(values).map(double[]::clone).toArray(double[][]::new);
            retainedRows = retainedRows.stream().map(List::copyOf).toList(); sourceRows = List.copyOf(sourceRows); warnings = List.copyOf(warnings);
        }
        @Override public double[][] values() { return Arrays.stream(values).map(double[]::clone).toArray(double[][]::new); }
    }
    private static final Pattern DOT = Pattern.compile("[+-]?(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
    private static final Pattern COMMA = Pattern.compile("[+-]?(?:[0-9]+|[0-9]{1,3}(?:\\.[0-9]{3})+)(?:,[0-9]+)?(?:[eE][+-]?[0-9]+)?");
    public static char detectDelimiter(String text) { return NumericCsv.detectDelimiter(text.startsWith("\ufeff") ? text.substring(1) : text); }
    public static Raw parse(String text, char delimiter) {
        if (text.startsWith("\ufeff")) text = text.substring(1);
        var rows = NumericCsv.records(text, delimiter);
        if (rows.size() < 2) throw new IllegalArgumentException("Expected headers and at least one data row.");
        var headers = rows.getFirst().stream().map(String::trim).toList();
        for (int i = 1; i < rows.size(); i++) if (rows.get(i).size() != headers.size())
            throw new IllegalArgumentException("Column count differs at source data row " + i);
        return new Raw(headers, rows.subList(1, rows.size()));
    }
    public static double number(String input, Decimal decimal) {
        if(input==null||input.length()>256)throw new IllegalArgumentException("Numeric cell exceeds 256 characters.");
        String value = input.trim();
        // Leading decimal separators are accepted; thousands groups must contain exactly three digits.
        String separator = decimal == Decimal.DOT ? "." : ",";
        if (value.startsWith(separator)) value = "0" + value;
        else if (value.startsWith("+" + separator) || value.startsWith("-" + separator)) value = value.charAt(0) + "0" + value.substring(1);
        if (!(decimal == Decimal.DOT ? DOT : COMMA).matcher(value).matches())
            throw new IllegalArgumentException("Not a number in the selected decimal format: " + input);
        double result = Double.parseDouble(decimal == Decimal.DOT ? value.replace(",", "") : value.replace(".", "").replace(',', '.'));
        if (!Double.isFinite(result)) throw new IllegalArgumentException("Non-finite numeric input: " + input);
        return result;
    }
    public static boolean missing(String input, Settings settings) {
        String value = input.trim();
        if (value.isEmpty()) return true;
        String marker = settings.missingMarker(); if (marker.isEmpty()) return false;
        if (value.equals(marker)) return true;
        try { return number(value, settings.decimal()) == number(marker, settings.decimal()); }
        catch (IllegalArgumentException ignored) { return false; }
    }
    public static List<Column> infer(Raw raw, Settings settings) {
        List<Column> result = new ArrayList<>();
        for (int j = 0; j < raw.headers().size(); j++) {
            boolean numeric = true;
            for (var row : raw.rows()) if (!missing(row.get(j), settings)) {
                try { number(row.get(j), settings.decimal()); } catch (IllegalArgumentException error) { numeric = false; break; }
            }
            result.add(new Column(raw.headers().get(j), numeric ? Kind.NUMERIC : Kind.TEXT));
        }
        return result;
    }
    public static Prepared prepare(Raw raw, Settings settings, List<Column> definitions) {
        if (definitions.size() != raw.headers().size()) throw new IllegalArgumentException("Column definition count differs.");
        Set<String> names = new HashSet<>(); List<Integer> numeric = new ArrayList<>();
        for (int j = 0; j < definitions.size(); j++) {
            Column column = definitions.get(j);
            if (column.name().isEmpty() || !names.add(column.name())) throw new IllegalArgumentException("Column names must be nonempty and unique; correct duplicate headers in the preview.");
            if (column.kind() == Kind.NUMERIC) numeric.add(j);
        }
        if (numeric.isEmpty()) throw new IllegalArgumentException("Select at least one numeric column for analysis. Text columns remain available in the source view.");
        List<double[]> values = new ArrayList<>(); List<List<String>> kept = new ArrayList<>(); List<Integer> sourceRows = new ArrayList<>();
        int removed = 0, replaced = 0;
        for (int i = 0; i < raw.rows().size(); i++) {
            var row = raw.rows().get(i); boolean incomplete = false;
            for (int j = 0; j < definitions.size(); j++) if (definitions.get(j).kind() != Kind.IGNORE && missing(row.get(j), settings)) incomplete = true;
            double[] parsed = new double[numeric.size()];
            for (int k = 0; k < numeric.size(); k++) {
                int j = numeric.get(k);
                try { parsed[k] = missing(row.get(j), settings) ? Double.NaN : number(row.get(j), settings.decimal()); }
                catch (IllegalArgumentException error) { throw new IllegalArgumentException("Source data row " + (i + 1) + ", column " + definitions.get(j).name() + ": " + error.getMessage()); }
            }
            if (settings.treatment() == Missing.REMOVE_ROWS && incomplete) { removed++; continue; }
            values.add(parsed); kept.add(row); sourceRows.add(i + 1);
        }
        if (values.isEmpty()) throw new IllegalArgumentException("No rows remain after preparation.");
        for (int k = 0; k < numeric.size(); k++) {
            int count = 0; double mean = 0; boolean hasMissing = false;
            for (double[] row : values) if (Double.isNaN(row[k])) hasMissing = true; else { count++; mean += (row[k] - mean) / count; }
            if (hasMissing && settings.treatment() == Missing.REQUIRE_COMPLETE) throw new IllegalArgumentException("Missing numeric values in " + definitions.get(numeric.get(k)).name() + "; select mean replacement or removal of incomplete rows.");
            if (count == 0) throw new IllegalArgumentException("Column " + definitions.get(numeric.get(k)).name() + " has no observed numeric values; mean replacement is undefined.");
            if (!Double.isFinite(mean)) throw new IllegalArgumentException("Numeric range is too large for stable preparation.");
            for (double[] row : values) if (Double.isNaN(row[k])) { row[k] = mean; replaced++; }
        }
        var columnNames = numeric.stream().map(j -> definitions.get(j).name()).toList();
        double[][] matrix = values.toArray(double[][]::new); List<String> warnings = warnings(matrix, columnNames);
        if (definitions.stream().anyMatch(c -> c.kind() == Kind.TEXT)) warnings.add("Text columns are retained in Source data; they are not silently converted into numeric predictors.");
        return new Prepared(columnNames, matrix, kept, sourceRows, replaced, removed, warnings);
    }
    public static List<String> warnings(double[][] data, List<String> names) {
        List<String> warnings = new ArrayList<>(); List<double[]> basis = new ArrayList<>();
        for (int j = 0; j < names.size(); j++) {
            double min = data[0][j], max = min, scale = Math.abs(min);
            for (double[] row : data) { min = Math.min(min, row[j]); max = Math.max(max, row[j]); scale = Math.max(scale, Math.abs(row[j])); }
            if (min == max) { warnings.add("Constant numeric column: " + names.get(j) + ". Exclude it from variance-based models."); continue; }
            double[] vector = new double[data.length]; double mean = 0;
            for (int i = 0; i < vector.length; i++) { vector[i] = data[i][j] / scale; mean += (vector[i] - mean) / (i + 1); }
            double original = 0; for (int i = 0; i < vector.length; i++) { vector[i] -= mean; original = Math.hypot(original, vector[i]); }
            for (int pass = 0; pass < 2; pass++) for (double[] previous : basis) {
                double dot = 0; for (int i = 0; i < vector.length; i++) dot += vector[i] * previous[i];
                for (int i = 0; i < vector.length; i++) vector[i] -= dot * previous[i];
            }
            double norm = 0; for (double value : vector) norm = Math.hypot(norm, value);
            if (norm <= original * 1e-10) warnings.add("Linearly dependent numeric column: " + names.get(j) + ". Review the selected predictors before fitting.");
            else { for (int i = 0; i < vector.length; i++) vector[i] /= norm; basis.add(vector); }
        }
        return warnings;
    }
}
