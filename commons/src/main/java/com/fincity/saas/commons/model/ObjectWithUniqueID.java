package com.fincity.saas.commons.model;

import java.io.Serializable;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Map;

import com.fincity.saas.commons.util.StringUtil;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class ObjectWithUniqueID<D> implements Serializable {
    private static final Logger logger = LoggerFactory.getLogger(ObjectWithUniqueID.class);

    private static final long serialVersionUID = -3804873941419862809L;

    private String uniqueId;
    private D object; // NOSONAR
    private Map<String, String> headers;

    public ObjectWithUniqueID(D object) {
        this.object = object;
    }

    public ObjectWithUniqueID(D object, String uniqueId) {
        this.object = object;
        this.uniqueId = uniqueId;
    }

    public String getUniqueId() {

        if (this.uniqueId != null)
            return this.uniqueId;

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(object.toString().getBytes());
            BigInteger no = new BigInteger(1, messageDigest);
            this.uniqueId = no.toString(16);
        } catch (Exception ex) {
            logger.error("Error while generating unique id for object : {}", StringUtil.trimToSize(object, 500), ex);
            this.uniqueId = Integer.toHexString(object.toString().hashCode());
        }

        this.uniqueId = '"' + this.uniqueId + '"';

        return this.uniqueId;
    }
}
