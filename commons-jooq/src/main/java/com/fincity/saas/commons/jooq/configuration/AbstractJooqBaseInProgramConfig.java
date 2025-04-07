package com.fincity.saas.commons.jooq.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Database;
import org.jooq.meta.jaxb.ForcedType;
import org.jooq.meta.jaxb.Generate;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Target;
import org.springframework.context.annotation.Bean;

public abstract class AbstractJooqBaseInProgramConfig extends AbstractJooqBaseConfiguration {

    protected String driver = "com.mysql.cj.jdbc.Driver";

    protected String schema;

    protected String packageName;

    protected String directory;

    protected AbstractJooqBaseInProgramConfig(
            ObjectMapper objectMapper, String schema, String packageName, String directory) {
        super(objectMapper);
        this.schema = schema;
        this.packageName = packageName;
        this.directory = directory;
    }

    protected AbstractJooqBaseInProgramConfig(ObjectMapper objectMapper, String schema) {
        this(objectMapper, schema, null, null);
    }

    protected abstract ForcedType[] getForcedTypes();

    @Bean
    Void generateJooqClasses() throws Exception {
        Configuration configuration = new Configuration()
                .withJdbc(new Jdbc()
                        .withDriver(this.getDriver())
                        .withUrl(super.getUrl())
                        .withUser(super.getUsername())
                        .withPassword(super.getPassword()))
                .withGenerator(new Generator()
                        .withDatabase(new Database()
                                .withIncludes(getSchemaIncludes())
                                .withInputSchema(this.getSchema())
                                .withForcedTypes(this.getForcedTypes()))
                        .withGenerate(new Generate()
                                .withDeprecated(false)
                                .withRelations(true)
                                .withRecords(true)
                                .withImmutablePojos(false)
                                .withFluentSetters(true))
                        .withTarget(new Target()
                                .withPackageName(this.getPackageName(this.getSchema()))
                                .withDirectory(this.getDirectory())));

        GenerationTool.generate(configuration);
        return null;
    }

    private String getDriver() {
        return this.driver;
    }

    private String getSchema() {
        return this.schema;
    }

    private String getSchemaIncludes() {
        return this.schema + "_.*";
    }

    private String getPackageName(String schema) {
        return this.packageName == null || this.packageName.isBlank() || !this.packageName.contains(".")
                ? "com.fincity.saas." + schema + ".jooq"
                : this.packageName;
    }

    private String getDirectory() {
        return this.directory == null || this.directory.isBlank() ? "src/main/java" : this.directory;
    }
}
