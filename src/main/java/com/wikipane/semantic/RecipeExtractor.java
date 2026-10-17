package com.wikipane.semantic;

import com.wikipane.api.WikiUrls;
import com.wikipane.semantic.SemanticDocument.*;
import java.util.*;
import java.util.regex.*;

/** Recognizes explicit recipe-table boundaries, never article names or ordinary links. */
public final class RecipeExtractor {
    private RecipeExtractor() {}
    public static List<Recipe> extract(List<Node> nodes) {
        Map<String,List<Table>> groups=new LinkedHashMap<>();
        for(Node node:nodes) if(node.table!=null&&!node.table.recipeGroup.isEmpty())
            groups.computeIfAbsent(node.table.recipeGroup,k->new ArrayList<>()).add(node.table);
        List<Recipe> result=new ArrayList<>();
        for(List<Table> tables:groups.values()) {
            if(tables.size()!=2) continue;
            Table requirements=tables.get(0),materials=tables.get(1);
            if(!requirements.title.equals("Requirements")||!materials.title.equals("Materials")
                ||!requirements.recipeVariant.equals(materials.recipeVariant)||!requirements.recipeMethod.equals(materials.recipeMethod)) continue;
            Recipe recipe=parse(requirements,materials);
            if(recipe!=null) result.add(recipe);
        }
        return List.copyOf(result);
    }
    private static Recipe parse(Table requirements,Table materials) {
        if(!requirements.kind.isEmpty()||!materials.kind.isEmpty()||materials.rows.isEmpty())return null;
        Row header=materials.rows.get(0);
        Cell item=header.cells.stream().filter(c->c.header&&c.value.text.equals("Item")).findFirst().orElse(null);
        Cell quantity=header.cells.stream().filter(c->c.header&&c.value.text.equals("Quantity")).findFirst().orElse(null);
        if(item==null||quantity==null||header.cells.stream().anyMatch(c -> !c.header||!Set.of("Item","Quantity","Cost").contains(c.value.text)))return null;
        List<Recipe.Ingredient> inputs=new ArrayList<>(),outputs=new ArrayList<>();
        boolean output=false;int separators=0;
        for(int i=1;i<materials.rows.size();i++) {
            Row row=materials.rows.get(i);
            if(row.cells.stream().anyMatch(c->c.header&&c.value.text.equals("Total cost"))) {output=true;separators++;continue;}
            if(row.cells.stream().anyMatch(c->c.header)) {
                if(row.cells.stream().filter(c->c.header).anyMatch(c -> !Set.of("Profit","Profit after GE Tax").contains(c.value.text))) return null;
                continue;
            }
            if(!row.carried.isEmpty()||row.cells.stream().anyMatch(c->!c.nested.isEmpty()))return null;
            Cell amount=at(row,quantity.column);
            Cell name=null,illustration=null;
            for(Cell c:row.cells) if(c.column>=item.column&&c.column<item.column+item.colspan) {
                if(c.illustration!=null&&!c.illustrationTarget.isEmpty())illustration=c;
                else if(!c.value.text.isBlank()) {if(name!=null)return null;name=c;}
            }
            if(name==null||amount==null||amount.value.text.isBlank())return null;
            List<Link> links=new ArrayList<>();
            for(Link link:name.value.links) if(articleLink(link)&&!link.url.contains("#cite_"))links.add(link);
            Link target=links.size()==1?links.get(0):null;
            if(links.isEmpty()&&illustration!=null) {
                String title=WikiUrls.title(WikiUrls.resolve(illustration.illustrationTarget));
                if(name.value.text.equals(title))target=new Link(name.value.text,illustration.illustrationTarget,true);
            }
            if(target==null)return null; // Ambiguous alternatives keep their full generic representation.
            Recipe.Ingredient ingredient=new Recipe.Ingredient(name,amount,target,illustration==null?null:illustration.illustration);
            (output?outputs:inputs).add(ingredient);
        }
        if(separators!=1||inputs.isEmpty()||outputs.isEmpty())return null;
        List<Recipe.Skill> skills=new ArrayList<>();Map<String,Cell> conditions=new LinkedHashMap<>();
        int skillColumn=-1,levelColumn=-1,xpColumn=-1;
        for(Row row:requirements.rows) {
            if(row.cells.stream().anyMatch(c -> !c.nested.isEmpty())) return null;
            if(row.cells.stream().filter(c -> c.header).anyMatch(c -> !Set.of("Skill","Level","XP","Members","Ticks","Tools","Facilities").contains(c.value.text))) return null;
            boolean recognized=row.cells.stream().allMatch(c -> c.header);
            Set<Cell> accounted=Collections.newSetFromMap(new IdentityHashMap<>());
            for(Cell c:row.cells)if(c.header) {
                if(c.value.text.equals("Skill"))skillColumn=c.column;
                if(c.value.text.equals("Level"))levelColumn=c.column;
                if(c.value.text.equals("XP"))xpColumn=c.column;
            }
            Cell skill=at(row,skillColumn),level=at(row,levelColumn),xp=at(row,xpColumn);
            if(skill!=null&&!skill.header&&level!=null&&!level.header&&xp!=null&&!xp.header) {
                String name=skill.value.text;
                Matcher match=Pattern.compile("^([0-9]{1,2})(?: \\((boostable|not boostable)\\))?$").matcher(level.value.text);
                Requirement requirement;
                if(match.matches()) {
                    Requirement.Boost boost="boostable".equals(match.group(2))?Requirement.Boost.ALLOWED:
                        "not boostable".equals(match.group(2))?Requirement.Boost.NOT_ALLOWED:Requirement.Boost.UNSPECIFIED;
                    requirement=new Requirement(Requirement.Kind.SKILL,name,"",Integer.valueOf(match.group(1)),null,boost,
                        SemanticExtractor.rich(com.wikipane.document.WikiDocumentParser.escape(name)+" "+level.value.html),List.of());
                } else requirement=new Requirement(Requirement.Kind.OTHER,name,"",null,null,Requirement.Boost.UNSPECIFIED,level.value,List.of());
                skills.add(new Recipe.Skill(name,level,xp,requirement));recognized=true;
                accounted.add(skill);accounted.add(level);accounted.add(xp);
            }
            for(int i=0;i+1<row.cells.size();i++) {
                Cell label=row.cells.get(i),value=row.cells.get(i+1);
                if(label.header&&!value.header&&Set.of("Tools","Facilities","Members","Ticks").contains(label.value.text)) {
                    if(conditions.putIfAbsent(label.value.text,value)!=null)return null;
                    recognized=true;accounted.add(value);
                }
            }
            if(!recognized||row.cells.stream().anyMatch(c -> !c.header&&!c.value.text.isBlank()&&!accounted.contains(c)))return null;
        }
        return new Recipe(requirements,materials,inputs,outputs,skills,conditions);
    }
    private static Cell at(Row row,int column) {
        if(column<0)return null;
        return row.cells.stream().filter(c->c.column<=column&&column<c.column+c.colspan).findFirst().orElse(null);
    }
    private static boolean articleLink(Link link) {
        String title=WikiUrls.title(WikiUrls.resolve(link.url));
        return link.internal&&title!=null&&!title.contains(":");
    }
}
