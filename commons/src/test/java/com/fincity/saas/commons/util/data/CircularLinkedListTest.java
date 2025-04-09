package com.fincity.saas.commons.util.data;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CircularLinkedListTest {

    @Test
    void test() {

        CircularLinkedList<Integer> list = new CircularLinkedList<>(1, 2, 3, 4);

        Integer[] arr = list.toArray(new Integer[list.size()]);

        assertArrayEquals(new Integer[] {1, 2, 3, 4}, arr);
    }

    @Test
    void circularTest() {

        CircularLinkedList<Integer> list = new CircularLinkedList<>(1);
        assertEquals(1, list.getHead().item);
        assertEquals(1, list.getTail().item);
        assertEquals(1, list.getHead().getNext().item);

        list = new CircularLinkedList<>();
        assertNull(list.getHead());

        list = new CircularLinkedList<>(1, 2, 3);
        var node = list.getHead();

        assertEquals(1, node.item);
        node = node.next;
        assertEquals(2, node.item);
        node = node.next;
        assertEquals(3, node.item);
        node = node.next;
        assertEquals(1, node.item);
    }
}
