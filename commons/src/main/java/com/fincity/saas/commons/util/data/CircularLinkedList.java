package com.fincity.saas.commons.util.data;

import java.util.Collection;

public class CircularLinkedList<T> {

	private DoublePointerNode<T> head;
	private DoublePointerNode<T> tail;

	private int size = 0;

	@SafeVarargs
	public CircularLinkedList(T... elements) {

		for (T element : elements)
			this.add(element);
	}

	public CircularLinkedList(Collection<T> data) {

		for (T element : data)
			this.add(element);
	}

	public DoublePointerNode<T> getHead() {
		return this.head;
	}

	public DoublePointerNode<T> getTail() {
		return this.tail;
	}

	public int size() {
		return this.size;
	}

	public synchronized boolean add(T element) {

		if (head == null) {

			head = tail = new DoublePointerNode<>(element);
			head.next = tail;
			head.prev = tail;
		} else if (head == tail) {

			tail = new DoublePointerNode<>(head, element, head);
			head.next = tail;
			head.prev = tail;
		} else {

			DoublePointerNode<T> node = new DoublePointerNode<>(tail, element, head);
			tail.next = node;
			head.prev = node;
			tail = node;
		}
		this.size++;

		return true;
	}

	public Object[] toArray() {

		Object[] result = new Object[size];

		if (size == 0)
			return result;

		int i = 0;
		DoublePointerNode<T> s = this.head;
		do {

			result[i++] = s.item;
			s = s.next;
		} while (s != this.head);

		return result;
	}

	@SuppressWarnings("unchecked")
	public T[] toArray(T[] a) {
		if (a.length < size)
			a = (T[]) java.lang.reflect.Array.newInstance(a.getClass()
			        .getComponentType(), size);

		if (size == 0)
			return a;

		int i = 0;
		DoublePointerNode<T> s = this.head;
		do {

			a[i++] = s.item;
			s = s.next;
		} while (s != this.head);

		if (a.length > size)
			a[size] = null;

		return a;
	}

	public boolean addAll(Collection<? extends T> elements) {
		boolean result = false;
		for (T element : elements)
			result |= this.add(element);
		return result;
	}

	public void clear() {
		this.head = null;
		this.tail = null;
		this.size = 0;
	}
}
