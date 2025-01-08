package com.fincity.security.dto;

import java.io.Serial;
import java.util.HashSet;
import java.util.Objects;
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

	public boolean isManagedBy(ULong clientId) {
		if (clientId == null)
			return false;

		return this.clientId.equals(clientId)
				|| Objects.equals(this.manageClientLevel0, clientId)
				|| Objects.equals(this.manageClientLevel1, clientId)
				|| Objects.equals(this.manageClientLevel2, clientId)
				|| Objects.equals(this.manageClientLevel3, clientId);
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

	public Set<ULong> getClientIds(Level level) {

		Set<ULong> clientIds = new HashSet<>();
		clientIds.add(this.clientId);

		if (Level.ZERO.equals(level)) {
			clientIds.add(this.manageClientLevel0);
		}

		if (Level.ONE.equals(level)) {
			clientIds.add(this.manageClientLevel0);
			clientIds.add(this.manageClientLevel1);
		}
		if (Level.TWO.equals(level)) {
			clientIds.add(this.manageClientLevel0);
			clientIds.add(this.manageClientLevel1);
			clientIds.add(this.manageClientLevel2);
		}

		if (Level.THREE.equals(level)) {
			clientIds.add(this.manageClientLevel0);
			clientIds.add(this.manageClientLevel1);
			clientIds.add(this.manageClientLevel2);
			clientIds.add(this.manageClientLevel3);
		}

		clientIds.remove(null);
		return clientIds;
	}
}
