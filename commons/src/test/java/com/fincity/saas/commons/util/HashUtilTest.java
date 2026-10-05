package com.fincity.saas.commons.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

class HashUtilTest {

	@Test
	void sha256HashIsTheKnownValue() {
		assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", HashUtil.sha256Hash("abc"));
	}

	@Test
	void sha256HashIsCorrectUnderConcurrentCalls() throws Exception {

		int threads = 16;
		int perThread = 2000;

		List<String> inputs = new ArrayList<>();
		List<String> expected = new ArrayList<>();
		for (int i = 0; i < 64; i++) {
			String input = "FIN/whatsapp/leadzump/avatars/" + "x".repeat(i * 7) + i;
			inputs.add(input);
			expected.add(HashUtil.sha256Hash(input));
		}

		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			List<Callable<Integer>> tasks = new ArrayList<>();
			for (int t = 0; t < threads; t++) {
				int offset = t;
				tasks.add(() -> {
					int wrong = 0;
					for (int i = 0; i < perThread; i++) {
						int k = (i + offset) % inputs.size();
						if (!expected.get(k).equals(HashUtil.sha256Hash(inputs.get(k))))
							wrong++;
					}
					return wrong;
				});
			}

			int wrong = 0;
			for (Future<Integer> f : pool.invokeAll(tasks))
				wrong += f.get();

			assertEquals(0, wrong);
		} finally {
			pool.shutdownNow();
		}
	}
}
