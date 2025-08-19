package com.fincity.saas.entity.processor.analytics.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.experimental.UtilityClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@UtilityClass
public class ReactivePaginationUtil {

    private static final Logger logger = LoggerFactory.getLogger(ReactivePaginationUtil.class);
    private static final ConcurrentHashMap<String, MethodHandle> getterCache = new ConcurrentHashMap<>();

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    public static Mono<Integer> getLimit(Pageable pageable) {
        return Mono.fromCallable(() -> {
            int pageNumber = pageable.getPageNumber();
            int pageSize = pageable.getPageSize();
            // For the first page, the limit is the page size
            // For subsequent pages, the limit is the product of page number and page size
            return pageNumber == 0 ? pageSize : (pageNumber + 1) * pageSize;
        });
    }

    public static <T> Mono<Page<T>> toPage(List<T> list, Pageable pageable) {
        return Mono.fromCallable(() -> {
            if (CollectionUtils.isEmpty(list)) return Page.empty();

            Pageable effectivePageable = pageable;
            if (effectivePageable == null) effectivePageable = PageRequest.of(0, list.size());

            int start = Math.toIntExact(effectivePageable.getOffset());
            int end = Math.min((start + effectivePageable.getPageSize()), list.size());

            if (start >= list.size()) return Page.empty();

            List<T> sortedList = sortListInternal(list, effectivePageable);

            return new PageImpl<>(sortedList.subList(start, end), effectivePageable, sortedList.size());
        });
    }

    public static <T> Mono<Page<T>> toPage(List<T> list, Pageable pageable, long totalElements) {
        return Mono.fromCallable(() -> {
            if (pageable == null) return new PageImpl<>(list, PageRequest.of(0, list.size()), list.size());

            List<T> sortedList = sortListInternal(list, pageable);
            int start = Math.toIntExact(pageable.getOffset());
            int end = Math.min((start + pageable.getPageSize()), sortedList.size());

            List<T> pageContent = start >= sortedList.size() ? List.of() : sortedList.subList(start, end);
            return new PageImpl<>(pageContent, pageable, totalElements);
        });
    }

    private static <T> List<T> sortListInternal(List<T> list, Pageable pageable) {
        List<T> copy = new ArrayList<>(list);

        if (pageable.getSort().isUnsorted()) return copy;

        // get the sort field and direction from the Pageable
        List<Sort.Order> orders = pageable.getSort().get().toList();

        // create a Comparator using the createComparator() function
        Comparator<T> comparator = createComparator(orders);

        // sort the list using the Collections.sort() method
        copy.sort(comparator);

        return copy;
    }

    public static <T> Flux<T> sort(List<T> list, Pageable pageable) {
        if (pageable.getSort().isUnsorted()) return Flux.fromIterable(list);

        List<Sort.Order> orders = pageable.getSort().get().toList();

        Comparator<T> comparator = createComparator(orders);

        List<T> sortedList = new ArrayList<>(list);
        sortedList.sort(comparator);
        return Flux.fromIterable(sortedList);
    }

    public static Mono<Pageable> updatePageable(Pageable pageable, Map<String, String> sortMap, boolean keepAll) {
        return Mono.fromCallable(() -> {
            List<Sort.Order> orders = new ArrayList<>();
            for (Sort.Order order : pageable.getSort()) {
                String mappedSortField = sortMap.get(order.getProperty());
                if (mappedSortField != null) {
                    orders.add(new Sort.Order(order.getDirection(), mappedSortField));
                    if (!keepAll)
                        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
                } else if (keepAll) {
                    orders.add(order);
                }
            }
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
        });
    }

    public static Mono<Pageable> updatePageableJpaSort(Pageable pageable, Map<String, String> sortMap) {
        return Mono.fromCallable(() -> {
            Sort sort = Sort.unsorted();
            for (Sort.Order order : pageable.getSort()) {
                String mappedSortField = sortMap.get(order.getProperty());
                if (mappedSortField != null) sort = sort.and(JpaSort.unsafe(order.getDirection(), mappedSortField));
            }
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        });
    }

