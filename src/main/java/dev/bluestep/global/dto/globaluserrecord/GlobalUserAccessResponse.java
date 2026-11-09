package dev.bluestep.global.dto.globaluserrecord;

import java.util.List;

import dev.bluestep.global.dto.tenantaccess.GlobalUserKey;

/**
 * What decides which knowledge bases and registries one global user may reach through the gateway (6.4.0).
 * Facts, combined at the gateway with the token's scopes and the target (b6p gateway contract §Auth "Access");
 * {@code disabled} comes first in every check.
 *
 * @param user                 the account asked about
 * @param disabled             switched off by the monolith: nothing is reachable, whatever else is true
 * @param superUser            every tenant, every knowledge base
 * @param knowledgeBaseManager manages (registers, edits, pushes to) global knowledge bases. Today = superUser:
 *                             G3 is supers-only and no per-KB manager exists; a per-KB rule later is an
 *                             additive field. Push to a global KB = {@code knowledgeBaseManager}
 * @param vouchedTenantIds     tenants vouched for right now (global.tenant_user_access, unexpired), as
 *                             {@code U<seqnum>}, sorted; NOT widened for a super
 * @param historyManager       may rewrite knowledge-base history (force push, ref deletion): the account holds an
 *                             unrevoked history-manager grant, given by another super, AND is a super now. Losing
 *                             super turns it false without touching the grant. The gateway also requires the
 *                             token's {@code kb:rewrite} scope ({@code TokenScope.KB_REWRITE}); like every fact
 *                             here it is reported as it stands, so {@code disabled} still comes first
 */
public record GlobalUserAccessResponse(GlobalUserKey user, boolean disabled, boolean superUser,
		boolean knowledgeBaseManager, List<String> vouchedTenantIds, boolean historyManager) {

	public GlobalUserAccessResponse {
		vouchedTenantIds = List.copyOf(vouchedTenantIds);
	}
}
