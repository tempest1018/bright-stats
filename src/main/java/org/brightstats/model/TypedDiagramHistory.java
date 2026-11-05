package org.brightstats.model;
import org.brightstats.data.DiagramHistory;
import org.brightstats.model.TypedDiagram.Document;
/** Geometry, bindings, parameters and annotations undo together; observations stay outside history. */
public final class TypedDiagramHistory {
    private final DiagramHistory history;
    public TypedDiagramHistory(Document initial){this(initial,100,8L*1024*1024);}
    public TypedDiagramHistory(Document initial,int states,long bytes){history=new DiagramHistory(states,bytes);history.reset(initial.json());}
    public boolean record(Document document){return history.record(document.json());}
    public Document current(){return TypedDiagram.parse(history.current().toString());}
    public boolean canUndo(){return history.canUndo();}public boolean canRedo(){return history.canRedo();}
    public Document undo(){return TypedDiagram.parse(history.undo().toString());}public Document redo(){return TypedDiagram.parse(history.redo().toString());}
    public long retainedBytes(){return history.retainedBytes();}
}
