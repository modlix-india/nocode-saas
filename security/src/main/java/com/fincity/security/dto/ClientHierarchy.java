package com.fincity.security.dto;

import java.io.Serial;
import java.util.LinkedList;
import java.util.List;

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
		ZERO, ONE, TWO, THREE
	}

	public List<ULong> getClientLevels() {
		List<ULong> clientLevels = new LinkedList<>();
		clientLevels.add(clientId);
		clientLevels.add(manageClientLevel0);
		clientLevels.add(manageClientLevel1);
		clientLevels.add(manageClientLevel2);
		clientLevels.add(manageClientLevel3);
		return clientLevels;
	}

	public boolean isSystemClient() {
		return this.manageClientLevel0 == null && this.manageClientLevel1 == null
				&& this.manageClientLevel2 == null && this.manageClientLevel3 == null;
	}

	public boolean canAddLevel() {
		return this.manageClientLevel3 != null || this.manageClientLevel2 != null || this.manageClientLevel1 != null;
	}

	public boolean isManagedBy(ULong clientId) {
		return this.clientId.equals(clientId) || this.manageClientLevel0.equals(clientId) || this.manageClientLevel1.equals(clientId)
				|| this.manageClientLevel2.equals(clientId) || this.manageClientLevel3.equals(clientId);
	}

	public ULong getManagingClient(Level level) {
		return switch (level) {
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

		return null;
	}
}
