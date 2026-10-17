package com.wikipane.semantic;

import com.wikipane.document.WikiDocument;
import com.wikipane.semantic.SemanticDocument.*;
import java.math.BigInteger;
import java.util.*;

/** One explicit Wiki method, retaining its complete source tables and variant scope. */
public final class Recipe {
    public static final class Ingredient {
        public final RichText name, quantity;
        public final Source source, quantitySource;
        public final Link target;
        public final WikiDocument.Image icon;
        public final BigInteger count;
        Ingredient(Cell name, Cell quantity, Link target, WikiDocument.Image icon) {
            this.name=name.value; this.quantity=quantity.value; source=name.source; quantitySource=quantity.source;
            this.target=target; this.icon=icon;
            String amount=quantity.value.text;
            count=amount.matches("(?:[1-9][0-9]*|[1-9][0-9]{0,2}(?:,[0-9]{3})+)")?new BigInteger(amount.replace(",","")):null;
        }
    }
    public static final class Skill {
        public final String name;
        public final Cell level, experience;
        public final Requirement requirement;
        Skill(String name,Cell level,Cell experience,Requirement requirement) {
            this.name=name;this.level=level;this.experience=experience;this.requirement=requirement;
        }
    }
    public final String id, variant, method;
    public String label() { return method.isEmpty()?variant:variant.isEmpty()?method:method+" / "+variant; }
    public final Table requirementsTable, materialsTable;
    public final List<Ingredient> inputs, outputs;
    public final List<Skill> skills;
    public final Map<String,Cell> conditions;
    Recipe(Table requirements, Table materials, List<Ingredient> inputs,List<Ingredient> outputs,
        List<Skill> skills,Map<String,Cell> conditions) {
        id=requirements.recipeGroup;variant=requirements.recipeVariant;method=requirements.recipeMethod;
        requirementsTable=requirements;materialsTable=materials;
        this.inputs=List.copyOf(inputs);this.outputs=List.copyOf(outputs);this.skills=List.copyOf(skills);
        this.conditions=Collections.unmodifiableMap(new LinkedHashMap<>(conditions));
    }
}
