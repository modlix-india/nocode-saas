package com.fincity.saas.ui.styles.expression;

import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

import com.fincity.saas.ui.styles.function.AbstractStyleFunction;
import com.fincity.saas.ui.styles.function.StyleFunctionAverage;
import com.fincity.saas.ui.styles.function.StyleFunctionDarken;
import com.fincity.saas.ui.styles.function.StyleFunctionDesaturate;
import com.fincity.saas.ui.styles.function.StyleFunctionDifference;
import com.fincity.saas.ui.styles.function.StyleFunctionExclusion;
import com.fincity.saas.ui.styles.function.StyleFunctionGreyscale;
import com.fincity.saas.ui.styles.function.StyleFunctionHSL;
import com.fincity.saas.ui.styles.function.StyleFunctionHSLA;
import com.fincity.saas.ui.styles.function.StyleFunctionHardlight;
import com.fincity.saas.ui.styles.function.StyleFunctionLighten;
import com.fincity.saas.ui.styles.function.StyleFunctionMultiply;
import com.fincity.saas.ui.styles.function.StyleFunctionNegation;
import com.fincity.saas.ui.styles.function.StyleFunctionOverlay;
import com.fincity.saas.ui.styles.function.StyleFunctionRGB;
import com.fincity.saas.ui.styles.function.StyleFunctionRGBA;
import com.fincity.saas.ui.styles.function.StyleFunctionSaturate;
import com.fincity.saas.ui.styles.function.StyleFunctionScreen;
import com.fincity.saas.ui.styles.function.StyleFunctionSoftlight;

import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class StyleExpressionEvaluator {

	private static final AbstractStyleFunction DEFAULT_FUNCTION =

	        new AbstractStyleFunction() {

		        @Override
		        protected String internalExecute(String t, String u) {
			        return new StringBuilder(t).append('(')
			                .append(u)
			                .append(')')
			                .toString();
		        }
	        };

	private static final Map<String, AbstractStyleFunction> FUNCTIONS = Map.ofEntries(

	        Map.entry("RGB", new StyleFunctionRGB()), Map.entry("RGBA", new StyleFunctionRGBA()),
	        Map.entry("HSL", new StyleFunctionHSL()), Map.entry("HSLA", new StyleFunctionHSLA()),
	        Map.entry("SATURATE", new StyleFunctionSaturate()), Map.entry("DESATURATE", new StyleFunctionDesaturate()),
	        Map.entry("LIGHTEN", new StyleFunctionLighten()), Map.entry("DARKEN", new StyleFunctionDarken()),
	        Map.entry("GREYSCALE", new StyleFunctionGreyscale()), Map.entry("MULTIPLY", new StyleFunctionMultiply()),
	        Map.entry("SCREEN", new StyleFunctionScreen()), Map.entry("OVERLAY", new StyleFunctionOverlay()),
	        Map.entry("SOFTLIGHT", new StyleFunctionSoftlight()), Map.entry("HARDLIGHT", new StyleFunctionHardlight()),
	        Map.entry("DIFFERENCE", new StyleFunctionDifference()),
	        Map.entry("EXCLUSION", new StyleFunctionExclusion()), Map.entry("AVERAGE", new StyleFunctionAverage()),
	        Map.entry("NEGATION", new StyleFunctionNegation()));

	@SafeVarargs
	public static Map<String, String> evaluateVaribles(final Map<String, String> eval,
	        final Map<String, String>... values) {

		if (eval == null)
			return new HashMap<>();

		return eval.entrySet()
		        .stream()
		        .map(e -> Tuples.of(e.getKey(), evaluateExpression(e.getValue(), eval, values)))
		        .collect(Collectors.toMap(Tuple2::getT1, Tuple2::getT2, (prev, next) -> next, HashMap::new));
	}

	@SafeVarargs
	private static String getValue(String variable, Map<String, String> current, Map<String, String>... values) {

		if (current != null && current.get(variable) != null && !current.get(variable)
		        .isBlank())
			return current.get(variable);

		if (values == null || values.length == 0)
			return null;

		for (Map<String, String> value : values) {
			if (value.get(variable) != null && !value.get(variable)
			        .isBlank())
				return value.get(variable);
		}

		return null;
	}

	@SafeVarargs
	public static String evaluateExpression(String expression, final Map<String, String> current,
	        final Map<String, String>... values) {

		if (expression == null || expression.isBlank())
			return expression;

		StringBuilder sb = new StringBuilder(expression);
		int index = -1;
		int loopCount = 0;

		while (((index = sb.indexOf(">", index + 1)) != -1) && loopCount < 300) {

			loopCount++;
			final int startIndex = sb.lastIndexOf("<", index);
			if (startIndex == -1)
				continue;

			final String x = sb.substring(startIndex + 1, index);
			final String value = getValue(x, current, values);
			if (value != null) {

				sb.replace(startIndex, index + 1, value);
				index = 0;
			}
		}

		return evaluateFunctions(sb.toString());
	}

	private static String evaluateFunctions(String expression) {

		int i = 0;
		Deque<Integer> nested = new ConcurrentLinkedDeque<>();

		Deque<StringBuilder> string = new ConcurrentLinkedDeque<>();
		string.push(new StringBuilder());
		String functionName;
		String prefix;

		while (i < expression.length()) {

			final char ch = expression.charAt(i);
			if (ch == '(') {
				string.push(new StringBuilder());
				nested.push(i);
			} else if (ch == ')') {
				final String param = string.pop()
				        .toString();

				AbstractStyleFunction f = DEFAULT_FUNCTION;
				functionName = "";
				prefix = "";
				if (!string.isEmpty()) {
					functionName = string.pop()
					        .toString();
					final int p = functionName.trim()
					        .lastIndexOf(' ');
					if (p != -1) {
						prefix = functionName.substring(0, p + 1);
						functionName = functionName.substring(p + 1);
					}
					f = FUNCTIONS.getOrDefault(functionName.toUpperCase(), DEFAULT_FUNCTION);
				}

				string.push(new StringBuilder(prefix).append(f.execute(functionName, param)));
			} else {
				string.peek()
				        .append(ch);
			}
			i++;
		}

		return string.stream()
		        .collect(Collectors.joining(""));
	}

	private StyleExpressionEvaluator() {
	}
}
