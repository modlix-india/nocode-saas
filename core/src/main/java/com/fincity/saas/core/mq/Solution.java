package com.fincity.saas.core.mq;

import java.util.*;

public class Solution {

	// Simple Pair record to make the code runnable
	public record Pair<K, V>(K x, V y) {}

	private static int getMaxWater(List<List<Integer>> grid) {

		Map<Pair<Integer, Integer>, Integer> cor0distance = firs(grid);
		Integer maxDistanceResult = -1;

		for (Map.Entry<Pair<Integer, Integer>, Integer> entry : cor0distance.entrySet()) {
			if (entry.getValue() > maxDistanceResult) {
				maxDistanceResult = entry.getValue();
			}
		}

		return maxDistanceResult;
	}

	private static Map<Pair<Integer, Integer>, Integer> firs(List<List<Integer>> grid) {

		List<Pair<Integer, Integer>> cor = new ArrayList<>();   // Land coordinates
		List<Pair<Integer, Integer>> cor0 = new ArrayList<>();  // Water coordinates

		int i = 0;
		for (List<Integer> row : grid) {
			for (int j = 0; j <= row.size() - 1; j++) {
				if (row.get(j) == 1) {
					cor.add(new Pair<>(i, j));
				}

				if (row.get(j) == 0) {
					cor0.add(new Pair<>(i, j));
				}
			}
			i++;
		}

		Map<Pair<Integer, Integer>, Integer> cor0distance = new HashMap<>();

		for (Pair<Integer, Integer> co0 : cor0) {
			for (Pair<Integer, Integer> co : cor) {
				Integer dist = Math.abs(co0.x() - co.x()) + Math.abs(co0.y() - co.y());

				if (cor0distance.get(co0) == null) {
					cor0distance.put(co0, dist);
					continue;
				}

				if (cor0distance.get(co0) != null && cor0distance.get(co0) > dist) {
					cor0distance.put(co0, dist);
					continue;
				}
			}
		}

		return cor0distance;
	}

	public static void main(String[] args) {
		// Example Input
		List<List<Integer>> input = List.of(
				List.of(1, 0, 1),
				List.of(0, 0, 0),
				List.of(1, 0, 1)
		);

		System.out.println("Max Distance: " + getMaxWater(input));
	}
}
