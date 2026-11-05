package org.brightstats.model;
import java.util.*;
import org.brightstats.model.TypedDiagram.*;
/** Group measurement indicators beside their component; layout never rewrites statistical properties. */
public final class DiagramLayouts {
    private DiagramLayouts(){}
    public static Document arrange(Document source){
        List<Node> components=source.nodes().stream().filter(node->node.type().latent()).toList();Map<String,Node> positions=new LinkedHashMap<>();Set<String> assigned=new HashSet<>();double cursor=100;
        for(int group=0;group<components.size();group++){Node component=components.get(group);List<Node> indicators=new ArrayList<>();for(var link:source.links())if(link.type().measurement()&&(link.source().equals(component.id())||link.target().equals(component.id()))){var node=source.node(link.source().equals(component.id())?link.target():link.source());if(node.type().observed()&&assigned.add(node.id()))indicators.add(node);}double height=Math.max(180,100+70*(indicators.size()-1));positions.put(component.id(),component.at(260+160*(group%2),cursor+height/2));for(int row=0;row<indicators.size();row++){var node=indicators.get(row);positions.put(node.id(),node.at(740,cursor+50+70*row));}cursor+=height+80;}
        int free=0;for(var node:source.nodes())if(node.type().observed()&&!positions.containsKey(node.id())){positions.put(node.id(),node.at(180+260*(free%3),cursor+80+100*(free/3)));free++;}
        if(components.isEmpty()){var predictors=source.nodes().stream().filter(node->node.type()==ElementType.REGRESSION_INDEPENDENT_VARIABLE).toList();for(int i=0;i<predictors.size();i++)positions.put(predictors.get(i).id(),predictors.get(i).at(180,140+100*i));for(var node:source.nodes())if(node.type()==ElementType.REGRESSION_DEPENDENT_VARIABLE)positions.put(node.id(),node.at(740,Math.max(180,140+50*(predictors.size()-1))));else if(node.type()==ElementType.REGRESSION_INTERCEPT)positions.put(node.id(),node.at(450,70));}
        for(var error:source.nodes())if(error.type()==ElementType.CBSEM_ERROR_TERM){var binding=source.links().stream().filter(link->(link.type()==ElementType.CBSEM_CONNECTION_BETWEEN_MV_AND_ERROR_TERM||link.type()==ElementType.CBSEM_CONNECTION_BETWEEN_LV_AND_ERROR_TERM)&&(link.source().equals(error.id())||link.target().equals(error.id()))).findFirst();if(binding.isPresent()){String peer=binding.get().source().equals(error.id())?binding.get().target():binding.get().source();var owner=positions.getOrDefault(peer,source.node(peer));positions.put(error.id(),error.at(owner.x()+(owner.type().observed()?170:-180),owner.y()));}}
        return new Document(source.version(),source.family(),source.root(),source.nodes().stream().map(node->positions.getOrDefault(node.id(),node)).toList(),source.links(),source.annotations());
    }
}
