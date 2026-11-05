package org.brightstats.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.*;
import org.brightstats.data.DataPreparation.*;

/** Source values and preparation choices travel with projects and report snapshots. */
public final class ImportHistory {
    private static final Gson JSON = new Gson();
    private ImportHistory() {}
    public record History(int version, String sourceName, Raw raw, Settings settings, List<Column> definitions,
                          List<Integer> sourceRows, int replacedCells, int removedRows) {
        public History {
            if (version != 1 || sourceName == null || raw == null || settings == null || sourceRows == null)
                throw new IllegalArgumentException("Invalid or unsupported import history.");
            definitions = List.copyOf(definitions); sourceRows = List.copyOf(sourceRows);
        }
    }
    public static JsonObject capture(String sourceName, Raw raw, Settings settings, List<Column> definitions) {
        Prepared prepared = DataPreparation.prepare(raw, settings, definitions);
        return JSON.toJsonTree(new History(1, sourceName, raw, settings, definitions,
                prepared.sourceRows(), prepared.replacedCells(), prepared.removedRows())).getAsJsonObject();
    }
    public static History read(JsonObject document) {
        History history = JSON.fromJson(document, History.class);
        if(document.has("transformations"))DataTransforms.verifyLineage(document,history);
        Prepared prepared = DataPreparation.prepare(history.raw(), history.settings(), history.definitions());
        if (!prepared.sourceRows().equals(history.sourceRows()) || prepared.replacedCells() != history.replacedCells() || prepared.removedRows() != history.removedRows())
            throw new IllegalArgumentException("Import history does not reproduce its saved preparation summary.");
        return history;
    }
    public static void verify(JsonObject document, List<String> names, double[][] observations) {
        History history = read(document);
        Prepared prepared = DataPreparation.prepare(history.raw(), history.settings(), history.definitions());
        if (!prepared.columns().equals(names) || !Arrays.deepEquals(prepared.values(), observations))
            throw new IllegalArgumentException("Prepared observations differ from the saved source and import settings.");
    }
}
