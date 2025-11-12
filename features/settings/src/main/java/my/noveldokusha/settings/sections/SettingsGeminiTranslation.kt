package my.noveldokusha.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import my.noveldoksuha.coreui.theme.ColorAccent
import my.noveldoksuha.coreui.theme.textPadding
import my.noveldokusha.settings.R

@Composable
internal fun SettingsGeminiTranslation(
    geminiApiKey: String,
    geminiModel: String,
    preferOnlineTranslation: Boolean,
    onGeminiApiKeyChange: (String) -> Unit,
    onGeminiModelChange: (String) -> Unit,
    onPreferOnlineChange: (Boolean) -> Unit,
) {
    var apiKeyText by remember(geminiApiKey) { mutableStateOf(geminiApiKey) }
    var modelText by remember(geminiModel) { mutableStateOf(geminiModel) }
    
    Column {
        Text(
            text = "Gemini Translation",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.textPadding(),
            color = ColorAccent
        )
        
        ListItem(
            headlineContent = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Outlined.Key,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gemini API Key",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKeyText,
                        onValueChange = {
                            apiKeyText = it
                            onGeminiApiKeyChange(it)
                        },
                        label = { Text("Enter your Gemini API key(s)") },
                        placeholder = { Text("AIzaSy...\\nAIzaSy...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Get your free API key at: ai.google.dev\n" +
                               "Tip: Enter multiple API keys (one per line or separated by semicolon) to avoid rate limits",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        ListItem(
            headlineContent = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Gemini Model",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = modelText,
                        onValueChange = {
                            modelText = it
                            onGeminiModelChange(it)
                        },
                        label = { Text("Model name") },
                        placeholder = { Text("gemini-2.5-flash-lite") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Default: gemini-2.5-flash-lite\n" +
                               "Examples: gemini-flash-lite-latest, gemini-2.5-flash-lite\n" +
                               "Find models at: ai.google.dev/gemini-api/docs/models",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        ListItem(
            headlineContent = {
                Text(text = "Prefer Online Translation")
            },
            supportingContent = {
                Text(
                    text = "Use Gemini (online) for translation when available. " +
                            "Falls back to offline models if Gemini fails or API key is not configured.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            trailingContent = {
                Switch(
                    checked = preferOnlineTranslation,
                    onCheckedChange = onPreferOnlineChange,
                    enabled = apiKeyText.isNotBlank()
                )
            }
        )
    }
}
