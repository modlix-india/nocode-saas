package com.fincity.saas.commons.core.service.connection.appdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.string.StringFormat;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLColumn;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLTypeMapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A storage written as a reference has to produce the same table as one written out,
 * because on Mongo the difference never mattered and authors have had no reason to
 * prefer either.
 */
class SchemaRefResolverTest {

    /** A repository of schemas held by name, which is all the resolver asks of one. */
    private static final class Repo implements ReactiveRepository<Schema> {

        private final Map<String, Schema> held = new HashMap<>();

        Repo put(String namespace, String name, Schema schema) {
            this.held.put(namespace + "." + name, schema.setNamespace(namespace).setName(name));
            return this;
        }

        @Override
        public Mono<Schema> find(String namespace, String name) {
            Schema s = this.held.get(namespace + "." + name);
            return s == null ? Mono.empty() : Mono.just(s);
        }

        @Override
        public Flux<String> filter(String name) {
            return Flux.fromIterable(this.held.keySet()).filter(k -> k.contains(name));
        }
    }

    private static Schema object(Map<String, Schema> props) {
        return new Schema().setType(Type.of(SchemaType.OBJECT)).setProperties(props);
    }

    private static Schema resolve(Schema schema, Repo repo) {
        return SchemaRefResolver.resolve(schema, repo).block();
    }

