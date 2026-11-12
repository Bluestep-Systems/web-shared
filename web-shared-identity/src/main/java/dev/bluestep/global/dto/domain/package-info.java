/**
 * Wire shapes for a tenant's domains (from 6.0.0): the host names the fleet serves a tenant on, the
 * monolith's {@code DomainName} (classid 120100) with its sign-in type folded in, as one record of
 * web-global's {@code global.tenant_domain} table.
 *
 * <ul>
 *   <li>{@link DomainKey} names a domain by its {@code (classid, seqnum)}.</li>
 *   <li>{@link DomainRecordResponse} is one domain; {@link DomainCreateRequest} and
 *       {@link DomainUpdateRequest} write one, and a write something stands in the way of answers
 *       {@code 409} with a {@link DomainWriteRefusal}.</li>
 *   <li>{@link LoginTypeSetting} is a domain's own sign-in type, named by {@link LoginTypeName}.</li>
 *   <li>{@link DomainIngressRow} is one host of a namespace's ingress, with the wildcard domain it falls
 *       under.</li>
 * </ul>
 *
 * <p>web-global serves these under {@code /api/v6.0.0/domains}. A domain write marks its tenant's
 * namespace modified in the same transaction, and web-global keeps the monolith's legacy
 * {@code domainname}, {@code logintype} and {@code basetable} rows in step by trigger, so a monolith
 * build still reading or writing them sees the same domain.</p>
 *
 * <h2>Null-marked</h2>
 *
 * <p>Every type in this package is non-null unless annotated {@link org.jspecify.annotations.Nullable}.
 * A record component that may be absent is a {@code @Nullable} value, and the record declares an
 * {@code optX()} accessor that wraps it; outside the record, read the accessor. An absent key and an
 * explicit JSON {@code null} both bind as absent.</p>
 */
@NullMarked
package dev.bluestep.global.dto.domain;

import org.jspecify.annotations.NullMarked;
