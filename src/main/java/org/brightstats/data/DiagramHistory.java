package org.brightstats.data;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded immutable diagram snapshots; data matrices are deliberately outside the history. */
public final class DiagramHistory {
    private record Entry(String json,long bytes){}
    private final int maximumStates;private final long maximumBytes;private final List<Entry> states=new ArrayList<>();private int cursor=-1;private long bytes;
    public DiagramHistory(int maximumStates,long maximumBytes){if(maximumStates<2||maximumStates>1000||maximumBytes<1024||maximumBytes>256L*1024*1024)throw new IllegalArgumentException("Invalid history limits.");this.maximumStates=maximumStates;this.maximumBytes=maximumBytes;}
    public boolean record(JsonObject diagram){Objects.requireNonNull(diagram);String json=diagram.toString();long size=json.getBytes(StandardCharsets.UTF_8).length;if(size>maximumBytes)throw new IllegalArgumentException("Diagram exceeds the undo-history memory budget.");if(cursor>=0&&JsonParser.parseString(states.get(cursor).json()).equals(diagram))return false;while(states.size()>cursor+1)bytes-=states.removeLast().bytes();states.add(new Entry(json,size));bytes+=size;cursor=states.size()-1;while(states.size()>maximumStates||bytes>maximumBytes){bytes-=states.removeFirst().bytes();cursor--;}return true;}
    public void reset(JsonObject diagram){states.clear();cursor=-1;bytes=0;record(diagram);}
    public boolean canUndo(){return cursor>0;}public boolean canRedo(){return cursor>=0&&cursor<states.size()-1;}public long retainedBytes(){return bytes;}public int retainedStates(){return states.size();}
    public JsonObject current(){if(cursor<0)throw new IllegalStateException("History has no diagram.");return JsonParser.parseString(states.get(cursor).json()).getAsJsonObject();}
    public JsonObject undo(){if(!canUndo())throw new IllegalStateException("Nothing to undo.");cursor--;return current();}public JsonObject redo(){if(!canRedo())throw new IllegalStateException("Nothing to redo.");cursor++;return current();}
}
