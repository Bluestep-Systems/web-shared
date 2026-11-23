package dev.bluestep.global.dto.stepup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Asking web-global for a step-up ticket on a provider's fresh sign-in (6.15.0): {@code POST /step-up-tickets/sso}.
 * The gateway sends the ID token exactly as the provider issued it, from a sign-in it forced to re-authenticate; web-global
 * verifies it itself and trusts nothing the gateway says about it. {@link #toString()} leaves the token out.
 *
 * @param provider the provider's id on the gateway ({@code microsoft}), which picks the verification web-global runs
 * @param idToken  the ID token, a compact JWS; at most {@value #MAX_ID_TOKEN_LENGTH} characters
 */
public record SsoStepUpRequest(@NotBlank @Size(max = 32) String provider,
		@NotBlank @Size(max = SsoStepUpRequest.MAX_ID_TOKEN_LENGTH) String idToken) {

	/** Far past any ID token a provider issues (Microsoft's run to 2-3 KB). */
	public static final int MAX_ID_TOKEN_LENGTH = 16_384;

	/** Never the token. */
	@Override
	public String toString() {
		return "SsoStepUpRequest[provider=" + provider + ", idToken=<redacted>]";
	}
}
