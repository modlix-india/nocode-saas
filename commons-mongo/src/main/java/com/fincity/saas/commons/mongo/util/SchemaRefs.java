package com.fincity.saas.commons.mongo.util;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.fincity.saas.commons.util.StringUtil;

/**
 * Finds the schema documents a raw KIRun schema definition refers to.
 *
 * Extracted so that schemas and storages are walked by the same code. They hold the
 * same kind of definition and the same kind of reference, and two walks that are meant
 * to agree but are written twice do not stay in agreement - here the consequence would
 * be a storage whose table is not rebuilt when the schema it points at changes, which
 * is silent until a write hits a column that is the wrong type.
 */
public final class SchemaRefs {

    private static final String REF = "ref";

    private SchemaRefs() {
    }

    /**
     * Every other schema this definition refers to.
     *
     * A definition is raw KIRun Schema JSON, and a reference is a plain {@code ref}
     * string, never {@code $ref} - {@code $defs} is the only dollar prefixed key KIRun
     * has. Refs nest wherever a sub schema is allowed: {@code properties},
     * {@code patternProperties}, {@code propertyNames}, {@code anyOf}/{@code allOf}/
     * {@code oneOf}, {@code not}, {@code contains}, {@code $defs}, {@code items},
     * {@code additionalProperties} and {@code additionalItems}.
     *
     * The walk is deliberately blind rather than field by field. {@code items} and the
     * two additional* fields are Gson union types whose adapters accept a bare schema,
     * a wrapped {@code singleSchema}/{@code tupleSchema}/{@code schemaValue}, a raw
     * array or a bare boolean, and only ever write the bare form back - so stored
     * definitions carry a mix of shapes. A recursive walk keyed on {@code ref} is
     * immune to all of that; a typed reader would have to reimplement both adapters.
     */
    public static Set<String> collect(Object definition) {

        Set<String> refs = new LinkedHashSet<>();
        if (definition != null) collectInto(definition, refs);
        return refs;
    }

    /**
     * Every document that would change if {@code target} changed, including
     * {@code target} itself.
     *
     * Reverse reachability, not forward: the question is not what a schema points at
     * but what points at it. A storage references {@code App.Order}, which references
     * {@code App.Money}, so editing {@code App.Money} changes the table behind that
     * storage although nothing about the storage or {@code App.Order} was touched.
     * Following only direct references would find nothing and rebuild nothing.
     *
     * @param refsByDocument each document's name against the documents it refers to
     */
    public static Set<String> referencingClosure(Map<String, Set<String>> refsByDocument, String target) {

        Map<String, Set<String>> referencedBy = new java.util.HashMap<>();
        refsByDocument.forEach((name, refs) -> refs.forEach(
                r -> referencedBy.computeIfAbsent(r, k -> new LinkedHashSet<>()).add(name)));

        Set<String> closure = new LinkedHashSet<>();
        java.util.Deque<String> pending = new java.util.ArrayDeque<>();
        closure.add(target);
        pending.add(target);

        // A schema may reference itself, directly or round a loop, which is
        // legitimate for a recursive type. The visited set is what keeps that from
        // being an infinite walk.
        while (!pending.isEmpty())
            for (String parent : referencedBy.getOrDefault(pending.poll(), Set.of()))
                if (closure.add(parent)) pending.add(parent);

        return closure;
    }

    private static void collectInto(Object node, Set<String> refs) {

        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (REF.equals(entry.getKey()) && entry.getValue() instanceof String ref) addSchemaNames(ref, refs);
                else collectInto(entry.getValue(), refs);
            }
        } else if (node instanceof Iterable<?> iterable) {
            for (Object value : iterable) collectInto(value, refs);
        }
    }

    /**
     * Turns a ref string into the schema document names it could point at.
     *
     * A schema document is stored under {@code namespace + "." + name}, and
     * ReactiveSchemaUtil resolves an external ref by cutting at the first {@code /} and
     * splitting the head at its <b>last</b> dot - so the head is already exactly the
     * document name. The dot trimmed prefixes are emitted too because refs written as
     * {@code Model.UserSegment._id} exist in the wild; only the prefix that names a
     * real schema will match anything. Refs starting with {@code #} are internal to the
     * schema and are not edges at all.
     */
    private static void addSchemaNames(String ref, Set<String> refs) {

        if (StringUtil.safeIsBlank(ref) || ref.charAt(0) == '#') return;

        int slash = ref.indexOf('/');
        String name = slash < 0 ? ref : ref.substring(0, slash);

        // A document name is namespace + "." + name, so it always has a dot. Stop once
        // there is none left, which is the bare namespace.
        while (name.indexOf('.') >= 0) {
            refs.add(name);
            name = name.substring(0, name.lastIndexOf('.'));
        }
    }
}
