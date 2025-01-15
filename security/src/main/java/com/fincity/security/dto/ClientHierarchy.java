package com.fincity.security.dto;

import java.io.Serial;
import java.util.HashSet;
import java.util.Set;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ClientHierarchy extends AbstractUpdatableDTO<ULong, ULong> {

	@Serial
	private static final long serialVersionUID = 4415956656881849444L;

	private ULong clientId;
	private ULong manageClientLevel0;
	private ULong manageClientLevel1;
	private ULong manageClientLevel2;
	private ULong manageClientLevel3;

	public enum Level {
		SYSTEM, ZERO, ONE, TWO, THREE
	}

	public boolean isSystemClient() {
		return this.manageClientLevel0 == null
				&& this.manageClientLevel1 == null
				&& this.manageClientLevel2 == null
				&& this.manageClientLevel3 == null;
	}

	public boolean canAddLevel() {
		return this.manageClientLevel3 == null;
	}

	public boolean inClientHierarchy(ULong clientId) {
		return this.clientId.equals(clientId)
				|| this.manageClientLevel3.equals(clientId)
				|| this.manageClientLevel2.equals(clientId)
				|| this.manageClientLevel1.equals(clientId)
				|| this.manageClientLevel0.equals(clientId);
	}

	public boolean isManagedBy(ULong clientId) {
		if (clientId == null)
			return false;
		return this.inClientHierarchy(clientId);
	}

	// Always use this through service
	public ULong getManagingClient(Level level) {
		return switch (level) {
			case SYSTEM -> isSystemClient() ? this.clientId : null;
			case ZERO -> this.manageClientLevel0;
			case ONE -> this.manageClientLevel1;
			case TWO -> this.manageClientLevel2;
			case THREE -> this.manageClientLevel3;
		};
	}

	public Level getMaxLevel() {

		if (this.manageClientLevel3 != null)
			return Level.THREE;

		if (this.manageClientLevel2 != null)
			return Level.TWO;

		if (this.manageClientLevel1 != null)
			return Level.ONE;

		if (this.manageClientLevel0 != null)
			return Level.ZERO;

		return Level.SYSTEM;
	}

	public Set<ULong> getClientIds() {

		Set<ULong> clientIds = new HashSet<>();
		clientIds.add(this.clientId);

		clientIds.add(this.manageClientLevel0);
		clientIds.add(this.manageClientLevel1);
		clientIds.add(this.manageClientLevel2);
		clientIds.add(this.manageClientLevel3);

		clientIds.remove(null);
		return clientIds;
	}

	// 	These Methods are for JOOQ Compatibility.
	// 	Jooq uses {@code org.jooq.tools.StringUtils.toCamelCase()} to get getter and setter of a Entity

	public ULong getManageClientLevel_0() { //NOSONAR
		return manageClientLevel0;
	}

	public ClientHierarchy setManageClientLevel_0(ULong manageClientLevel0) { //NOSONAR
		this.manageClientLevel0 = manageClientLevel0;
		return this;
	}

	public ULong getManageClientLevel_1() { //NOSONAR
		return manageClientLevel1;
	}

	public ClientHierarchy setManageClientLevel_1(ULong manageClientLevel1) { //NOSONAR
		this.manageClientLevel1 = manageClientLevel1;
		return this;
	}

	public ULong getManageClientLevel_2() { //NOSONAR
		return manageClientLevel2;
	}

	public ClientHierarchy setManageClientLevel_2(ULong manageClientLevel2) { //NOSONAR
		this.manageClientLevel2 = manageClientLevel2;
		return this;
	}

	public ULong getManageClientLevel_3() { //NOSONAR
		return manageClientLevel3;
	}

	public ClientHierarchy setManageClientLevel_3(ULong manageClientLevel3) { //NOSONAR
		this.manageClientLevel3 = manageClientLevel3;
		return this;
	}
}
