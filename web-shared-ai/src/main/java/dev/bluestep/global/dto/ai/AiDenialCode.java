package dev.bluestep.global.dto.ai;

public enum AiDenialCode {
	BUDGET_EXCEEDED(1),
	TENANT_BLOCKED(2),
	USER_BLOCKED(3),
	RATE_LIMIT_EXCEEDED(4),
	SERVICE_DISABLED(5),
	TENANT_NOT_CONFIGURED(6),
	/** The requested provider/model has no ai_unit_rate pricing, so the call can't be metered — fail closed. */
	MODEL_NOT_PRICED(7);

	private final int code;

	AiDenialCode(int code) {
		this.code = code;
	}

	public int getCode() { return code; }

	public static AiDenialCode fromCode(int code) {
		for (AiDenialCode v : values()) {
			if (v.code == code) return v;
		}
		throw new IllegalArgumentException("Unknown AiDenialCode: " + code);
	}
}
