package com.zhousl.aether.data

fun normalizeSelectableModelKey(
    modelKey: String,
    options: List<ProviderModelOption>,
): String = if (options.any { it.key == modelKey }) modelKey else ""

fun resolveStoredOrAutomaticModelKey(
    modelKey: String,
    options: List<ProviderModelOption>,
    purpose: AutomaticModelPurpose,
    fallbackPurpose: AutomaticModelPurpose? = null,
): String = normalizeSelectableModelKey(modelKey, options)
    .ifBlank {
        options.resolveAutomaticModelKey(purpose).ifBlank {
            fallbackPurpose?.let(options::resolveAutomaticModelKey).orEmpty()
        }
    }

fun resolveDefaultChatModelKey(
    settings: AppSettings,
    providerConfigs: List<LlmProviderConfig>,
): String {
    val options = providerConfigs.availableModelOptions()
    return resolveStoredOrAutomaticModelKey(
        modelKey = settings.defaultChatModelKey,
        options = options,
        purpose = AutomaticModelPurpose.Chat,
    )
}

fun resolveDefaultTitleModelKey(
    settings: AppSettings,
    providerConfigs: List<LlmProviderConfig>,
): String {
    val options = providerConfigs.availableModelOptions()
    return resolveStoredOrAutomaticModelKey(
        modelKey = settings.defaultTitleModelKey,
        options = options,
        purpose = AutomaticModelPurpose.Title,
        fallbackPurpose = AutomaticModelPurpose.Chat,
    )
}

fun resolveDefaultNamingModelKey(
    settings: AppSettings,
    providerConfigs: List<LlmProviderConfig>,
): String {
    val options = providerConfigs.availableModelOptions()
    return resolveStoredOrAutomaticModelKey(
        modelKey = settings.defaultNamingModelKey,
        options = options,
        purpose = AutomaticModelPurpose.Naming,
        fallbackPurpose = AutomaticModelPurpose.Chat,
    )
}

fun resolveModelSettings(
    baseSettings: AppSettings,
    providerConfigs: List<LlmProviderConfig>,
    preferredModelKey: String,
    fallbackModelKey: String,
): AppSettings {
    val options = providerConfigs.availableModelOptions()
    val selectedOption = options.findModelOption(preferredModelKey)
        ?: options.findModelOption(fallbackModelKey)
        ?: options.firstOrNull()
    return selectedOption?.let(baseSettings::withModelOption) ?: baseSettings
}
