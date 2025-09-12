package com.modlix.saas.commons2.util.data;

public class DoublePointerNode<E> {
	
	E item;
	DoublePointerNode<E> next;
	DoublePointerNode<E> prev;

	public DoublePointerNode(E element) {
		this.item = element;
	}

	public DoublePointerNode(DoublePointerNode<E> prev, E element, DoublePointerNode<E> next) {
		this.item = element;
		this.next = next;
		this.prev = prev;
	}

	public E getItem() {
		return item;
	}

	public DoublePointerNode<E> getNext() {
		return this.next;
	}

	public DoublePointerNode<E> getPrevious() {
		return this.prev;
	}
}
