package com.fincity.security.dto;


import com.fincity.saas.commons.model.dto.AbstractDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserInvite extends AbstractDTO<ULong, ULong> {

    private ULong clientId;
    private String emailId;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String userName;

    private String inviteCode;

    private ULong profileId;
    private ULong designationId;
}
