/**
 * Wire shapes for the Console's organization directory (from 6.10.0): what the gateway's Console reads about every
 * tenant in one call, beyond the tenant record, because it is held in tables a tenant record does not carry.
 *
 * <ul>
 *   <li>{@link ConsoleDirectoryResponse} is the answer: the resellers by name and one {@link ConsoleDirectoryTenant}
 *       per tenant.</li>
 *   <li>{@link ConsoleDirectoryReseller} names a reseller by its key.</li>
 *   <li>{@link ConsoleDirectoryTenant} is a tenant's host names, newest storage levels and last traffic.</li>
 * </ul>
 *
 * <h2>Null-marked</h2>
 *
 * <p>Every type in this package is non-null except the members marked {@code @Nullable}: a level or a last
 * activity web-global holds nothing for is absent, never zero.</p>
 */
@NullMarked
package dev.bluestep.global.dto.consoledirectory;

import org.jspecify.annotations.NullMarked;
