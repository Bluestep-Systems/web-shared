package dev.bluestep.global.dto.tenant;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

import dev.bluestep.global.dto.tenantaccess.GlobalUserKey;
import dev.bluestep.global.dto.tenantaccess.TenantUserKey;

/**
 * Who created or last edited a tenant: a global user, or a user of one tenant. Never anything else —
 * the system acting on its own behalf is no actor at all, an absent component where this type is
 * held, not a third kind (web-global's changeset 023 retired the "System User" {@code (222222, -1)}).
 *
 * <p>On the wire the kind is the {@code "kind"} property: {@code "global-user"} or
 * {@code "tenant-user"}. An unknown kind fails binding, so a later kind arrives with a release.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
	@JsonSubTypes.Type(TenantActor.GlobalUser.class),
	@JsonSubTypes.Type(TenantActor.TenantUser.class)
})
public sealed interface TenantActor permits TenantActor.GlobalUser, TenantActor.TenantUser {

	/**
	 * A global user, by key; web-global holds the key to an account that exists.
	 *
	 * @param user the account
	 */
	@JsonTypeName("global-user")
	record GlobalUser(GlobalUserKey user) implements TenantActor {}

	/**
	 * A user of one tenant: the tenant, which web-global holds to one that exists, and the user's key
	 * inside it, which nothing outside that tenant's own database can check.
	 *
	 * @param tenant the tenant the user belongs to
	 * @param user   the user's key in that tenant's schema
	 */
	@JsonTypeName("tenant-user")
	record TenantUser(TenantKey tenant, TenantUserKey user) implements TenantActor {}
}
