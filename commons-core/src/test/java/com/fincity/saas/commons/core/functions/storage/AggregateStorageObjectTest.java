package com.fincity.saas.commons.core.functions.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.jackson.CommonsSerializationModule;
import com.fincity.saas.commons.model.AggregateQuery;
import com.fincity.saas.commons.model.DateBucketUnit;
import com.fincity.saas.commons.model.JoinType;
import com.fincity.saas.commons.model.condition.AggregateFunction;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * The arguments of CoreServices.Storage.Aggregate as the query the backend receives.
 *
 * Joins and subqueries were already understood by the MySQL backend but never reached
 * it from KIRun: the function dropped them, so a grouped read across a relation could
 * not be asked for at all.
 */
class AggregateStorageObjectTest {

    // The app's mapper reads an AbstractCondition through the commons module, as Spring's does.
    private final AggregateStorageObject fn = new AggregateStorageObject(
            null, new ObjectMapper().registerModule(new CommonsSerializationModule()), new Gson());

    private static Map<String, JsonElement> args(String json) {
        Map<String, JsonElement> m = new HashMap<>();
        // The defaults the signature fills in before internalExecute runs.
        m.put("filter", JsonParser.parseString("{}"));
        m.put("having", JsonParser.parseString("{}"));
        m.put("groupBy", JsonParser.parseString("[]"));
        m.put("aggregations", JsonParser.parseString("[]"));
        m.put("sort", JsonParser.parseString("[]"));
        m.put("page", JsonParser.parseString("0"));
        m.put("size", JsonParser.parseString("100"));
        m.put("count", JsonParser.parseString("false"));
        m.put("joins", JsonParser.parseString("[]"));
        m.put("subQueries", JsonParser.parseString("[]"));
        JsonParser.parseString(json).getAsJsonObject().entrySet().forEach(e -> m.put(e.getKey(), e.getValue()));
        return m;
    }

    @Test
    void noJoinsBuildsTheSameQueryAsBefore() {
        AggregateQuery q = fn.toQuery(args("{\"aggregations\": [{\"function\": \"COUNT\", \"alias\": \"n\"}]}"));
        assertNull(q.getJoins());
        assertNull(q.getSubQueries());
        assertEquals(AggregateFunction.COUNT, q.getAggregations().getFirst().getFunction());
    }

    @Test
    void joinsAndSubQueriesReachTheQuery() {
        AggregateQuery q = fn.toQuery(args("""
                {"joins": [{"relation": "ticketId", "alias": "t", "type": "INNER"}, {"relation": "stageId"}],
                 "subQueries": [{"storage": "task", "relation": "ticketId", "alias": "tk",
                                 "condition": {"field": "completed", "operator": "IS_FALSE"},
                                 "aggregations": [{"function": "MIN", "field": "dueDate", "alias": "mn"}]}],
                 "filter": {"field": "t.isActive", "operator": "IS_TRUE"},
                 "groupBy": [{"field": "t.assignedUserId", "alias": "u"}],
                 "aggregations": [{"function": "COUNT_DISTINCT", "field": "ticketId", "alias": "deals"}]}
                """));

        assertEquals(2, q.getJoins().size());
        assertEquals("ticketId", q.getJoins().get(0).getRelation());
        assertEquals("t", q.getJoins().get(0).getAlias());
        assertEquals(JoinType.INNER, q.getJoins().get(0).getType());
        assertEquals(JoinType.LEFT, q.getJoins().get(1).getType());
        assertEquals("stageId", q.getJoins().get(1).resolvedAlias());

        assertEquals(1, q.getSubQueries().size());
        assertEquals("task", q.getSubQueries().getFirst().getStorage());
        assertEquals("tk", q.getSubQueries().getFirst().getAlias());
        assertNotNull(q.getSubQueries().getFirst().getCondition());
        assertEquals(AggregateFunction.MIN, q.getSubQueries().getFirst().getAggregations().getFirst().getFunction());

        assertNotNull(q.getCondition());
        assertEquals("t.assignedUserId", q.getGroupBy().getFirst().getField());
        assertEquals(AggregateFunction.COUNT_DISTINCT, q.getAggregations().getFirst().getFunction());
        assertEquals("ticketId", q.getAggregations().getFirst().getField());
    }

    @Test
    void isoWeekBucketIsUnderstood() {
        AggregateQuery q = fn.toQuery(args("""
                {"groupBy": [{"field": "createdAt", "alias": "wk", "bucket": "iso_week", "timezone": "Asia/Kolkata"}],
                 "aggregations": [{"function": "count_distinct", "field": "clientId", "alias": "c"}]}
                """));

        assertEquals(DateBucketUnit.ISO_WEEK, q.getGroupBy().getFirst().getBucket());
        assertEquals("Asia/Kolkata", q.getGroupBy().getFirst().resolvedTimezone());
        assertEquals(AggregateFunction.COUNT_DISTINCT, q.getAggregations().getFirst().getFunction());
        assertTrue(q.getPageable().getPageSize() == 100);
    }
}
