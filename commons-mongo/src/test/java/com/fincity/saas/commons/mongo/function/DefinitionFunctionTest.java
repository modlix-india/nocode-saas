package com.fincity.saas.commons.mongo.function;

import org.junit.jupiter.api.Test;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.nocode.kirun.engine.json.schema.type.Type.SchemaTypeAdapter;
import com.fincity.nocode.kirun.engine.model.FunctionDefinition;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveFunctionRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveSchemaRepository;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DefinitionFunctionTest {

    @Test
    void test() {

        Gson gson = new GsonBuilder().registerTypeAdapter(Type.class, new SchemaTypeAdapter())
                .create();

        var first = new DefinitionFunction(gson.fromJson(
                """
                        {
                        		        "name": "First",
                        		        "namespace": "Internal",
                        		        "events": {
                        		            "output": {
                        		                "name": "output",
                        		                "parameters": { "aresult": { "name": "aresult", "type": "INTEGER" } }
                        		            }
                        		        },
                        		        "steps": {
                        		            "exSecond": {
                        		                "statementName": "exSecond",
                        		                "name": "Second",
                        		                "namespace": "Internal",
                        		                "parameterMap": {
                        		                    "value" : { "one" : { "key": "one", "type": "VALUE", "value": 2 } }
                        		                }
                        		            },
                        		            "exThird": {
                        		                "statementName": "exThird",
                        		                "name": "Third",
                        		                "namespace": "Internal",
                        		                "parameterMap": {
                        		                    "value" : { "one" : { "key": "one", "type": "VALUE", "value": 3 } }
                        		                }
                        		            },
                        		            "genOutput": {
                        		                "statementName": "genOutput",
                        		                "namespace": "System",
                        		                "name": "GenerateEvent",
                        		                "parameterMap": {
                        		                    "eventName": { "one": { "key": "one", "type": "VALUE", "value": "output" } },
                        		                    "results": {
                        		                        "one": {
                        		                            "key": "one",
                        		                            "type": "VALUE",
                        		                            "value": {
                        		                                "name": "aresult",
                        		                                "value": { "isExpression": true, "value": "Steps.exSecond.output.result + Steps.exThird.output.result" }
                        		                            }
                        		                        }
                        		                    }
                        		                }
                        		            }
                        		        }
                        		    }""",
                FunctionDefinition.class), null);

        var second = new DefinitionFunction(
                gson.fromJson(
                        """
                                {
                                		            "name": "Second",
                                		            "namespace": "Internal",
                                		            "parameters": {
                                		                "value": { "parameterName": "value", "schema": { "name": "INTEGER", "type": "INTEGER" } } },
                                		            "events": {
                                		                "output": {
                                		                    "name": "output",
                                		                    "parameters": { "result": { "name": "result", "type": "INTEGER" } }
                                		                }
                                		            },
                                		            "steps": {
                                		                "genOutput": {
                                		                    "statementName": "genOutput",
                                		                    "namespace": "System",
                                		                    "name": "GenerateEvent",
                                		                    "parameterMap": {
                                		                        "eventName": { "one": { "key": "one", "type": "VALUE", "value": "output" } },
                                		                        "results": {
                                		                            "one": {
                                		                                "key": "one",
                                		                                "type": "VALUE",
                                		                                "value": {
                                		                                    "name": "result",
                                		                                    "value": { "isExpression": true, "value": "Arguments.value * 2" }
                                		                                }
                                		                            }
                                		                        }
                                		                    }
                                		                }
                                		            }
                                		        }""",
                        FunctionDefinition.class),
                null);

        var third = new DefinitionFunction(gson.fromJson(
                """
                        {
                        		            "name": "Third",
                        		            "namespace": "Internal",
                        		            "parameters": {
                        		                "value": { "parameterName": "value", "schema": { "name": "INTEGER", "type": "INTEGER" } } },
                        		            "events": {
                        		                "output": {
                        		                    "name": "output",
                        		                    "parameters": { "result": { "name": "result", "type": "INTEGER" } }
                        		                }
                        		            },
                        		            "steps": {
                        		                "genOutput": {
                        		                    "statementName": "genOutput",
                        		                    "namespace": "System",
                        		                    "name": "GenerateEvent",
                        		                    "parameterMap": {
                        		                        "eventName": { "one": { "key": "one", "type": "VALUE", "value": "output" } },
                        		                        "results": {
                        		                            "one": {
                        		                                "key": "one",
                        		                                "type": "VALUE",
                        		                                "value": {
                        		                                    "name": "result",
                        		                                    "value": { "isExpression": true, "value": "Arguments.value * 3" }
                        		                                }
                        		                            }
                        		                        }
                        		                    }
                        		                }
                        		            }
                        		        }""",
                FunctionDefinition.class), null);

        class InternalRepository implements ReactiveRepository<ReactiveFunction> {

            @Override
            public Mono<ReactiveFunction> find(String namespace, String name) {

                if (!"Internal".equals(namespace))
                    return Mono.empty();

                if ("Third".equals(name))
                    return Mono.just(third);
                if ("Second".equals(name))
                    return Mono.just(second);

                return Mono.empty();
            }

            @Override
            public Flux<String> filter(String name) {
                return Flux.empty();
            }
        }

        var repo = new ReactiveHybridRepository<>(new KIRunReactiveFunctionRepository(), new InternalRepository());

        var results = first
                .execute(new ReactiveFunctionExecutionParameters(repo, new KIRunReactiveSchemaRepository(), "Testing"))
                .map(FunctionOutput::next).map(e -> e.getResult().get("aresult").getAsInt());

        StepVerifier.create(results).expectNext(13).verifyComplete();
    }

}
