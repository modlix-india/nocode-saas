package com.fincity.security.dto;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

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
    private ULong reportingTo;

    // Display names resolved on read, not columns. An invite row is almost all
    // foreign keys - a listing that shows the raw ids tells the reader nothing.
    // Filled by UserInviteService.fillDetails; left null when the id is absent or
    // no longer resolves.
    private String clientName;
    private String profileName;
    private String appCode;
    private String designationName;
    private String reportingToName;
    private String createdByName;
}
