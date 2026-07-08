package com.modlix.saas.adzump.platform;

/**
 * A discovered Facebook Page (Meta only; capabilities-gated). Google impls return an empty list.
 *
 * @param id   platform page id.
 * @param name page name.
 */
public record PageRef(String id, String name) {
}
