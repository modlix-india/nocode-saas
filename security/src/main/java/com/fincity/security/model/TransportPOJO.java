package com.fincity.security.model;

import java.io.Serializable;
import java.util.List;

import com.fincity.security.enums.ClientLevelType;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class TransportPOJO implements Serializable {

	private static final long serialVersionUID = 7298239872398723987L;

	private String appCode;
	private String name;
	private String uniqueTransportCode;
	private String type;
	private String clientCode;
	private List<AppTransportRole> roles;
	private List<AppTransportPackage> packages;
	private List<AppTransportProperty> properties;
	private List<AppTransportRegAppAccess> appAccess;
	private List<AppTransportRegRoles> rolesAccess;
	private List<AppTransportRegPackages> packagesAccess;
	private List<AppTransportRegFiles> filesAccess;

	@Data
	@NoArgsConstructor
	@Accessors(chain = true)
	public static class AppTransportRole implements Serializable {
		private String roleName;
		private String roleDescription;
		private List<AppTransportPermission> permissions;
		// For when we have roles in roles
		private List<String> roleNames;
	}

	@Data
	@NoArgsConstructor
	@Accessors(chain = true)
	public static class AppTransportPermission implements Serializable {
		private String permissionName;
		private String permissionDescription;
	}

	@Data
	@Accessors(chain = true)
	@NoArgsConstructor
	public static class AppTransportPackage implements Serializable {
		private String packageName;
		private String packageCode;
		private String packageDescription;
		private List<String> roles;
	}

	@Data
	@NoArgsConstructor
	public static class AppTransportProperty implements Serializable {
		private String propertyName;
		private String propertyValue;
	}

	@Data
	@NoArgsConstructor
	public static class AppTransportRegAppAccess implements Serializable {
		private String appCode;

		private String clientType;
		private String businessType;
		private ClientLevelType level;
	}

	@Data
	@NoArgsConstructor
	public static class AppTransportRegPackages implements Serializable {
		private String packageName;

		private String clientType;
		private String businessType;
		private ClientLevelType level;
	}

	@Data
	@NoArgsConstructor
	public static class AppTransportRegRoles implements Serializable {
		private String roleName;

		private String clientType;
		private String businessType;
		private ClientLevelType level;
	}

	@Data
	@NoArgsConstructor
	public static class AppTransportRegFiles implements Serializable {

		private String resourceType;
		private String accessName;
		private boolean writeAccess;
		private String path;
		private boolean allowSubPathAccess;

		private String clientType;
		private String businessType;
		private ClientLevelType level;
	}
}
