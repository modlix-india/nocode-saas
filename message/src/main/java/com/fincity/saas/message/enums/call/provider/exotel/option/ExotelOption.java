package com.fincity.saas.message.enums.call.provider.exotel.option;

import jakarta.validation.constraints.NotNull;
import org.jooq.EnumType;

public interface ExotelOption<E extends ExotelOption<E>> extends EnumType {

    static <E extends Enum<E> & ExotelOption<E>> E getDefault(Class<E> enumType) {
        for (E e : enumType.getEnumConstants()) if (e.isDefault()) return e;
        return null;
    }

    static <E extends Enum<E> & ExotelOption<E>> E getByName(Class<E> enumType, String name) {
        if (name == null) return getDefault(enumType);

        for (E e : enumType.getEnumConstants()) if (e.getExotelName().equals(name)) return e;

        return getDefault(enumType);
    }

    @NotNull String getExotelName();

    @NotNull String getName();

    boolean isDefault();
}
