package org.brightstats.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/** UTF-8 comma/semicolon/tab data import with quoted fields and numeric cells. */
public final class NumericCsv {
    private NumericCsv() {}
    public record Dataset(List<String> columns, double[][] values) {
        public Dataset {
            columns = List.copyOf(columns);
            values = Arrays.stream(values).map(double[]::clone).toArray(double[][]::new);
        }
        @Override public double[][] values() {
            return Arrays.stream(values).map(double[]::clone).toArray(double[][]::new);
        }
    }

    public static Dataset read(Path path) throws IOException { return parse(readText(path)); }

    public static String readText(Path path)throws IOException{if(Files.size(path)>64L*1024*1024)throw new IllegalArgumentException("CSV exceeds 64 MiB.");byte[] bytes;try(var stream=Files.newInputStream(path)){bytes=stream.readNBytes(64*1024*1024+1);}if(bytes.length>64*1024*1024)throw new IllegalArgumentException("CSV exceeds 64 MiB.");return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();}
    public static Dataset parse(String text) {
        if (text.startsWith("\ufeff")) text = text.substring(1);
        char delimiter = detectDelimiter(text);
        List<List<String>> records = records(text, delimiter);
        if (records.size() < 2) throw new IllegalArgumentException("Expected column names and at least one data row");
        List<String> names = records.getFirst().stream().map(String::trim).toList();
        if (names.stream().anyMatch(String::isEmpty) || new HashSet<>(names).size() != names.size()) {
            throw new IllegalArgumentException("Column names must be nonempty and unique");
        }
        if(names.size()>10000||records.size()-1>1000000||(long)(records.size()-1)*names.size()>5000000)throw new IllegalArgumentException("CSV dataset exceeds row/column/cell limits.");
        double[][] values = new double[records.size() - 1][names.size()];
        for (int i = 1; i < records.size(); i++) {
            List<String> row = records.get(i);
            if (row.size() != names.size()) throw new IllegalArgumentException("Column count differs at data row " + i);
            for (int j = 0; j < row.size(); j++) {
                String value = row.get(j).trim();
                try {
                    if(value.length()>256)throw new NumberFormatException();
                    values[i - 1][j] = Double.parseDouble(value);
                    if (!Double.isFinite(values[i - 1][j])) throw new NumberFormatException();
                } catch (NumberFormatException error) {
                    throw new IllegalArgumentException("Expected a finite number at row " + i + ", column " + names.get(j));
                }
            }
        }
        return new Dataset(names, values);
    }

    static char detectDelimiter(String text) {
        int[] counts = new int[3];
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if (value == '"') {
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') i++;
                else quoted = !quoted;
            } else if (!quoted) {
                if (value == '\n' || value == '\r') break;
                if (value == ',') counts[0]++;
                else if (value == ';') counts[1]++;
                else if (value == '\t') counts[2]++;
            }
        }
        int selected = 0;
        for (int i = 1; i < counts.length; i++) if (counts[i] > counts[selected]) selected = i;
        return new char[]{',', ';', '\t'}[selected];
    }

    static List<List<String>> records(String text, char delimiter) {
        if(text==null||text.length()>64*1024*1024)throw new IllegalArgumentException("CSV exceeds the input character limit.");
        long cells=0;
        List<List<String>> output = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean closedQuote = false;
        for (int i = 0; i < text.length(); i++) {
            if(field.length()>1000000||row.size()>10000||output.size()>1000001||cells>5010000)throw new IllegalArgumentException("CSV field/column/row/cell budget exceeded.");
            char value = text.charAt(i);
            if (quoted) {
                if (value == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else { quoted = false; closedQuote = true; }
                } else field.append(value);
                continue;
            }
            if (value == delimiter) {
                row.add(field.toString()); cells++; field.setLength(0); closedQuote = false;
            } else if (value == '\n' || value == '\r') {
                if (value == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                boolean explicitlyQuoted = closedQuote;
                row.add(field.toString()); cells++; field.setLength(0); closedQuote = false;
                if (explicitlyQuoted || !(row.size() == 1 && row.getFirst().isBlank())) output.add(List.copyOf(row));
                row.clear();
            } else if (closedQuote) {
                if (!Character.isWhitespace(value)) throw new IllegalArgumentException("Unexpected text after quoted field");
            } else if (value == '"') {
                if (!field.toString().isBlank()) throw new IllegalArgumentException("Unexpected quote in field");
                field.setLength(0); quoted = true;
            } else field.append(value);
        }
        if(field.length()>1000000||row.size()>10000||output.size()>1000001||cells>5010000)throw new IllegalArgumentException("CSV input budget exceeded.");
        if (quoted) throw new IllegalArgumentException("Unclosed quoted field");
        if (!row.isEmpty() || !field.isEmpty() || closedQuote) {
            row.add(field.toString()); cells++;
            if (closedQuote || !(row.size() == 1 && row.getFirst().isBlank())) output.add(List.copyOf(row));
        }
        return output;
    }
}
