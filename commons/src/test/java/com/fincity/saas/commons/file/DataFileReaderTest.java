package com.fincity.saas.commons.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.multipart.FilePart;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.DataFileType;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DataFileReaderTest {

	private static final Duration NO_SPIN = Duration.ofSeconds(10);

	@Test
	void readsEveryObjectFromAJsonArray() throws IOException {

		List<Map<String, Object>> objects = readAll(
				"[{\"a\":1},{\"a\":2},{\"a\":3}]", DataFileType.JSON);

		assertEquals(3, objects.size());
		assertEquals(1, ((Number) objects.get(0).get("a")).intValue());
		assertEquals(3, ((Number) objects.get(2).get("a")).intValue());
	}

	/**
	 * A \" inside a string used to flip the in-string flag, so every brace after it in that string
	 * was counted and the object was cut in the wrong place.
	 */
	@Test
	void keepsEscapedQuotesAndBracesInsideStrings() throws IOException {

		String body = "a \\\" quote, a { brace and a } brace";

		List<Map<String, Object>> objects = readAll(
				"[{\"body\":\"" + body + "\",\"n\":1},{\"body\":\"second\",\"n\":2}]", DataFileType.JSON);

		assertEquals(2, objects.size());
		assertEquals("a \" quote, a { brace and a } brace", objects.get(0).get("body"));
		assertEquals("second", objects.get(1).get("body"));
	}

	@Test
	void readsNestedObjectsWholeAndNotAtTheFirstInnerBrace() throws IOException {

		List<Map<String, Object>> objects = readAll(
				"[{\"outer\":{\"inner\":{\"deep\":\"v\"}},\"n\":1}]", DataFileType.JSON);

		assertEquals(1, objects.size());
		assertEquals(1, ((Number) objects.get(0).get("n")).intValue());
	}

	@Test
	void readsJsonLinesOneObjectPerLine() throws IOException {

		List<Map<String, Object>> objects = readAll(
				"{\"a\":1}\n{\"a\":2, \"b\":\"has a \\\" and a { in it\"}\n", DataFileType.JSONL);

		assertEquals(2, objects.size());
		assertEquals("has a \" and a { in it", objects.get(1).get("b"));
	}

	/**
	 * An unclosed object used to leave the read loop spinning on -1 for ever, so the request never
	 * came back at all.
	 */
	@Test
	void failsOnATruncatedObjectInsteadOfSpinning() {

		assertTimeoutPreemptively(NO_SPIN, () -> assertThrows(
				GenericException.class, () -> readAll("[{\"a\":1},{\"a\":", DataFileType.JSON)));
	}

	@Test
	void failsOnATruncatedObjectWithAnUnclosedString() {

		assertTimeoutPreemptively(NO_SPIN, () -> assertThrows(
				GenericException.class, () -> readAll("[{\"a\":\"unterminated", DataFileType.JSON)));
	}

	@Test
	void endsQuietlyOnAnEmptyJsonArray() throws IOException {

		assertEquals(List.of(), readAll("[]", DataFileType.JSON));
	}

	@Test
	void readsNothingFromAFlatTypeAsAnObject() throws IOException {

		try (DataFileReader reader = new DataFileReader(filePart("a,b\n1,2\n"), DataFileType.CSV)) {
			assertNull(reader.readObject());
		}
	}

	private List<Map<String, Object>> readAll(String content, DataFileType fileType) throws IOException {

		List<Map<String, Object>> objects = new ArrayList<>();

		try (DataFileReader reader = new DataFileReader(filePart(content), fileType)) {
			Map<String, Object> object;
			while ((object = reader.readObject()) != null)
				objects.add(object);
		}

		return objects;
	}

	private FilePart filePart(String content) {

		byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

		return new FilePart() {

			@Override
			public String filename() {
				return "test.json";
			}

			@Override
			public Mono<Void> transferTo(Path dest) {
				return Mono.empty();
			}

			@Override
			public String name() {
				return "file";
			}

			@Override
			public HttpHeaders headers() {
				return new HttpHeaders();
			}

			@Override
			public Flux<DataBuffer> content() {
				// One buffer per byte, so a reader that assumes a whole object arrives in one
				// buffer is caught here rather than in production.
				return Flux.range(0, bytes.length)
						.map(i -> {
							DataBuffer buffer = DefaultDataBufferFactory.sharedInstance.allocateBuffer(1);
							buffer.write(bytes[i]);
							return buffer;
						});
			}
		};
	}
}
