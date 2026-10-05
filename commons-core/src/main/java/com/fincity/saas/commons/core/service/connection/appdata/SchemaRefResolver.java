package com.fincity.saas.commons.core.service.connection.appdata;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.reactive.ReactiveSchemaUtil;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.kirun.engine.util.string.StringUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Follows a storage schema's references until the thing it actually describes is in
 * hand, which is what the DDL has to be generated from.
 *
 * A storage may be written as nothing but {@code {"ref": "App.Customer"}}, and
 * individual fields may be refs too. Mongo never had to care: it stores whatever
 * arrives. MySQL does, because an unresolved ref declares no type and no properties,
 * and a table built from it is a table with a primary key and nothing else - which
 * fails silently on the first write rather than loudly at publish.
 *
 * {@link com.fincity.saas.commons.core.service.StorageService#validate} already
 * resolves the TOP-LEVEL ref before validating. That is not enough here, for two
 * reasons: it does not descend into the properties, and its result is not what
 * {@code getSchema} hands back at read time.
 */
public final class SchemaRefResolver {

    /**
     * How deep a chain of references is followed.
     *
     * A ref may point at a schema that is itself a ref, and nothing stops that chain
     * being a loop. KIRun's own resolver guards a single chain, but it does not know
     * that this code then walks into the resolved schema's properties and resolves
     * those too, which is where a self-referencing type becomes infinite.
     */
    public static final int MAX_DEPTH = 10;

    private SchemaRefResolver() {
    }

    /**
     * The storage's schema with every reference it needs for DDL followed.
     *
     * Two levels, and only two, because that is all the DDL can use. The storage
     * itself must resolve to an object or there are no columns, and each of its
     * properties must resolve far enough to know its type. Anything below a property
     * is going into a JSON column whatever it turns out to be, so following it further
     * would be work in aid of a decision already made.
     */
    public static Mono<Schema> resolve(Schema storageSchema, ReactiveRepository<Schema> repo) {

        if (storageSchema == null) return Mono.empty();
        if (repo == null) return Mono.just(storageSchema);

        return follow(storageSchema, repo, 0, new LinkedHashSet<>())
                .map(resolved -> resolved == storageSchema ? resolved : mergeProperties(storageSchema, resolved))
                .flatMap(resolved -> properties(resolved, repo));
    }

    /** Resolve each property one level, so every column has a type to be mapped from. */
    private static Mono<Schema> properties(Schema schema, ReactiveRepository<Schema> repo) {

        Map<String, Schema> props = schema.getProperties();
        if (props == null || props.isEmpty()) return Mono.just(schema);

        return Flux.fromIterable(props.entrySet())
                .concatMap(e -> property(e.getValue(), repo).map(r -> Map.entry(e.getKey(), r)))
                .collectList()
                .map(entries -> {
                    Map<String, Schema> out = new LinkedHashMap<>();
                    entries.forEach(e -> out.put(e.getKey(), e.getValue()));
                    return new Schema(schema).setProperties(out);
                });
    }

    private static Mono<Schema> property(Schema prop, ReactiveRepository<Schema> repo) {

        // A null property schema describes nothing, so there is no column to make from
        // it and nothing is lost by dropping it.
        if (prop == null) return Mono.empty();

        if (StringUtil.isNullOrBlank(prop.getRef())) return Mono.just(prop);

        return follow(prop, repo, 0, new LinkedHashSet<>()).map(r -> r == prop ? r : overlay(prop, r));
    }

    /**
     * Follow one chain of references to its end.
     *
     * Returning the schema UNCHANGED when the chain cannot be followed - because it is
     * too deep, cyclic, or points at something that is not there - is deliberate. An
     * unresolved ref still has a column to produce, and the type mapper turns it into
     * JSON with a note saying why. A recursive type, say a tree node whose children are
     * tree nodes, is a real thing an author may write, and JSON is the honest column
     * for it. Failing the publish instead would make a legitimate schema unpublishable.
     */
    private static Mono<Schema> follow(Schema schema, ReactiveRepository<Schema> repo, int depth, Set<String> seen) {

        String ref = schema.getRef();
        if (StringUtil.isNullOrBlank(ref)) return Mono.just(schema);

        if (depth >= MAX_DEPTH || !seen.add(ref)) return Mono.just(schema);

        return ReactiveSchemaUtil.getSchemaFromRef(schema, repo, ref)
                .flatMap(resolved -> StringUtil.isNullOrBlank(resolved.getRef()) || resolved == schema
                        ? Mono.just(resolved)
                        : follow(resolved, repo, depth + 1, seen))
                // A ref that resolves to nothing, or throws because the target is gone,
                // leaves the field as written. Publishing a table is not the moment to
                // discover a broken reference, and StorageService.validate is the place
                // that already refuses one.
                .onErrorReturn(schema)
                .defaultIfEmpty(schema);
    }

    /**
     * What the author wrote alongside the ref wins over what the ref resolved to.
     *
     * Only the three things the DDL reads are overlaid. Writing
     * {@code {"ref": "App.Code", "maxLength": 12}} is a narrowing of a shared type, and
     * silently taking the shared type's width instead would produce a column the author
     * did not ask for - which, once rows exist in it, is not a mistake that can be
     * quietly corrected later.
     */
    static Schema overlay(Schema local, Schema resolved) {

        Schema out = new Schema(resolved);

        if (local.getType() != null) out.setType(local.getType());
        if (local.getFormat() != null) out.setFormat(local.getFormat());
        if (local.getMaxLength() != null) out.setMaxLength(local.getMaxLength());

        // The field's name in the storage is the column name, so the referenced
        // schema's own name must not travel with it.
        out.setName(local.getName());

        return out;
    }

    /**
     * Merge a locally declared property set over a resolved one.
     *
     * Kept separate from {@link #overlay} because the risk is the opposite way round:
     * here a property the author declared and the referenced type does not have is a
     * column that would otherwise never be created, and a missing column loses writes.
     */
    static Schema mergeProperties(Schema local, Schema resolved) {

        if (local.getProperties() == null || local.getProperties().isEmpty()) return resolved;

        Map<String, Schema> merged = new LinkedHashMap<>();
        if (resolved.getProperties() != null) merged.putAll(resolved.getProperties());
        merged.putAll(local.getProperties());

        Set<String> required = new LinkedHashSet<>();
        if (resolved.getRequired() != null) required.addAll(resolved.getRequired());
        if (local.getRequired() != null) required.addAll(local.getRequired());

        return new Schema(resolved).setProperties(merged).setRequired(List.copyOf(required));
    }
}
