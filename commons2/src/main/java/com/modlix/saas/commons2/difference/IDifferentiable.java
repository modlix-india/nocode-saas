package com.modlix.saas.commons2.difference;

public interface IDifferentiable<T extends IDifferentiable<T>> {

	public T extractDifference(T inc);

	public T applyOverride(T override);

}
