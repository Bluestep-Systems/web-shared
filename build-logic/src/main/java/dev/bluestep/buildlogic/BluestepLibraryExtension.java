package dev.bluestep.buildlogic;

import org.gradle.api.provider.Property;

/**
 * The {@code bluestepLibrary { }} block of a project that applies {@code bluestep.library}: what differs between the
 * libraries this repository publishes. A configuration holder; Gradle instantiates it.
 *
 * <p>Each library has its own version line, declared as the project's {@code version} in its own build file, and is
 * released by a GitHub release whose tag is {@link #getTagPrefix() the prefix} followed by that version
 * ({@code deadline-http-1.0.0}). {@code .github/workflows/publish.yml} routes a tag to the project by that prefix and
 * runs {@code checkReleaseTag}, which refuses a tag whose number disagrees with the declared version.</p>
 */
// Package `buildlogic`, not `build`: .gitignore's `build/` matches any directory of that name, which once left this
// file untracked and a clean checkout unable to build.
public abstract class BluestepLibraryExtension {

	/**
	 * The release-tag prefix: the project's name and {@code -} unless set. The root library (dev.bluestep:web-shared)
	 * sets it empty, so its tags stay bare ({@code 6.5.0}), as they always were.
	 *
	 * @return the prefix property
	 */
	public abstract Property<String> getTagPrefix();
}
