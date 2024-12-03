package com.fincity.security.dto;

import java.time.LocalDateTime;

import org.jooq.types.ULong;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class SSLCertificate extends AbstractUpdatableDTO<ULong, ULong> {

	private static final long serialVersionUID = 1038080671735058921L;

	private ULong urlId;
	private String crt;
	private String crtChain;
	private String crtKey;
	private String csr;
	private String domains;
	private String organization;
	private LocalDateTime expiryDate;
	private String issuer;
	private Boolean current;
	private LocalDateTime autoRenewTill;
	private String crtKeyUpload;

	@JsonIgnore
	public String getCrtKey() {
		return this.crtKey;
	}
}