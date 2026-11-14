package com.example.feature.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.core.tools.UtilityToolsEngine
import com.example.core.ui.*
import com.example.feature.main.MainViewModel
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VioletPulse
import kotlinx.coroutines.launch

@Composable
fun PersonalToolsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val tools = listOf("QR Gen/Scan", "Weather & FX", "Calc & Units", "Dev Tools", "Translate & TZ")
    var selectedTool by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        WafaScreenHeader(
            title = "Personal Tools Suite",
            subtitle = "QR • Weather • Currency • Calc • Dev Utilities • Translator",
            onBack = onBack
        )

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tools.forEachIndexed { idx, label ->
                FilterChip(selected = selectedTool == idx, onClick = { selectedTool = idx }, label = { Text(label) })
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (selectedTool) {
                0 -> item {
                    var qrInput by remember { mutableStateOf("https://docs.xpiki.com/") }
                    var qrBmp by remember { mutableStateOf<Bitmap?>(UtilityToolsEngine.generateQrBitmap("https://docs.xpiki.com/")) }
                    var scanResult by remember { mutableStateOf<String?>(null) }

                    val qrPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
                        if (uri != null) {
                            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            val bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                            scanResult = bmp?.let { UtilityToolsEngine.decodeBarcodeOrQr(it) } ?: "No QR/Barcode detected in image."
                        }
                    }

                    WafaGlassCard(accentColor = ElectricCyan, modifier = Modifier.fillMaxWidth()) {
                        Text("QR Code Generator & Image Scanner", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = qrInput, onValueChange = { qrInput = it }, label = { Text("Text or URL") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { qrBmp = UtilityToolsEngine.generateQrBitmap(qrInput) }, modifier = Modifier.weight(1f)) {
                                Text("Generate QR")
                            }
                            OutlinedButton(onClick = {
                                qrPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }, modifier = Modifier.weight(1f)) {
                                Text("Scan Image")
                            }
                        }
                        qrBmp?.let { bmp ->
                            Spacer(Modifier.height(10.dp))
                            Image(bitmap = bmp.asImageBitmap(), contentDescription = "Generated QR Code", modifier = Modifier.size(180.dp))
                        }
                        scanResult?.let {
                            Spacer(Modifier.height(8.dp))
                            WafaHudBadge("Decoded: $it", EmeraldSignal)
                        }
                    }
                }

                1 -> item {
                    var city by remember { mutableStateOf("Dhaka") }
                    var weatherOut by remember { mutableStateOf("") }
                    var fxAmount by remember { mutableStateOf("100") }
                    var fxFrom by remember { mutableStateOf("USD") }
                    var fxTo by remember { mutableStateOf("EUR") }
                    var fxOut by remember { mutableStateOf("") }

                    WafaGlassCard(accentColor = EmeraldSignal, modifier = Modifier.fillMaxWidth()) {
                        Text("Live Weather (Open-Meteo, No Key)", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") }, singleLine = true, modifier = Modifier.weight(1f))
                            Button(onClick = {
                                scope.launch {
                                    viewModel.repository.webTools.fetchWeatherByCity(city)
                                        .onSuccess { weatherOut = "${it.cityName}: ${it.temperatureC}°C • ${it.conditionLabel} • Humidity ${it.humidityPercent}% • Wind ${it.windSpeedKmh} km/h" }
                                        .onFailure { weatherOut = it.message ?: "Error" }
                                }
                            }) { Text("Check") }
                        }
                        if (weatherOut.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(weatherOut, style = MaterialTheme.typography.bodyMedium, color = ElectricCyan)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    WafaGlassCard(accentColor = ElectricCyan, modifier = Modifier.fillMaxWidth()) {
                        Text("Live Currency Converter (Frankfurter ECB)", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(value = fxAmount, onValueChange = { fxAmount = it }, label = { Text("Amount") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = fxFrom, onValueChange = { fxFrom = it }, label = { Text("From") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = fxTo, onValueChange = { fxTo = it }, label = { Text("To") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            val amt = fxAmount.toDoubleOrNull() ?: 1.0
                            scope.launch {
                                viewModel.repository.webTools.convertCurrency(amt, fxFrom, fxTo)
                                    .onSuccess { fxOut = "${it.amount} ${it.from} = ${String.format("%.4f", it.convertedAmount)} ${it.to} (ECB ${it.date})" }
                                    .onFailure { fxOut = it.message ?: "Conversion error" }
                            }
                        }) { Text("Convert") }
                        if (fxOut.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(fxOut, style = MaterialTheme.typography.bodyMedium, color = EmeraldSignal)
                        }
                    }
                }

                2 -> item {
                    var expr by remember { mutableStateOf("(125 * 4) + 18.5") }
                    var calcResult by remember { mutableStateOf("518.5") }
                    var unitVal by remember { mutableStateOf("10") }
                    var unitCat by remember { mutableStateOf("Length") }
                    var unitFrom by remember { mutableStateOf("km") }
                    var unitTo by remember { mutableStateOf("mi") }
                    var unitOut by remember { mutableStateOf("") }

                    WafaGlassCard(accentColor = ElectricCyan, modifier = Modifier.fillMaxWidth()) {
                        Text("Calculator", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = expr, onValueChange = { expr = it }, label = { Text("Expression (+ - * / ^)") }, singleLine = true, modifier = Modifier.weight(1f))
                            Button(onClick = {
                                UtilityToolsEngine.evaluateMath(expr)
                                    .onSuccess { calcResult = it.toString() }
                                    .onFailure { calcResult = it.message ?: "Invalid" }
                            }) { Text("=") }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Result: $calcResult", style = MaterialTheme.typography.titleLarge, color = ElectricCyan)
                    }

                    Spacer(Modifier.height(12.dp))

                    WafaGlassCard(accentColor = VioletPulse, modifier = Modifier.fillMaxWidth()) {
                        Text("Unit Converter (Length: m,km,mi,ft • Mass: kg,g,lb • Temp: C,F,K • Data: KB,MB,GB)", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Length", "Mass", "Temperature", "Data").forEach { c ->
                                FilterChip(selected = unitCat == c, onClick = { unitCat = c }, label = { Text(c) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(value = unitVal, onValueChange = { unitVal = it }, label = { Text("Val") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = unitFrom, onValueChange = { unitFrom = it }, label = { Text("From") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = unitTo, onValueChange = { unitTo = it }, label = { Text("To") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            val v = unitVal.toDoubleOrNull() ?: 0.0
                            val res = UtilityToolsEngine.convertUnit(v, unitCat, unitFrom, unitTo)
                            unitOut = "$v $unitFrom = ${String.format("%.4f", res)} $unitTo"
                        }) { Text("Convert Unit") }
                        if (unitOut.isNotBlank()) Text(unitOut, color = EmeraldSignal, modifier = Modifier.padding(top = 6.dp))
                    }
                }

                3 -> item {
                    var passwordOut by remember { mutableStateOf(UtilityToolsEngine.generatePassword(18, true)) }
                    var devInput by remember { mutableStateOf("""{"app":"WAFA AI","status":"ready"}""") }
                    var regexPat by remember { mutableStateOf("[A-Z]+") }
                    var devOutput by remember { mutableStateOf("") }

                    WafaGlassCard(accentColor = EmeraldSignal, modifier = Modifier.fillMaxWidth()) {
                        Text("Cryptographic Password Generator", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(passwordOut, fontFamily = JetBrainsMonoFamily, color = ElectricCyan)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { passwordOut = UtilityToolsEngine.generatePassword(18, true) }) { Text("Regenerate") }
                            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(passwordOut)) }) { Text("Copy") }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    WafaGlassCard(accentColor = ElectricCyan, modifier = Modifier.fillMaxWidth()) {
                        Text("JSON / Regex / Base64 / URL Developer Lab", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(value = devInput, onValueChange = { devInput = it }, label = { Text("Input Payload") }, modifier = Modifier.fillMaxWidth(), maxLines = 6)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(value = regexPat, onValueChange = { regexPat = it }, label = { Text("Regex Pattern") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { devOutput = UtilityToolsEngine.formatJson(devInput).getOrElse { it.message ?: "Invalid JSON" } }) { Text("Format JSON") }
                            OutlinedButton(onClick = { devOutput = UtilityToolsEngine.testRegex(regexPat, devInput).getOrElse { listOf(it.message ?: "Err") }.joinToString(", ") }) { Text("Test Regex") }
                            OutlinedButton(onClick = { devOutput = UtilityToolsEngine.encodeBase64(devInput) }) { Text("B64 Enc") }
                            OutlinedButton(onClick = { devOutput = UtilityToolsEngine.decodeBase64(devInput).getOrElse { it.message ?: "Err" } }) { Text("B64 Dec") }
                            OutlinedButton(onClick = { devOutput = UtilityToolsEngine.encodeUrl(devInput) }) { Text("URL Enc") }
                            OutlinedButton(onClick = { devOutput = UtilityToolsEngine.decodeUrl(devInput).getOrElse { it.message ?: "Err" } }) { Text("URL Dec") }
                        }
                        if (devOutput.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(devOutput, fontFamily = JetBrainsMonoFamily, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                else -> item {
                    var trText by remember { mutableStateOf("Hello, welcome to WAFA AI.") }
                    var fromLang by remember { mutableStateOf("en") }
                    var toLang by remember { mutableStateOf("bn") }
                    var trOut by remember { mutableStateOf("") }
                    var srcTz by remember { mutableStateOf("Asia/Dhaka") }
                    var dstTz by remember { mutableStateOf("UTC") }
                    var tzOut by remember { mutableStateOf(UtilityToolsEngine.convertTimeZone("Asia/Dhaka", "UTC").getOrDefault("")) }

                    WafaGlassCard(accentColor = ElectricCyan, modifier = Modifier.fillMaxWidth()) {
                        Text("Quick EN <-> BN Translator (MyMemory API)", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(value = trText, onValueChange = { trText = it }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                scope.launch {
                                    viewModel.repository.webTools.translateOfflineOrMyMemory(trText, fromLang, toLang)
                                        .onSuccess { trOut = it }
                                        .onFailure { trOut = it.message ?: "Error" }
                                }
                            }) { Text("Translate ($fromLang -> $toLang)") }
                            OutlinedButton(onClick = {
                                val tmp = fromLang; fromLang = toLang; toLang = tmp
                            }) { Text("Swap") }
                        }
                        if (trOut.isNotBlank()) Text(trOut, modifier = Modifier.padding(top = 6.dp), color = EmeraldSignal)
                    }

                    Spacer(Modifier.height(12.dp))

                    WafaGlassCard(accentColor = VioletPulse, modifier = Modifier.fillMaxWidth()) {
                        Text("Time Zone Converter", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(value = srcTz, onValueChange = { srcTz = it }, label = { Text("Source Zone") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = dstTz, onValueChange = { dstTz = it }, label = { Text("Target Zone") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = {
                            tzOut = UtilityToolsEngine.convertTimeZone(srcTz, dstTz).getOrElse { it.message ?: "Invalid ZoneId" }
                        }) { Text("Convert Now") }
                        if (tzOut.isNotBlank()) Text(tzOut, modifier = Modifier.padding(top = 6.dp), color = ElectricCyan)
                    }
                }
            }
        }
    }
}
