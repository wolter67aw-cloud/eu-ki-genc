package eu.kigenc.app.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kigenc.app.BuildConfig
import eu.kigenc.app.R
import android.util.Base64
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.text.DateFormat
import java.util.Date

/** Testversion: 30 Tage gültig, nur mit einem von 12 persönlichen Zugangscodes nutzbar. */
object Beta {
    val isBeta: Boolean get() = BuildConfig.BETA
    val expiresAt: Long get() = BuildConfig.BETA_EXPIRES
    fun isExpired(now: Long = System.currentTimeMillis()) = isBeta && now > expiresAt
    fun expiryText(): String = DateFormat.getDateInstance(DateFormat.LONG).format(Date(expiresAt))
    fun daysLeft(): Long = ((expiresAt - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)

    private fun hash(code: String): String =
        MessageDigest.getInstance("SHA-256").digest("kigenc-beta:$code".toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun normalize(input: String): String {
        val raw = input.uppercase().filter { it.isLetterOrDigit() }
        val body = raw.removePrefix("KIG")
        return if (body.length == 8) "KIG-${body.take(4)}-${body.drop(4)}" else input.trim().uppercase()
    }

    fun isValid(code: String): Boolean =
        BuildConfig.BETA_CODE_HASHES.split(',').filter { it.isNotBlank() }.contains(hash(normalize(code)))

    fun storedCode(context: Context): String? =
        context.getSharedPreferences("beta", Context.MODE_PRIVATE).getString("code", null)?.takeIf { isValid(it) }

    fun store(context: Context, code: String) {
        context.getSharedPreferences("beta", Context.MODE_PRIVATE).edit().putString("code", normalize(code)).apply()
    }

    /** Gemeinsamer Mistral-Schlüssel der Testversion (nur im Arbeitsspeicher, nach Entschlüsselung mit dem Zugangscode). */
    val sharedKey = MutableStateFlow<String?>(null)

    val hasSharedKey: Boolean get() = isBeta && BuildConfig.BETA_KEY_BLOBS.isNotBlank()

    /** Entschlüsselt den Test-Schlüssel mit dem Zugangscode (PBKDF2 + AES-GCM). Ohne gültigen Code nicht lesbar. */
    suspend fun unlockSharedKey(code: String): String? = withContext(Dispatchers.Default) {
        if (!hasSharedKey) return@withContext null
        sharedKey.value?.let { return@withContext it }
        val spec = PBEKeySpec(normalize(code).toCharArray(), "kigenc-beta-key-v1".toByteArray(), 120_000, 256)
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        val aes = SecretKeySpec(keyBytes, "AES")
        val key = BuildConfig.BETA_KEY_BLOBS.split(',').firstNotNullOfOrNull { blob ->
            runCatching {
                val raw = Base64.decode(blob.trim(), Base64.DEFAULT)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, aes, GCMParameterSpec(128, raw, 0, 12))
                String(cipher.doFinal(raw, 12, raw.size - 12))
            }.getOrNull()
        }
        sharedKey.value = key
        key
    }
}

/** Zeigt bei der Testversion den Code-Bildschirm bzw. den Ablauf-Hinweis, sonst den Inhalt. */
@Composable
fun BetaGate(content: @Composable () -> Unit) {
    if (!Beta.isBeta) {
        content()
        return
    }
    val context = LocalContext.current
    var unlocked by remember { mutableStateOf(Beta.storedCode(context) != null) }
    when {
        Beta.isExpired() -> BetaMessage(
            "Testphase beendet",
            "Vielen Dank fürs Testen von KI-GenC! Die Testversion war bis ${Beta.expiryText()} gültig.\n\n" +
                "Deine Rückmeldungen helfen sehr. Die fertige Version erhältst du bald – frag einfach beim Anbieter nach."
        )
        !unlocked -> CodeEntry(onUnlocked = { unlocked = true })
        else -> {
            // Test-Schlüssel freischalten, damit Tester keinen eigenen Mistral-Schlüssel brauchen
            val shared by Beta.sharedKey.collectAsState()
            var tried by remember { mutableStateOf(!Beta.hasSharedKey) }
            LaunchedEffect(unlocked) {
                Beta.storedCode(context)?.let { Beta.unlockSharedKey(it) }
                tried = true
            }
            if (shared == null && !tried) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                content()
            }
        }
    }
}

@Composable
private fun BetaMessage(title: String, text: String) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painterResource(R.drawable.logo_kigenc), null, Modifier.size(110.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp))
        Text(text, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun CodeEntry(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Image(painterResource(R.drawable.logo_kigenc), null, Modifier.size(110.dp).padding(top = 24.dp))
        Text("KI-GenC Testversion", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Schön, dass du testest! Diese Version ist bis ${Beta.expiryText()} gültig " +
                "(noch ${Beta.daysLeft()} Tage). Bitte gib deinen persönlichen Zugangscode ein.",
            textAlign = TextAlign.Center
        )
        OutlinedTextField(
            value = code,
            onValueChange = { code = it; error = null },
            label = { Text("Zugangscode, z. B. KIG-ABCD-1234") },
            singleLine = true,
            isError = error != null,
            supportingText = { error?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                if (Beta.isValid(code)) {
                    Beta.store(context, code)
                    onUnlocked()
                } else {
                    error = "Dieser Code ist nicht gültig. Bitte genau so eingeben, wie du ihn bekommen hast."
                }
            },
            enabled = code.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Freischalten") }
        Text(
            "Jeder Code ist für eine Person gedacht. Bitte nicht weitergeben.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
