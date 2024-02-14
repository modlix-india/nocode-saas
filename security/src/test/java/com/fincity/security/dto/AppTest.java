package com.fincity.security.dto;

import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;

import org.jooq.types.ULong;
import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.service.CacheObject;
import com.fincity.security.jooq.enums.SecurityAppAppAccessType;
import com.fincity.security.jooq.enums.SecurityAppAppType;

class AppTest {

    @Test
    public void testAppSerialization() {
        App app = new App();
        app.setAppName("Test App");
        app.setAppCode("Test App Code");
        app.setAppType(SecurityAppAppType.APP);
        app.setAppAccessType(SecurityAppAppAccessType.OWN);
        app.setThumbUrl("Test Thumb Url");
        app.setClientId(ULong.valueOf(1));

        CacheObject co = new CacheObject();
        co.setObject(app);

        assert encodeValue(co).capacity() > 0;
    }

    public ByteBuffer encodeValue(Object value) {

        if (value == null)
            return ByteBuffer.wrap(new byte[0]);

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ObjectOutputStream os = new ObjectOutputStream(bytes);
            os.writeObject(value);
            return ByteBuffer.wrap(bytes.toByteArray());
        } catch (IOException e) {

            e.printStackTrace();
            return null;
        }
    }
}
