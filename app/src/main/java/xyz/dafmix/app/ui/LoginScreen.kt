package xyz.dafmix.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import xyz.dafmix.app.Svc
import xyz.dafmix.app.data.ApiException
import xyz.dafmix.app.data.OfflineException

@Composable
fun LoginScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf(Svc.prefs.baseUrl) }
    var showServer by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var regOpen by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val passFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { regOpen = Svc.api.registrationOpen() }

    fun submit() {
        if (busy) return
        focus.clearFocus()
        if (user.isBlank() || pass.isEmpty()) { error = "Kullanıcı adı ve parolayı yaz"; return }
        scope.launch {
            busy = true; error = null
            try {
                Svc.prefs.baseUrl = server
                Svc.api.login(user.trim(), pass)
                Svc.prefs.loggedIn = true
                Svc.prefs.profileId = 0
                onDone()
            } catch (e: ApiException) {
                error = e.message
            } catch (e: OfflineException) {
                error = "Sunucuya ulaşılamadı. İnternet bağlantını kontrol et."
            } catch (e: IllegalArgumentException) {
                error = "Sunucu adresi geçersiz (https://… olmalı)"
            } catch (e: Exception) {
                error = e.message ?: "Giriş yapılamadı"
            }
            busy = false
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color(0x24FFFFFF), unfocusedContainerColor = Color(0x1AFFFFFF),
        focusedBorderColor = Color.White, unfocusedBorderColor = Color(0x1FFFFFFF),
        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
        focusedLabelColor = Color(0xFFD0D0D0), unfocusedLabelColor = Muted,
        cursorColor = Color.White,
    )
    val shape = RoundedCornerShape(8.dp)

    Box(
        Modifier.fillMaxSize().background(Color.Black).background(
            Brush.radialGradient(listOf(Color(0x66E50914), Color.Transparent), center = Offset(500f, 0f), radius = 1300f)
        )
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ArrowBack, "Geri", tint = Color.White)
                }
                Icon(LogoIcon, null, tint = Color.Unspecified, modifier = Modifier.padding(start = 8.dp).size(26.dp))
                Text("DAFMIX", fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Red, modifier = Modifier.padding(start = 6.dp))
            }

            Text("Oturum Aç", fontSize = 32.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 56.dp, bottom = 24.dp))

            OutlinedTextField(
                value = user, onValueChange = { user = it }, singleLine = true, shape = shape, colors = fieldColors,
                label = { Text("E-posta veya kullanıcı adı") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { passFocus.requestFocus() }),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = pass, onValueChange = { pass = it }, singleLine = true, shape = shape, colors = fieldColors,
                label = { Text("Parola") },
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Text(
                        if (showPass) "Gizle" else "Göster", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { showPass = !showPass }.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                },
                modifier = Modifier.fillMaxWidth().focusRequester(passFocus),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            if (showServer) {
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = server, onValueChange = { server = it }, singleLine = true, shape = shape, colors = fieldColors,
                    label = { Text("Sunucu adresi") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }

            Text(error ?: "", color = Color(0xFFFF6B6B), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp).height(20.dp))

            Box(
                Modifier.padding(top = 8.dp).fillMaxWidth().height(54.dp).clip(shape).background(if (busy) Color(0xFF8A0A10) else Red)
                    .clickable(enabled = !busy) { submit() },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = Color.White)
                else Text("Oturum Aç", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }

            Text(
                if (showServer) "Sunucu ayarını gizle" else "Sunucu adresi", color = Muted, fontSize = 13.sp,
                modifier = Modifier.padding(top = 14.dp).clip(RoundedCornerShape(6.dp)).clickable { showServer = !showServer }.padding(8.dp),
            )

            Spacer(Modifier.height(28.dp))
            if (regOpen) {
                Row(Modifier.fillMaxWidth().padding(bottom = 28.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                    Text("Dafmix'e yeni misiniz? ", color = Color(0xFFB3B3B3), fontSize = 15.sp)
                    Text("Hemen kaydolun", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.clickable { openInBrowser(ctx, "/kayit") })
                }
            }
        }
    }
}
