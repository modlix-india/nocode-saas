package com.modlix.saas.commons2.util;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

public class Tuples {

    public static <T1, T2> Tuple2<T1, T2> of(T1 t1, T2 t2) {
        return new Tuple2<>(t1, t2);
    }

    public static <T1, T2, T3> Tuple3<T1, T2, T3> of(T1 t1, T2 t2, T3 t3) {
        return new Tuple3<>(t1, t2, t3);
    }

    public static <T1, T2, T3, T4> Tuple4<T1, T2, T3, T4> of(T1 t1, T2 t2, T3 t3, T4 t4) {
        return new Tuple4<>(t1, t2, t3, t4);
    }

    public static <T1, T2, T3, T4, T5> Tuple5<T1, T2, T3, T4, T5> of(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5) {
        return new Tuple5<>(t1, t2, t3, t4, t5);
    }

    public static <T1, T2, T3, T4, T5, T6> Tuple6<T1, T2, T3, T4, T5, T6> of(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6) {
        return new Tuple6<>(t1, t2, t3, t4, t5, t6);
    }

    public static <T1, T2, T3, T4, T5, T6, T7> Tuple7<T1, T2, T3, T4, T5, T6, T7> of(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5,
            T6 t6, T7 t7) {
        return new Tuple7<>(t1, t2, t3, t4, t5, t6, t7);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8> Tuple8<T1, T2, T3, T4, T5, T6, T7, T8> of(T1 t1, T2 t2, T3 t3, T4 t4,
            T5 t5,
            T6 t6, T7 t7, T8 t8) {
        return new Tuple8<>(t1, t2, t3, t4, t5, t6, t7, t8);
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromArray(Object[] array) {
        if (array == null || array.length < 2 || array.length > 8) {
            throw new IllegalArgumentException("Array must have between 2 and 8 elements");
        }

        switch (array.length) {
            case 2:
                return (T) of(array[0], array[1]);
            case 3:
                return (T) of(array[0], array[1], array[2]);
            case 4:
                return (T) of(array[0], array[1], array[2], array[3]);
            case 5:
                return (T) of(array[0], array[1], array[2], array[3], array[4]);
            case 6:
                return (T) of(array[0], array[1], array[2], array[3], array[4], array[5]);
            case 7:
                return (T) of(array[0], array[1], array[2], array[3], array[4], array[5], array[6]);
            case 8:
                return (T) of(array[0], array[1], array[2], array[3], array[4], array[5], array[6], array[7]);
            default:
                throw new IllegalArgumentException("Unsupported array length: " + array.length);
        }
    }

    public static interface ITuple {
        Object[] toArray();
    }

    @AllArgsConstructor
    @Getter
    @EqualsAndHashCode
    public static class Tuple2<T1, T2> implements ITuple {
        private final T1 t1;
        private final T2 t2;

        public Object[] toArray() {
            return new Object[] { t1, t2 };
        }
    }

    @AllArgsConstructor
    @Getter
    @EqualsAndHashCode
    public static class Tuple3<T1, T2, T3> implements ITuple {
        private final T1 t1;
        private final T2 t2;
        private final T3 t3;

        public Object[] toArray() {
            return new Object[] { t1, t2, t3 };
        }
    }

    @AllArgsConstructor
    @Getter
    @EqualsAndHashCode
    public static class Tuple4<T1, T2, T3, T4> implements ITuple {
        private final T1 t1;
        private final T2 t2;
        private final T3 t3;
        private final T4 t4;

        public Object[] toArray() {
            return new Object[] { t1, t2, t3, t4 };
        }
    }

    @AllArgsConstructor
    @Getter
    @EqualsAndHashCode
    public static class Tuple5<T1, T2, T3, T4, T5> implements ITuple {
        private final T1 t1;
        private final T2 t2;
        private final T3 t3;
        private final T4 t4;
        private final T5 t5;

        public Object[] toArray() {
            return new Object[] { t1, t2, t3, t4, t5 };
        }
    }

    @AllArgsConstructor
    @Getter
    @EqualsAndHashCode
    public static class Tuple6<T1, T2, T3, T4, T5, T6> implements ITuple {
        private final T1 t1;
        private final T2 t2;
        private final T3 t3;
        private final T4 t4;
        private final T5 t5;
        private final T6 t6;

        public Object[] toArray() {
            return new Object[] { t1, t2, t3, t4, t5, t6 };
        }
    }

    @AllArgsConstructor
    @Getter
    @EqualsAndHashCode
    public static class Tuple7<T1, T2, T3, T4, T5, T6, T7> implements ITuple {
        private final T1 t1;
        private final T2 t2;
        private final T3 t3;
        private final T4 t4;
        private final T5 t5;
        private final T6 t6;
        private final T7 t7;

        public Object[] toArray() {
            return new Object[] { t1, t2, t3, t4, t5, t6, t7 };
        }
    }

    @AllArgsConstructor
    @Getter
    @EqualsAndHashCode
    public static class Tuple8<T1, T2, T3, T4, T5, T6, T7, T8> implements ITuple {
        private final T1 t1;
        private final T2 t2;
        private final T3 t3;
        private final T4 t4;
        private final T5 t5;
        private final T6 t6;
        private final T7 t7;
        private final T8 t8;

        public Object[] toArray() {
            return new Object[] { t1, t2, t3, t4, t5, t6, t7, t8 };
        }
    }

}
