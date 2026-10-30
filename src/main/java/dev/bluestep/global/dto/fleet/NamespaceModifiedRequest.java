package dev.bluestep.global.dto.fleet;

/**
 * Setting a namespace's modified mark (from 6.0.0): {@code PUT /namespaces/{name}/modified} with a
 * required {@code If-Match}, the version the entry was listed with, answered with the
 * {@link FleetNamespaceResponse} and its new version as the {@code ETag}.
 *
 * <p>The monolith's ingress checker sends {@code false} once it has rebuilt the namespace's ingress. A
 * {@code 412} means a domain or tenant write marked it again meanwhile: the mark stands and the next
 * check rebuilds it. Marking is web-global's own, inside the domain and tenant writes; {@code true} is
 * accepted for operators.</p>
 *
 * @param modified the mark to set
 */
public record NamespaceModifiedRequest(boolean modified) {}
