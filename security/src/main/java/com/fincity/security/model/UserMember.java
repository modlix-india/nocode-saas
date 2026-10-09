package com.fincity.security.model;

import java.io.Serial;
import java.io.Serializable;

import org.jooq.types.ULong;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * One member of the caller's own company, as any signed in member of that company may see them.
 *
 * <p>Deliberately not {@link com.fincity.security.dto.User}. This is what an assignee picker or an
 * activity line needs (a name and an id to build the avatar URL from) and nothing else: no email, no
 * phone, no password or lock fields. {@code userName} is left out when it is an email address, so
 * the email cannot leak through it either.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserMember implements Serializable {

    @Serial
    private static final long serialVersionUID = 4180239561320584417L;

    private ULong id;
    private String firstName;
    private String lastName;
    private String userName;
    private String name;
    private SecurityUserStatusCode statusCode;
    private String clientCode;
}
