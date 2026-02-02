package com.modlix.saas.commons2.util;

import org.springframework.http.HttpStatus;

import com.modlix.saas.commons2.exception.GenericException;

public class MathUtil {

	public static final float max(float... nums) {

		if (nums == null || nums.length == 0)
			throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, "No numbers found to find max.");

		float m = nums[0];

		for (int i = 1; i < nums.length; i++)
			if (m < nums[i])
				m = nums[i];

		return m;
	}

	public static final float min(float... nums) {

		if (nums == null || nums.length == 0)
			throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, "No numbers found to find max.");

		float m = nums[0];

		for (int i = 1; i < nums.length; i++)
			if (m > nums[i])
				m = nums[i];

		return m;
	}

	private MathUtil() {

	}
}
