package com.anggrayudi.storage

/**
 * Marks SimpleStorage APIs that are experimental: they may change signature, behavior, or be
 * removed entirely in any release without a deprecation cycle. Opt in with
 * `@OptIn(ExperimentalSimpleStorageApi::class)`.
 *
 * @author Anggrayudi H
 */
@RequiresOptIn(
  message =
    "This SimpleStorage API is experimental and may change or be removed in future releases.",
  level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY,
  AnnotationTarget.CONSTRUCTOR,
)
annotation class ExperimentalSimpleStorageApi
