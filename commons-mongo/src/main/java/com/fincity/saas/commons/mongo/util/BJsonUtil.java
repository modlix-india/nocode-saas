package com.fincity.saas.commons.mongo.util;

import java.math.BigDecimal;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.StreamSupport;

import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonNull;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.types.ObjectId;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class BJsonUtil {

	public static Document from(Set<String> idKeys, JsonObject job) {

		Document doc = new Document();

		for (Entry<String, BsonValue> entry : ((BsonDocument) fromElement(idKeys, job)).entrySet()) {

			doc.append(entry.getKey(), entry.getValue());
		}

		return doc;
	}

	public static BsonValue fromElement(Set<String> idKeys, JsonElement value) { // NOSONAR
		// It doesn't make sense to break this method.

		if (value.isJsonNull()) {

			return BsonNull.VALUE;
		} else if (value.isJsonObject()) {

			BsonDocument doc = new BsonDocument();

			for (Entry<String, JsonElement> entry : value.getAsJsonObject()
					.entrySet()) {

				BsonValue bValue;

				if (idKeys.contains(entry.getKey())) {
					bValue = entry.getValue().isJsonArray()
							? new BsonArray(StreamSupport.stream(entry.getValue().getAsJsonArray().spliterator(), false)
									.map(JsonElement::getAsString).map(ObjectId::new).map(BsonObjectId::new).toList())
							: new BsonObjectId(new ObjectId(entry.getValue().getAsString()));
				} else {
					bValue = fromElement(Set.of(), entry.getValue());
				}

				doc.append(entry.getKey(), bValue);
			}

			return doc;
		} else if (value.isJsonArray()) {

			JsonArray ja = value.getAsJsonArray();

			BsonArray ba = new BsonArray(ja.size());
			for (JsonElement je : ja)
				ba.add(fromElement(Set.of(), je));

			return ba;
		} else if (value.isJsonPrimitive()) {

			JsonPrimitive jp = value.getAsJsonPrimitive();

			if (jp.isBoolean())
				return BsonBoolean.valueOf(jp.getAsBoolean());

			if (jp.isString())
				return new BsonString(jp.getAsString());

			if (jp.isNumber())
				return number(jp);

			return new BsonString(jp.getAsString());
		}

		return new BsonString(value.getAsString());
	}

	/**
	 * The narrowest BSON number that holds this value exactly.
	 *
	 * Decided from the literal rather than from a double. The previous version
	 * computed {@code getAsNumber().doubleValue()} first and chose the BSON type by
	 * comparing that double against its own int and long values - so any integer
	 * beyond a double's exact range, 2^53, was already rounded before anything was
	 * decided, and 9007199254740993 was stored as 9007199254740992 with nothing
	 * reporting it. Every value written through the app data API passes here.
	 *
	 * An integral value still collapses to an integer type, including one written
	 * as 3.0. That is long-standing behaviour for every storage on the platform and
	 * is deliberately left alone: changing what a whole-numbered DOUBLE is stored as
	 * would change what reads back for 219 live storages, which is not something a
	 * precision fix should carry with it.
	 */
	private static BsonValue number(JsonPrimitive jp) {

		String text = jp.getAsString();

		try {
			BigDecimal value = new BigDecimal(text);

			if (value.stripTrailingZeros()
					.scale() <= 0) {

				long l = value.longValueExact();
				return l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE ? new BsonInt32((int) l) : new BsonInt64(l);
			}
		} catch (NumberFormatException | ArithmeticException e) {
			// Not a number this can hold exactly - too large for a long, or a
			// literal BigDecimal will not take. A double is the honest fallback.
		}

		return new BsonDouble(jp.getAsDouble());
	}

	private BJsonUtil() {

	}
}