    private static MySQLColumn column(Schema resolved, String name) {
        return MySQLTypeMapper.columns(resolved).stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    @Nested
    @DisplayName("the storage itself is a reference")
    class TopLevel {

        @Test
        @DisplayName("a storage that is nothing but a ref still produces its columns")
        void bareRef() {
            Repo repo = new Repo()
                    .put(
                            "App",
                            "Customer",
                            object(Map.of(
                                    "name", Schema.ofString("name").setMaxLength(80),
                                    "age", Schema.ofInteger("age"))));

            Schema resolved = resolve(Schema.ofRef("App.Customer"), repo);

            // Unresolved, this schema has no properties at all, so the table would be a
            // primary key and nothing else - and that failure surfaces on the first
            // write rather than at publish, which is the worst possible place for it.
            assertEquals(2, MySQLTypeMapper.columns(resolved).size());
            assertEquals("VARCHAR(80)", column(resolved, "name").type());
            assertEquals("INT", column(resolved, "age").type());
        }

        @Test
        @DisplayName("properties declared next to the ref are kept, not replaced by it")
        void localPropertiesSurvive() {
            Repo repo = new Repo().put("App", "Base", object(Map.of("code", Schema.ofString("code").setMaxLength(10))));

            Schema local = Schema.ofRef("App.Base")
                    .setProperties(Map.of("extra", Schema.ofString("extra").setMaxLength(5)));

            Schema resolved = resolve(local, repo);

            // Dropping a declared property drops a column, and a missing column loses
            // every write to that field without reporting anything.
            assertNotNull(column(resolved, "code"));
            assertNotNull(column(resolved, "extra"));
        }

        @Test
        @DisplayName("required from both sides is honoured")
        void requiredIsMerged() {
            Repo repo = new Repo()
                    .put(
                            "App",
                            "Base",
                            object(Map.of("code", Schema.ofString("code").setMaxLength(10)))
                                    .setRequired(List.of("code")));

            Schema local = Schema.ofRef("App.Base")
                    .setProperties(Map.of("extra", Schema.ofString("extra").setMaxLength(5)))
                    .setRequired(List.of("extra"));

            Schema resolved = resolve(local, repo);

            assertEquals(false, column(resolved, "code").nullable());
            assertEquals(false, column(resolved, "extra").nullable());
        }

        @Test
        @DisplayName("a ref that points at nothing leaves the schema as written")
        void missingTarget() {
            Schema resolved = resolve(Schema.ofRef("App.Gone"), new Repo());

            // Refusing here would make publish the place a broken reference is
            // discovered. StorageService.validate is the place that already refuses one.
            assertNotNull(resolved);
            assertTrue(MySQLTypeMapper.columns(resolved).isEmpty());
        }
    }

    @Nested
    @DisplayName("a field is a reference")
    class Fields {

        @Test
        @DisplayName("a referenced scalar becomes the column that type deserves")
        void scalarField() {
            Repo repo = new Repo().put("App", "Email", Schema.ofString("Email").setMaxLength(120));

            Schema resolved = resolve(object(Map.of("contact", Schema.ofRef("App.Email"))), repo);

            // Unresolved this is a ref with no type, which the mapper can only call
            // JSON - a column you cannot index, join on, or compare.
            assertEquals("VARCHAR(120)", column(resolved, "contact").type());
        }

        @Test
        @DisplayName("a referenced date is a real date column")
        void dateField() {
            Repo repo = new Repo()
                    .put("App", "Stamp", Schema.ofString("Stamp").setFormat(StringFormat.DATETIME));

            Schema resolved = resolve(object(Map.of("createdAt", Schema.ofRef("App.Stamp"))), repo);

            assertEquals("DATETIME(3)", column(resolved, "createdAt").type());
        }

        @Test
        @DisplayName("a referenced object is a JSON column")
        void objectField() {
            Repo repo = new Repo()
                    .put(
                            "App",
                            "Address",
                            object(Map.of(
                                    "city", Schema.ofString("city").setMaxLength(60),
                                    "pin", Schema.ofString("pin").setMaxLength(6))));

            Schema resolved = resolve(object(Map.of("address", Schema.ofRef("App.Address"))), repo);

            MySQLColumn c = column(resolved, "address");
            assertEquals("JSON", c.type());
            assertTrue(c.note().contains("nested object"), c.note());
        }

        @Test
        @DisplayName("what the author wrote beside the ref wins over what the ref says")
        void localOverlayWins() {
            Repo repo = new Repo().put("App", "Code", Schema.ofString("Code").setMaxLength(40));

            Schema resolved = resolve(
                    object(Map.of("code", Schema.ofRef("App.Code").setMaxLength(12))), repo);

            // Narrowing a shared type at the point of use is a deliberate act. Taking
            // the shared width instead produces a column the author did not ask for,
            // and once rows exist in it that is not quietly fixable.
            assertEquals("VARCHAR(12)", column(resolved, "code").type());
        }

        @Test
        @DisplayName("a chain of references is followed to the end")
        void chainedRefs() {
            Repo repo = new Repo()
                    .put("App", "Outer", Schema.ofRef("App.Inner"))
                    .put("App", "Inner", Schema.ofString("Inner").setMaxLength(25));

            Schema resolved = resolve(object(Map.of("v", Schema.ofRef("App.Outer"))), repo);

            assertEquals("VARCHAR(25)", column(resolved, "v").type());
        }

        @Test
        @DisplayName("a self-referencing type becomes JSON rather than hanging")
        void cyclicRef() {
            Repo repo = new Repo();
            repo.put(
                    "App",
                    "Node",
                    object(Map.of(
                            "label", Schema.ofString("label").setMaxLength(30),
                            "child", Schema.ofRef("App.Node"))));

            Schema resolved = resolve(object(Map.of("tree", Schema.ofRef("App.Node"))), repo);

            // A tree whose children are trees is a legitimate thing to write, and JSON
            // is the honest column for it. Refusing would make a valid schema
            // unpublishable; looping would take the service down.
            MySQLColumn c = column(resolved, "tree");
            assertEquals("JSON", c.type());
        }

        @Test
        @DisplayName("a field with no ref is left exactly as it was")
        void untouched() {
            Schema plain = Schema.ofString("plain").setMaxLength(7);
            Schema resolved = resolve(object(Map.of("plain", plain)), new Repo());

            assertEquals("VARCHAR(7)", column(resolved, "plain").type());
        }

        @Test
        @DisplayName("a null repository returns the schema untouched rather than failing")
        void noRepository() {
            Schema schema = object(Map.of("a", Schema.ofInteger("a")));
            assertEquals(schema, SchemaRefResolver.resolve(schema, null).block());
        }

        @Test
        @DisplayName("a null schema resolves to nothing")
        void noSchema() {
            assertNull(SchemaRefResolver.resolve(null, new Repo()).block());
        }
    }
}