    public static <T> Comparator<T> createComparator(List<Sort.Order> orders) {
        if (CollectionUtils.isEmpty(orders)) return Comparator.comparing(Object::hashCode); // no-op comparator
        return (o1, o2) -> compareObjects(o1, o2, orders);
    }

    private static <T> int compareObjects(T o1, T o2, List<Sort.Order> orders) {
        for (Sort.Order order : orders) {
            try {
                int result = compareSingleField(o1, o2, order);
                if (result != 0) return result;
            } catch (Exception e) {
                logger.warn("Error comparing field {}, skipping", order.getProperty(), e);
            }
        }
        return 0;
    }

    private static <T> int compareSingleField(T o1, T o2, Sort.Order order) {
        Object value1 = getFlattenedValue(o1, order.getProperty());
        Object value2 = getFlattenedValue(o2, order.getProperty());

        int result = compareValues(value1, value2, order.getProperty());
        return order.getDirection() == Sort.Direction.ASC ? result : -result;
    }

    @SuppressWarnings("unchecked")
    private static int compareValues(Object value1, Object value2, String fieldName) {
        if (value1 == null && value2 == null) return 0;
        if (value1 == null) return -1;
        if (value2 == null) return 1;

        try {
            if (value1 instanceof String val1 && value2 instanceof String val2) return compareAsStrings(val1, val2);

            if (value1 instanceof Comparable && value2 instanceof Comparable)
                return ((Comparable<Object>) value1).compareTo(value2);

            return compareAsStrings(value1, value2);

        } catch (ClassCastException e) {
            logger.debug(
                    "Type mismatch comparing field {}: {} vs {} - using string comparison",
                    fieldName,
                    value1.getClass().getSimpleName(),
                    value2.getClass().getSimpleName());
            return compareAsStrings(value1, value2);
        } catch (Exception e) {
            logger.warn("Unable to compare values for field {}: {} vs {}", fieldName, value1, value2, e);
            return 0;
        }
    }

    private static int compareAsStrings(Object value1, Object value2) {
        return value1.toString().compareToIgnoreCase(value2.toString());
    }

    private static Object getFlattenedValue(Object obj, String sortField) {
        String[] keys = sortField.split("_");
        Object currentObj = obj;

        for (String key : keys) {
            if (currentObj == null) break;

            if (currentObj instanceof Map<?, ?>) {
                currentObj = ((Map<?, ?>) currentObj).get(key);
            } else {
                currentObj = getFieldOrNestedField(currentObj, key);
            }
        }

        return currentObj;
    }

    private static Object getFieldOrNestedField(Object obj, String fieldName) {

        try {
            MethodHandle getter = getGetter(obj.getClass(), fieldName);
            if (getter != null) return getter.invoke(obj);
        } catch (Throwable e) {
            logger.warn(
                    "Error invoking getter for field {} on {}",
                    fieldName,
                    obj.getClass().getSimpleName(),
                    e);
        }

        Field field = getField(obj.getClass(), fieldName);
        if (field != null) {
            field.setAccessible(true);
            try {
                return field.get(obj);
            } catch (IllegalAccessException e) {
                throw new IllegalArgumentException("Error accessing field: " + fieldName, e);
            }
        }
        return null;
    }

    private static MethodHandle getGetter(Class<?> clazz, String fieldName) {
        String cacheKey = clazz.getName() + "#" + fieldName;
        return getterCache.computeIfAbsent(cacheKey, key -> {
            String capitalized = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

            MethodHandle mh = tryUnReflect(clazz, "get" + capitalized);
            if (mh != null) return mh;

            return tryUnReflect(clazz, "is" + capitalized);
        });
    }

    private static MethodHandle tryUnReflect(Class<?> clazz, String getter) {
        try {
            return LOOKUP.unreflect(clazz.getMethod(getter));
        } catch (NoSuchMethodException | IllegalAccessException ignored) {
            return null;
        }
    }

    private static Field getField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            // If the field is not found in the current class, check the superclass
            if (clazz.getSuperclass() != null) return getField(clazz.getSuperclass(), fieldName);
            return null;
        }
    }
}
